package io.github.daisycatts.punishbridge.paper

import io.github.daisycatts.punishbridge.BridgeEvent
import io.github.daisycatts.punishbridge.BridgeOutcome
import io.github.daisycatts.punishbridge.CapabilityRequest
import io.github.daisycatts.punishbridge.OperationReceipt
import io.github.daisycatts.punishbridge.ProviderCapabilities
import io.github.daisycatts.punishbridge.ProviderDescriptor
import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.PunishmentBridge
import io.github.daisycatts.punishbridge.PunishmentOperation
import io.github.daisycatts.punishbridge.PunishmentQuery
import io.github.daisycatts.punishbridge.PunishmentRecord
import io.github.daisycatts.punishbridge.PunishmentRequest
import io.github.daisycatts.punishbridge.RequestValidator
import io.github.daisycatts.punishbridge.RevocationReceipt
import io.github.daisycatts.punishbridge.RevocationRequest
import io.github.daisycatts.punishbridge.RevocationSelector
import io.github.daisycatts.punishbridge.kind
import io.github.daisycatts.punishbridge.mode
import io.github.daisycatts.punishbridge.paper.vanilla.VanillaProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.bukkit.plugin.Plugin
import java.time.Clock
import java.time.Duration
import java.util.ServiceLoader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

public sealed interface BridgeStartResult {
    public data class Ready(
        public val bridge: PunishmentBridge,
    ) : BridgeStartResult

    public data class Conflict(
        public val providerIds: List<String>,
    ) : BridgeStartResult

    public data class Unavailable(
        public val reason: String,
    ) : BridgeStartResult

    public data class Failed(
        public val providerId: String?,
        public val message: String,
        public val cause: Throwable,
    ) : BridgeStartResult
}

