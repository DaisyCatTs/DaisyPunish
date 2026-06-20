package io.github.daisycatts.punishbridge.provider.advancedban

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
import kotlinx.coroutines.withContext
import me.leoko.advancedban.bukkit.event.PunishmentEvent
import me.leoko.advancedban.bukkit.event.RevokePunishmentEvent
import me.leoko.advancedban.manager.PunishmentManager
import me.leoko.advancedban.utils.Punishment
import me.leoko.advancedban.utils.PunishmentType
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public class AdvancedBanProvider(
    private val context: PaperProviderContext,
) : PaperPunishmentProvider,
    Listener {
    private val pending: ConcurrentHashMap<String, UUID> = ConcurrentHashMap()

    override val descriptor: ProviderDescriptor =
        ProviderDescriptor(
            "advancedban",
            "AdvancedBan",
            AdvancedBanProvider::class.java.`package`.implementationVersion ?: "development",
            context.plugin.server.pluginManager
                .getPlugin("AdvancedBan")
                ?.pluginMeta
                ?.version,
            ProviderTier.DEDICATED,
        )

    override val capabilities: ProviderCapabilities = ProviderCapabilities(buildCapabilities())

    init {
        context.plugin.server.pluginManager
            .registerEvents(this, context.plugin)
    }

    override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> =
        runProviderOperation("issue ${request.kind}") {
            val correlationId = UUID.randomUUID()
            pending[request.fingerprint()] = correlationId
            withContext(context.blockingDispatcher) {
                val type = request.advancedType()
                val (name, target) = request.target.advancedIdentity(type)
                Punishment.create(
                    name,
                    target,
                    request.reason,
                    request.actor.operatorName(),
                    type,
                    request.endEpochMillis(),
                    null,
                    false,
                )
            }
            BridgeOutcome.Success(OperationReceipt(descriptor.id, correlationId, ReceiptStatus.APPLIED))
        }

    override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> =
        runProviderOperation("revoke punishment") {
            val correlationId = UUID.randomUUID()
            val count =
                withContext(context.blockingDispatcher) {
                    when (val selector = request.selector) {
                        is RevocationSelector.ByReference -> {
                            val id =
                                selector.reference.externalId
                                    .substringAfterLast(':')
                                    .toIntOrNull()
                                    ?: return@withContext 0
                            val punishment = PunishmentManager.get().getPunishment(id) ?: return@withContext 0
                            punishment.delete(request.actor.operatorName(), false, true)
                            1
                        }
                        is RevocationSelector.ByTarget -> {
                            val targets = selector.target.advancedQueryTargets()
                            val basicType = selector.kind.advancedBasicType()
                            val punishments =
                                targets
                                    .flatMap { PunishmentManager.get().getPunishments(it, basicType, true) }
                                    .distinctBy { it.id }
                            punishments.forEach { it.delete(request.actor.operatorName(), punishments.size > 1, true) }
                            punishments.size
                        }
                    }
                }
            BridgeOutcome.Success(RevocationReceipt(descriptor.id, correlationId, count))
        }

    override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> =
        runProviderOperation("query active punishments") {
            val result =
                withContext(context.blockingDispatcher) {
                    query.kinds
                        .flatMap { kind ->
                            query.target.advancedQueryTargets().flatMap { target ->
                                PunishmentManager.get().getPunishments(target, kind.advancedBasicType(), true)
                            }
                        }.distinctBy { it.id }
                        .map { it.toRecord() }
                }
            BridgeOutcome.Success(result)
        }

    @EventHandler
    public fun onPunishment(event: PunishmentEvent) {
        emit(event.punishment, revoked = false)
    }

    @EventHandler
    public fun onRevoke(event: RevokePunishmentEvent) {
        emit(event.punishment, revoked = true)
    }

    private fun emit(
        punishment: Punishment,
        revoked: Boolean,
    ) {
        val record =
            runCatching { punishment.toRecord() }.getOrElse { error ->
                context.plugin.logger.warning("Could not normalize AdvancedBan event: ${error.message}")
                return
            }
        val correlationId = pending.remove(punishment.fingerprint())
        val origin = if (correlationId == null) EventOrigin.EXTERNAL_PROVIDER else EventOrigin.BRIDGE
        if (revoked) {
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
        } else {
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
        }
    }

    override fun close() {
        HandlerList.unregisterAll(this)
        pending.clear()
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
        fun buildCapabilities(): Set<Capability> =
            buildSet {
                val player = setOf(TargetKind.PLAYER)
                val banTargets = setOf(TargetKind.PLAYER, TargetKind.ADDRESS, TargetKind.PLAYER_AND_ADDRESS)
                val durations = setOf(DurationMode.PERMANENT, DurationMode.TEMPORARY)
                for (kind in PunishmentKind.entries) {
                    val targets = if (kind == PunishmentKind.BAN) banTargets else player
                    val issueDurations = if (kind == PunishmentKind.KICK) setOf(DurationMode.PERMANENT) else durations
                    add(
                        Capability(
                            PunishmentOperation.ISSUE,
                            kind,
                            targets,
                            issueDurations,
                            setOf(ScopeMode.CURRENT_SERVER),
                            EventFidelity.AUTHORITATIVE_LOCAL,
                        ),
                    )
                    add(
                        Capability(
                            PunishmentOperation.OBSERVE_EXTERNAL,
                            kind,
                            targets,
                            issueDurations,
                            setOf(ScopeMode.CURRENT_SERVER),
                            EventFidelity.AUTHORITATIVE_LOCAL,
                        ),
                    )
                    if (kind != PunishmentKind.KICK) {
                        add(
                            Capability(
                                PunishmentOperation.REVOKE,
                                kind,
                                targets,
                                durations,
                                setOf(ScopeMode.CURRENT_SERVER),
                                EventFidelity.AUTHORITATIVE_LOCAL,
                            ),
                        )
                        add(
                            Capability(
                                PunishmentOperation.QUERY,
                                kind,
                                targets,
                                durations,
                                setOf(ScopeMode.CURRENT_SERVER),
                                EventFidelity.AUTHORITATIVE_LOCAL,
                            ),
                        )
                    }
                }
            }
    }
}

