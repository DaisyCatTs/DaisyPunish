package io.github.daisycatts.punishbridge.provider.essentialsx

import com.earth2me.essentials.Essentials
import com.earth2me.essentials.User
import io.github.daisycatts.punishbridge.BridgeEvent
import io.github.daisycatts.punishbridge.BridgeOutcome
import io.github.daisycatts.punishbridge.Capability
import io.github.daisycatts.punishbridge.DataFidelity
import io.github.daisycatts.punishbridge.DurationMode
import io.github.daisycatts.punishbridge.EventFidelity
import io.github.daisycatts.punishbridge.EventOrigin
import io.github.daisycatts.punishbridge.OperationReceipt
import io.github.daisycatts.punishbridge.ProviderCapabilities
import io.github.daisycatts.punishbridge.ProviderDescriptor
import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.PunishmentActor
import io.github.daisycatts.punishbridge.PunishmentDuration
import io.github.daisycatts.punishbridge.PunishmentKind
import io.github.daisycatts.punishbridge.PunishmentOperation
import io.github.daisycatts.punishbridge.PunishmentQuery
import io.github.daisycatts.punishbridge.PunishmentRecord
import io.github.daisycatts.punishbridge.PunishmentReference
import io.github.daisycatts.punishbridge.PunishmentRequest
import io.github.daisycatts.punishbridge.PunishmentScope
import io.github.daisycatts.punishbridge.PunishmentTarget
import io.github.daisycatts.punishbridge.ReceiptStatus
import io.github.daisycatts.punishbridge.RevocationReceipt
import io.github.daisycatts.punishbridge.RevocationRequest
import io.github.daisycatts.punishbridge.RevocationSelector
import io.github.daisycatts.punishbridge.ScopeMode
import io.github.daisycatts.punishbridge.TargetKind
import io.github.daisycatts.punishbridge.paper.PaperProviderContext
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProvider
import io.github.daisycatts.punishbridge.paper.vanilla.VanillaProvider
import net.ess3.api.IUser
import net.ess3.api.events.MuteStatusChangeEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public class EssentialsXProvider(
    private val context: PaperProviderContext,
) : PaperPunishmentProvider,
    Listener {
    private val essentials: Essentials =
        context.plugin.server.pluginManager
            .getPlugin("Essentials") as? Essentials
            ?: error("EssentialsX plugin is not available")
    private val pending: ConcurrentHashMap<String, UUID> = ConcurrentHashMap()
    private val vanilla: VanillaProvider =
        VanillaProvider(
            context,
            providerId = "essentialsx",
            providerDisplayName = "EssentialsX",
            providerTier = ProviderTier.FALLBACK,
            providerVersion = essentials.pluginMeta.version,
        )

    override val descriptor: ProviderDescriptor =
        ProviderDescriptor(
            "essentialsx",
            "EssentialsX",
            EssentialsXProvider::class.java.`package`.implementationVersion ?: "development",
            essentials.pluginMeta.version,
            ProviderTier.FALLBACK,
        )

    override val capabilities: ProviderCapabilities =
        ProviderCapabilities(
            vanilla.capabilities.entries + muteCapabilities(),
        )

    init {
        context.plugin.server.pluginManager
            .registerEvents(this, context.plugin)
    }

    override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> {
        if (request.kind != PunishmentKind.MUTE) return vanilla.issue(request)
        return runProviderOperation("mute player") {
            val target =
                request.target as? PunishmentTarget.Player
                    ?: return@runProviderOperation BridgeOutcome.Rejected("EssentialsX mutes require a player target")
            val user = essentials.getUser(target.uuid)
            val correlationId = UUID.randomUUID()
            pending[target.uuid.toString()] = correlationId
            val outcome =
                context.onServerThread {
                    val timeout =
                        when (val duration = request.duration) {
                            PunishmentDuration.Permanent -> 0L
                            is PunishmentDuration.Temporary ->
                                context.clock
                                    .instant()
                                    .plus(duration.duration)
                                    .toEpochMilli()
                        }
                    val event = MuteStatusChangeEvent(user, request.actor.controller(), true, timeout, request.reason)
                    context.plugin.server.pluginManager
                        .callEvent(event)
                    if (event.isCancelled) return@onServerThread false
                    user.muteReason = request.reason
                    user.isMuted = true
                    user.muteTimeout = timeout
                    true
                }
            if (!outcome) {
                pending.remove(target.uuid.toString(), correlationId)
                BridgeOutcome.Rejected("EssentialsX mute event was cancelled")
            } else {
                BridgeOutcome.Success(OperationReceipt(descriptor.id, correlationId, ReceiptStatus.APPLIED))
            }
        }
    }

    override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> {
        val muteTarget = request.muteTarget()
        if (muteTarget == null) return vanilla.revoke(request)
        return runProviderOperation("unmute player") {
            val user = essentials.getUser(muteTarget.uuid)
            val correlationId = UUID.randomUUID()
            pending[muteTarget.uuid.toString()] = correlationId
            val outcome =
                context.onServerThread {
                    val event =
                        MuteStatusChangeEvent(
                            user,
                            request.actor.controller(),
                            false,
                            user.muteTimeout,
                            request.reason ?: user.muteReason,
                        )
                    context.plugin.server.pluginManager
                        .callEvent(event)
                    if (event.isCancelled) return@onServerThread false
                    user.isMuted = false
                    user.muteTimeout = 0L
                    user.muteReason = null
                    true
                }
            if (!outcome) {
                pending.remove(muteTarget.uuid.toString(), correlationId)
                BridgeOutcome.Rejected("EssentialsX unmute event was cancelled")
            } else {
                BridgeOutcome.Success(RevocationReceipt(descriptor.id, correlationId, 1))
            }
        }
    }

    override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> {
        val wantsMute = PunishmentKind.MUTE in query.kinds
        val otherKinds = query.kinds - PunishmentKind.MUTE
        val records = mutableListOf<PunishmentRecord>()
        if (otherKinds.isNotEmpty()) {
            when (val outcome = vanilla.findActive(query.copy(kinds = otherKinds))) {
                is BridgeOutcome.Success -> records += outcome.value
                else -> return outcome
            }
        }
        if (wantsMute) {
            val target =
                query.target as? PunishmentTarget.Player
                    ?: return BridgeOutcome.Rejected("EssentialsX mute queries require a player target")
            val user = essentials.getUser(target.uuid)
            val timeout = user.muteTimeout
            if (user.isMuted && (timeout <= 0L || timeout > context.clock.millis())) {
                records += user.toMuteRecord(target)
            }
        }
        return BridgeOutcome.Success(records)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public fun onMuteChange(event: MuteStatusChangeEvent) {
        val affected = event.affected
        val target = PunishmentTarget.Player(affected.getUUID(), affected.name)
        val correlationId = pending.remove(affected.getUUID().toString())
        val origin = if (correlationId == null) EventOrigin.EXTERNAL_PROVIDER else EventOrigin.BRIDGE
        val record =
            PunishmentRecord(
                descriptor.id,
                PunishmentReference(descriptor.id, "mute:${affected.getUUID()}"),
                PunishmentKind.MUTE,
                target,
                event.controller?.toActor(),
                event.reason,
                null,
                event.timestamp.orElse(null)?.let(Instant::ofEpochMilli),
                PunishmentScope.CurrentServer,
                DataFidelity.PARTIAL,
            )
        if (event.value) {
            context.emit(
                BridgeEvent.PunishmentApplied(
                    descriptor.id,
                    context.clock.instant(),
                    origin,
                    correlationId,
                    EventFidelity.AUTHORITATIVE_LOCAL,
                    record,
                ),
            )
        } else {
            context.emit(
                BridgeEvent.PunishmentRevoked(
                    descriptor.id,
                    context.clock.instant(),
                    origin,
                    correlationId,
                    EventFidelity.AUTHORITATIVE_LOCAL,
                    record,
                    null,
                ),
            )
        }
    }

    private fun PunishmentActor.controller(): User? =
        when (this) {
            PunishmentActor.Console -> null
            is PunishmentActor.Player -> essentials.getUser(uuid)
            is PunishmentActor.System -> null
        }

    private fun User.toMuteRecord(target: PunishmentTarget.Player): PunishmentRecord =
        PunishmentRecord(
            descriptor.id,
            PunishmentReference(descriptor.id, "mute:${target.uuid}"),
            PunishmentKind.MUTE,
            target,
            null,
            muteReason,
            null,
            muteTimeout.takeIf { it > 0L }?.let(Instant::ofEpochMilli),
            PunishmentScope.CurrentServer,
            DataFidelity.PARTIAL,
        )

    private fun RevocationRequest.muteTarget(): PunishmentTarget.Player? =
        when (val selector = selector) {
            is RevocationSelector.ByReference -> {
                if (!selector.reference.externalId.startsWith("mute:")) return null
                selector.reference.externalId.substringAfter("mute:").let(UUID::fromString).let {
                    PunishmentTarget.Player(it, essentials.getUser(it).name)
                }
            }
            is RevocationSelector.ByTarget -> {
                if (selector.kind == PunishmentKind.MUTE) selector.target as? PunishmentTarget.Player else null
            }
        }

    override fun close() {
        HandlerList.unregisterAll(this)
        pending.clear()
        vanilla.close()
    }

    private suspend inline fun <T> runProviderOperation(
        label: String,
        crossinline action: suspend () -> BridgeOutcome<T>,
    ): BridgeOutcome<T> =
        try {
            action()
        } catch (error: Throwable) {
            BridgeOutcome.Failed(descriptor.id, "Failed to $label", error)
        }

    private companion object {
        fun muteCapabilities(): Set<Capability> {
            val durations = setOf(DurationMode.PERMANENT, DurationMode.TEMPORARY)
            return PunishmentOperation.entries
                .map { operation ->
                    Capability(
                        operation,
                        PunishmentKind.MUTE,
                        setOf(TargetKind.PLAYER),
                        durations,
                        setOf(ScopeMode.CURRENT_SERVER),
                        EventFidelity.AUTHORITATIVE_LOCAL,
                    )
                }.toSet()
        }
    }
}

private fun IUser.toActor(): PunishmentActor = PunishmentActor.Player(getUUID(), name)
