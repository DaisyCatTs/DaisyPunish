package io.github.daisycatts.punishbridge.provider.litebans

import io.github.daisycatts.punishbridge.BridgeEvent
import io.github.daisycatts.punishbridge.BridgeOutcome
import io.github.daisycatts.punishbridge.Capability
import io.github.daisycatts.punishbridge.DataFidelity
import io.github.daisycatts.punishbridge.DurationMode
import io.github.daisycatts.punishbridge.EventFidelity
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
import io.github.daisycatts.punishbridge.paper.AbstractPaperProvider
import io.github.daisycatts.punishbridge.paper.PaperProviderContext
import io.github.daisycatts.punishbridge.paper.ProviderIds
import kotlinx.coroutines.withContext
import litebans.api.Database
import litebans.api.Entry
import litebans.api.Events
import java.net.InetAddress
import java.time.Instant
import java.util.UUID

public class LiteBansProvider(
    context: PaperProviderContext,
) : AbstractPaperProvider(context) {
    private val listener: Events.Listener =
        object : Events.Listener() {
            override fun entryAdded(entry: Entry) {
                onEntry(entry, revoked = false)
            }

            override fun entryRemoved(entry: Entry) {
                onEntry(entry, revoked = true)
            }
        }

    override val descriptor: ProviderDescriptor =
        ProviderDescriptor(
            ProviderIds.LITEBANS,
            "LiteBans",
            LiteBansProvider::class.java.`package`.implementationVersion ?: "development",
            context.plugin.server.pluginManager
                .getPlugin("LiteBans")
                ?.pluginMeta
                ?.version,
            ProviderTier.DEDICATED,
        )

    override val capabilities: ProviderCapabilities = ProviderCapabilities(buildCapabilities())

    init {
        Events.get().register(listener)
    }

    override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> =
        runProviderOperation("issue ${request.kind}") {
            val fingerprint = request.fingerprint()
            val correlationId = correlations.begin(fingerprint)
            val accepted =
                context.onServerThread {
                    context.plugin.server.dispatchCommand(context.plugin.server.consoleSender, request.toLiteBansCommand())
                }
            if (!accepted) {
                correlations.discard(fingerprint, correlationId)
                return@runProviderOperation BridgeOutcome.Rejected("LiteBans rejected the command")
            }
            context.emit(
                BridgeEvent.OperationAccepted(
                    descriptor.id,
                    context.clock.instant(),
                    correlationId = correlationId,
                    fidelity = EventFidelity.PARTIAL_LOCAL,
                    request = request,
                ),
            )
            BridgeOutcome.Accepted(OperationReceipt(descriptor.id, correlationId, ReceiptStatus.ACCEPTED))
        }

    override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> =
        runProviderOperation("revoke punishment") {
            val command =
                request.toCommand() ?: return@runProviderOperation BridgeOutcome.Rejected(
                    "LiteBans cannot revoke this selector through its public command interface",
                )
            val accepted =
                context.onServerThread {
                    context.plugin.server.dispatchCommand(context.plugin.server.consoleSender, command)
                }
            if (!accepted) return@runProviderOperation BridgeOutcome.Rejected("LiteBans rejected the command")
            BridgeOutcome.Success(RevocationReceipt(descriptor.id, UUID.randomUUID(), null))
        }

    override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> =
        runProviderOperation("query active punishments") {
            val records =
                withContext(context.blockingDispatcher) {
                    query.kinds.mapNotNull { kind -> queryEntry(query, kind) }.map { it.toRecord() }
                }
            BridgeOutcome.Success(records)
        }

    private fun queryEntry(
        query: PunishmentQuery,
        kind: PunishmentKind,
    ): Entry? {
        val (uuid, ip) = query.target.identifiers()
        val scope = query.scope.databaseScope(context.currentServerName)
        return when (kind) {
            PunishmentKind.BAN -> Database.get().getBan(uuid, ip, scope)
            PunishmentKind.MUTE -> Database.get().getMute(uuid, ip, scope)
            PunishmentKind.WARNING -> Database.get().getWarning(uuid, ip, scope)
            PunishmentKind.KICK -> null
        }
    }

    private fun onEntry(
        entry: Entry,
        revoked: Boolean,
    ) {
        val record =
            runCatching { entry.toRecord() }.getOrElse { error ->
                context.plugin.logger.warning("Could not normalize LiteBans event: ${error.message}")
                return
            }
        val correlationKey = entry.fingerprint()
        if (revoked) {
            emitRevoked(record, EventFidelity.PARTIAL_LOCAL, correlationKey)
        } else {
            emitApplied(record, EventFidelity.PARTIAL_LOCAL, correlationKey)
        }
    }

    override fun close() {
        Events.get().unregister(listener)
        correlations.clear()
    }

    private companion object {
        fun buildCapabilities(): Set<Capability> {
            val ipCapableTargets = setOf(TargetKind.PLAYER, TargetKind.ADDRESS, TargetKind.PLAYER_AND_ADDRESS)
            val playerTargets = setOf(TargetKind.PLAYER, TargetKind.PLAYER_AND_ADDRESS)
            val durations = setOf(DurationMode.PERMANENT, DurationMode.TEMPORARY)
            val currentOnly = setOf(ScopeMode.CURRENT_SERVER)
            val queryScopes = setOf(ScopeMode.CURRENT_SERVER, ScopeMode.GLOBAL, ScopeMode.NAMED_SERVER)

            fun cap(
                operation: PunishmentOperation,
                kind: PunishmentKind,
                targets: Set<TargetKind>,
                durationModes: Set<DurationMode>,
                scopes: Set<ScopeMode>,
            ): Capability = Capability(operation, kind, targets, durationModes, scopes, EventFidelity.PARTIAL_LOCAL)

            return buildSet {
                for (kind in PunishmentKind.entries) {
                    val targets =
                        when (kind) {
                            PunishmentKind.BAN, PunishmentKind.MUTE -> ipCapableTargets
                            PunishmentKind.WARNING, PunishmentKind.KICK -> playerTargets
                        }
                    val issueDurations = if (kind == PunishmentKind.KICK) setOf(DurationMode.PERMANENT) else durations
                    add(cap(PunishmentOperation.ISSUE, kind, targets, issueDurations, currentOnly))
                    add(cap(PunishmentOperation.OBSERVE_EXTERNAL, kind, targets, issueDurations, queryScopes))
                    if (kind != PunishmentKind.KICK) {
                        add(cap(PunishmentOperation.REVOKE, kind, targets, durations, currentOnly))
                        add(cap(PunishmentOperation.QUERY, kind, targets, durations, queryScopes))
                    }
                }
            }
        }
    }
}