public class PaperPunishmentBridge private constructor(
    private val delegate: PaperPunishmentProvider,
    private val eventFlow: MutableSharedFlow<BridgeEvent>,
    private val dispatcher: ExecutorCoroutineDispatcher,
    private val timeout: Duration,
) : PunishmentBridge {
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val targetLocks: HashMap<String, TargetLock> = HashMap()

    override val provider: ProviderDescriptor
        get() = delegate.descriptor

    override val capabilities: ProviderCapabilities
        get() = delegate.capabilities

    override val events: Flow<BridgeEvent> = eventFlow

    override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> {
        if (closed.get()) return BridgeOutcome.Unavailable("PunishBridge is closed")
        RequestValidator.validate(request)?.let { return BridgeOutcome.Rejected(it) }
        val requested =
            CapabilityRequest(
                PunishmentOperation.ISSUE,
                request.kind,
                request.target.kind(),
                request.duration.mode(),
                request.scope.mode(),
            )
        if (!capabilities.supports(requested)) return BridgeOutcome.Unsupported(requested)
        return serialized(request.target.toLockKey()) { timed("issue ${request.kind}") { delegate.issue(request) } }
    }

    override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> {
        if (closed.get()) return BridgeOutcome.Unavailable("PunishBridge is closed")
        RequestValidator.validate(request)?.let { return BridgeOutcome.Rejected(it) }
        val selector = request.selector
        if (selector is RevocationSelector.ByReference && selector.reference.providerId != provider.id) {
            return BridgeOutcome.Rejected("Punishment reference belongs to provider ${selector.reference.providerId}")
        }
        if (selector is RevocationSelector.ByTarget) {
            val requested =
                CapabilityRequest(
                    PunishmentOperation.REVOKE,
                    selector.kind,
                    selector.target.kind(),
                    null,
                    selector.scope.mode(),
                )
            if (!capabilities.supports(requested)) return BridgeOutcome.Unsupported(requested)
        }
        val lockKey =
            when (selector) {
                is RevocationSelector.ByReference -> "reference:${selector.reference.externalId}"
                is RevocationSelector.ByTarget -> selector.target.toLockKey()
            }
        return serialized(lockKey) { timed("revoke punishment") { delegate.revoke(request) } }
    }

    override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> {
        if (closed.get()) return BridgeOutcome.Unavailable("PunishBridge is closed")
        RequestValidator.validate(query)?.let { return BridgeOutcome.Rejected(it) }
        query.kinds.forEach { kind ->
            val requested =
                CapabilityRequest(
                    PunishmentOperation.QUERY,
                    kind,
                    query.target.kind(),
                    null,
                    query.scope.mode(),
                )
            if (!capabilities.supports(requested)) return BridgeOutcome.Unsupported(requested)
        }
        return timed("query active punishments") { delegate.findActive(query) }
    }

    private suspend fun <T> timed(
        label: String,
        action: suspend () -> BridgeOutcome<T>,
    ): BridgeOutcome<T> =
        try {
            withTimeout(timeout.toMillis()) { action() }
        } catch (error: TimeoutCancellationException) {
            BridgeOutcome.Failed(provider.id, "Timed out while attempting to $label", error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            BridgeOutcome.Failed(provider.id, "Failed to $label", error)
        }

    private suspend fun <T> serialized(
        key: String,
        action: suspend () -> BridgeOutcome<T>,
    ): BridgeOutcome<T> {
        val lock =
            synchronized(targetLocks) {
                targetLocks.getOrPut(key) { TargetLock() }.also { it.waiters++ }
            }
        return try {
            lock.mutex.withLock { action() }
        } finally {
            synchronized(targetLocks) {
                if (--lock.waiters == 0) targetLocks.remove(key)
            }
        }
    }

    private class TargetLock {
        val mutex: Mutex = Mutex()
        var waiters: Int = 0
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { delegate.close() }
        dispatcher.close()
    }

    public class Builder internal constructor(
        private val plugin: Plugin,
    ) {
        private var providerId: String? = null
        private var currentServerName: String = "default"
        private var operationTimeout: Duration = Duration.ofSeconds(5)
        private var clock: Clock = Clock.systemUTC()
        private var factories: List<PaperPunishmentProviderFactory>? = null

        public fun provider(id: String?): Builder = apply { providerId = id }

        public fun currentServer(name: String): Builder = apply { currentServerName = name }

        public fun operationTimeout(timeout: Duration): Builder = apply { operationTimeout = timeout }

        public fun clock(clock: Clock): Builder = apply { this.clock = clock }

        public fun providerFactories(factories: List<PaperPunishmentProviderFactory>): Builder =
            apply { this.factories = factories.toList() }

        public fun build(): BridgeStartResult {
            if (operationTimeout.isZero || operationTimeout.isNegative) {
                return BridgeStartResult.Unavailable("Operation timeout must be positive")
            }
            val discovered = factories ?: discoverFactories(plugin)
            val available =
                discovered.filter { factory ->
                    runCatching { factory.isAvailable(plugin) }.getOrDefault(false)
                }
            val selected = selectFactory(available, providerId) ?: return selectionFailure(available, providerId)
            val executor =
                Executors.newFixedThreadPool(4) { runnable ->
                    Thread(runnable, "punishbridge-${plugin.name}").apply { isDaemon = true }
                }
            val dispatcher = executor.asCoroutineDispatcher()
            val eventFlow = MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 128)
            val context =
                PaperProviderContext(
                    plugin,
                    currentServerName,
                    operationTimeout,
                    clock,
                    dispatcher,
                ) { event ->
                    if (!eventFlow.tryEmit(event)) {
                        plugin.logger.warning("PunishBridge event buffer is full; event from ${event.providerId} was dropped")
                    }
                }
            return try {
                val provider = selected.create(context)
                plugin.logger.info(
                    "PunishBridge selected ${provider.descriptor.displayName} " +
                        "(${provider.descriptor.providerVersion ?: "unknown version"}) with " +
                        "${provider.capabilities.entries.size} capabilities",
                )
                BridgeStartResult.Ready(PaperPunishmentBridge(provider, eventFlow, dispatcher, operationTimeout))
            } catch (error: Throwable) {
                dispatcher.close()
                BridgeStartResult.Failed(selected.id, "Failed to initialize punishment provider", error)
            }
        }
    }

    public companion object {
        @JvmStatic
        public fun builder(plugin: Plugin): Builder = Builder(plugin)

        private fun discoverFactories(plugin: Plugin): List<PaperPunishmentProviderFactory> =
            buildList {
                ServiceLoader.load(PaperPunishmentProviderFactory::class.java, plugin.javaClass.classLoader).forEach(::add)
                add(VanillaProviderFactory())
            }.distinctBy { it.id }

        private fun selectFactory(
            available: List<PaperPunishmentProviderFactory>,
            explicitId: String?,
        ): PaperPunishmentProviderFactory? {
            if (explicitId != null) return available.firstOrNull { it.id.equals(explicitId, ignoreCase = true) }
            val dedicated = available.filter { it.tier == ProviderTier.DEDICATED }
            if (dedicated.size == 1) return dedicated.single()
            if (dedicated.size > 1) return null
            return available.firstOrNull { it.tier == ProviderTier.FALLBACK }
                ?: available.firstOrNull { it.tier == ProviderTier.SYSTEM }
        }

        private fun selectionFailure(
            available: List<PaperPunishmentProviderFactory>,
            explicitId: String?,
        ): BridgeStartResult {
            if (explicitId != null) return BridgeStartResult.Unavailable("Requested provider '$explicitId' is not available")
            val conflicts = available.filter { it.tier == ProviderTier.DEDICATED }.map { it.id }.sorted()
            return if (conflicts.size > 1) {
                BridgeStartResult.Conflict(conflicts)
            } else {
                BridgeStartResult.Unavailable("No punishment provider is available")
            }
        }
    }
}

private fun io.github.daisycatts.punishbridge.PunishmentTarget.toLockKey(): String =
    when (this) {
        is io.github.daisycatts.punishbridge.PunishmentTarget.Player -> "player:$uuid"
        is io.github.daisycatts.punishbridge.PunishmentTarget.Address -> "address:${address.hostAddress}"
        is io.github.daisycatts.punishbridge.PunishmentTarget.PlayerAndAddress -> "composite:$uuid:${address.hostAddress}"
    }