private fun PunishmentRequest.advancedType(): PunishmentType {
    val temporary = duration is PunishmentDuration.Temporary
    val ipBased = target !is PunishmentTarget.Player
    return when (kind) {
        PunishmentKind.BAN ->
            when {
                ipBased && temporary -> PunishmentType.TEMP_IP_BAN
                ipBased -> PunishmentType.IP_BAN
                temporary -> PunishmentType.TEMP_BAN
                else -> PunishmentType.BAN
            }
        PunishmentKind.MUTE -> if (temporary) PunishmentType.TEMP_MUTE else PunishmentType.MUTE
        PunishmentKind.WARNING -> if (temporary) PunishmentType.TEMP_WARNING else PunishmentType.WARNING
        PunishmentKind.KICK -> PunishmentType.KICK
    }
}

private fun PunishmentKind.advancedBasicType(): PunishmentType =
    when (this) {
        PunishmentKind.BAN -> PunishmentType.BAN
        PunishmentKind.MUTE -> PunishmentType.MUTE
        PunishmentKind.WARNING -> PunishmentType.WARNING
        PunishmentKind.KICK -> PunishmentType.KICK
    }

private fun PunishmentTarget.advancedIdentity(type: PunishmentType): Pair<String, String> =
    when (this) {
        is PunishmentTarget.Player -> username to uuid.toUndashedString()
        is PunishmentTarget.Address -> address.hostAddress to address.hostAddress
        is PunishmentTarget.PlayerAndAddress -> username to if (type.isIpOrientated) address.hostAddress else uuid.toUndashedString()
    }

private fun PunishmentTarget.advancedQueryTargets(): List<String> =
    when (this) {
        is PunishmentTarget.Player -> listOf(uuid.toUndashedString())
        is PunishmentTarget.Address -> listOf(address.hostAddress)
        is PunishmentTarget.PlayerAndAddress -> listOf(uuid.toUndashedString(), address.hostAddress)
    }

private fun UUID.toUndashedString(): String = toString().replace("-", "")

private fun PunishmentActor.operatorName(): String =
    when (this) {
        PunishmentActor.Console -> "CONSOLE"
        is PunishmentActor.Player -> username
        is PunishmentActor.System -> componentName
    }

private fun PunishmentRequest.endEpochMillis(): Long =
    when (val value = duration) {
        PunishmentDuration.Permanent -> -1L
        is PunishmentDuration.Temporary -> Instant.now().plus(value.duration).toEpochMilli()
    }

private fun PunishmentRequest.fingerprint(): String {
    val type = advancedType()
    val (_, identity) = target.advancedIdentity(type)
    return "${type.basic.name}:$identity"
}

private fun Punishment.fingerprint(): String = "${type.basic.name}:$uuid"

private fun Punishment.toRecord(): PunishmentRecord {
    val kind =
        when (type.basic) {
            PunishmentType.BAN -> PunishmentKind.BAN
            PunishmentType.MUTE -> PunishmentKind.MUTE
            PunishmentType.WARNING -> PunishmentKind.WARNING
            PunishmentType.KICK -> PunishmentKind.KICK
            else -> error("Unsupported AdvancedBan punishment type: $type")
        }
    val target =
        if (type.isIpOrientated) {
            PunishmentTarget.Address(InetAddress.getByName(uuid))
        } else {
            PunishmentTarget.Player(parseUndashedUuid(uuid), name)
        }
    val actor =
        if (operator.equals("CONSOLE", ignoreCase = true)) {
            PunishmentActor.Console
        } else {
            PunishmentActor.System(operator)
        }
    return PunishmentRecord(
        "advancedban",
        PunishmentReference("advancedban", "${kind.name.lowercase()}:$id"),
        kind,
        target,
        actor,
        reason,
        Instant.ofEpochMilli(start),
        end.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        PunishmentScope.CurrentServer,
        DataFidelity.PARTIAL,
    )
}

internal fun parseUndashedUuid(value: String): UUID {
    require(value.length == 32) { "Invalid undashed UUID" }
    val dashed =
        buildString(36) {
            append(value, 0, 8)
            append('-')
            append(value, 8, 12)
            append('-')
            append(value, 12, 16)
            append('-')
            append(value, 16, 20)
            append('-')
            append(value, 20, 32)
        }
    return UUID.fromString(dashed)
}