internal fun PunishmentRequest.toLiteBansCommand(): String {
    val targetValue = target.commandTarget()
    val durationValue =
        when (val value = duration) {
            PunishmentDuration.Permanent -> "perm"
            is PunishmentDuration.Temporary -> "${value.duration.seconds}s"
        }
    return when (kind) {
        PunishmentKind.BAN -> "${if (target is PunishmentTarget.Player) "ban" else "ipban"} $targetValue $durationValue $reason"
        PunishmentKind.MUTE -> "${if (target is PunishmentTarget.Player) "mute" else "ipmute"} $targetValue $durationValue $reason"
        PunishmentKind.WARNING -> "warn $targetValue $durationValue $reason"
        PunishmentKind.KICK -> "kick $targetValue $reason"
    }
}

private fun RevocationRequest.toCommand(): String? =
    when (val value = selector) {
        is RevocationSelector.ByReference -> {
            val parts = value.reference.externalId.split(':', limit = 2)
            if (parts.size != 2) return null
            when (parts[0]) {
                "ban" -> "unban --id ${parts[1]}"
                "mute" -> "unmute --id ${parts[1]}"
                "warn" -> "unwarn ${parts[1]}"
                else -> null
            }
        }
        is RevocationSelector.ByTarget ->
            when (value.kind) {
                PunishmentKind.BAN -> "unban ${value.target.commandTarget()}"
                PunishmentKind.MUTE -> "unmute ${value.target.commandTarget()}"
                PunishmentKind.WARNING -> "clearwarnings ${value.target.commandTarget()}"
                PunishmentKind.KICK -> null
            }
    }

private fun PunishmentTarget.commandTarget(): String =
    when (this) {
        is PunishmentTarget.Player -> username
        is PunishmentTarget.Address -> address.hostAddress
        is PunishmentTarget.PlayerAndAddress -> username
    }

private fun PunishmentTarget.identifiers(): Pair<UUID?, String?> =
    when (this) {
        is PunishmentTarget.Player -> uuid to null
        is PunishmentTarget.Address -> null to address.hostAddress
        is PunishmentTarget.PlayerAndAddress -> uuid to address.hostAddress
    }

private fun PunishmentScope.databaseScope(currentServerName: String): String? =
    when (this) {
        PunishmentScope.CurrentServer -> currentServerName
        PunishmentScope.Global -> null
        is PunishmentScope.NamedServer -> name
    }

private fun PunishmentRequest.fingerprint(): String {
    val (uuid, ip) = target.identifiers()
    return "${kind.name.lowercase()}:${uuid ?: ""}:${ip ?: ""}"
}

private fun Entry.fingerprint(): String = "$type:${uuid ?: ""}:${ip ?: ""}"

private fun Entry.toRecord(): PunishmentRecord {
    val parsedUuid = uuid?.let(UUID::fromString)
    val parsedAddress = ip?.takeUnless { '%' in it || '*' in it }?.let(InetAddress::getByName)
    val target =
        when {
            parsedUuid != null && parsedAddress != null -> PunishmentTarget.PlayerAndAddress(parsedUuid, "unknown", parsedAddress)
            parsedUuid != null -> PunishmentTarget.Player(parsedUuid, "unknown")
            parsedAddress != null -> PunishmentTarget.Address(parsedAddress)
            else -> error("LiteBans entry has no representable target")
        }
    val kind =
        when (type.lowercase()) {
            "ban" -> PunishmentKind.BAN
            "mute" -> PunishmentKind.MUTE
            "warn" -> PunishmentKind.WARNING
            "kick" -> PunishmentKind.KICK
            else -> error("Unsupported LiteBans entry type: $type")
        }
    val actor =
        when {
            executorUUID.equals("CONSOLE", ignoreCase = true) -> PunishmentActor.Console
            executorUUID != null ->
                runCatching {
                    PunishmentActor.Player(UUID.fromString(executorUUID), executorName ?: "unknown")
                }.getOrElse { PunishmentActor.System(executorName ?: "LiteBans") }
            else -> PunishmentActor.System(executorName ?: "LiteBans")
        }
    val scope =
        if (serverScope.equals("global", ignoreCase = true)) {
            PunishmentScope.Global
        } else {
            PunishmentScope.NamedServer(serverScope)
        }
    return PunishmentRecord(
        ProviderIds.LITEBANS,
        PunishmentReference(ProviderIds.LITEBANS, "$type:$id"),
        kind,
        target,
        actor,
        reason,
        Instant.ofEpochMilli(dateStart),
        dateEnd.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        scope,
        DataFidelity.PARTIAL,
    )
}
