package io.github.daisycatts.punishbridge.provider.libertybans

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
import kotlinx.coroutines.future.await
import space.arim.libertybans.api.AddressVictim
import space.arim.libertybans.api.CompositeVictim
import space.arim.libertybans.api.ConsoleOperator
import space.arim.libertybans.api.LibertyBans
import space.arim.libertybans.api.NetworkAddress
import space.arim.libertybans.api.Operator
import space.arim.libertybans.api.PlayerOperator
import space.arim.libertybans.api.PlayerVictim
import space.arim.libertybans.api.PunishmentType
import space.arim.libertybans.api.Victim
import space.arim.libertybans.api.event.PostPardonEvent
import space.arim.libertybans.api.event.PostPunishEvent
import space.arim.libertybans.api.punish.Punishment
import space.arim.libertybans.api.scope.ServerScope
import space.arim.omnibus.OmnibusProvider
import space.arim.omnibus.events.EventConsumer
import space.arim.omnibus.events.ListenerPriorities
import space.arim.omnibus.events.RegisteredListener
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

public class LibertyBansProvider(
    private val context: PaperProviderContext,
) : PaperPunishmentProvider {
    private val libertyBans: LibertyBans =
        OmnibusProvider
            .getOmnibus()
            .registry
            .getProvider(LibertyBans::class.java)
            .orElseThrow { IllegalStateException("LibertyBans API provider is unavailable") }
    private val pending: ConcurrentHashMap<String, UUID> = ConcurrentHashMap()
    private val punishListener: RegisteredListener
    private val pardonListener: RegisteredListener

    override val descriptor: ProviderDescriptor =
        ProviderDescriptor(
            "libertybans",
            "LibertyBans",
            LibertyBansProvider::class.java.`package`.implementationVersion ?: "development",
            context.plugin.server.pluginManager
                .getPlugin("LibertyBans")
                ?.pluginMeta
                ?.version,
            ProviderTier.DEDICATED,
        )

    override val capabilities: ProviderCapabilities = ProviderCapabilities(buildCapabilities())

    init {
        val eventBus = libertyBans.omnibus.eventBus
        punishListener =
            eventBus.registerListener(
                PostPunishEvent::class.java,
                ListenerPriorities.NORMAL,
                EventConsumer { event -> emitApplied(event.punishment) },
            )
        pardonListener =
            eventBus.registerListener(
                PostPardonEvent::class.java,
                ListenerPriorities.NORMAL,
                EventConsumer { event -> emitRevoked(event.punishment) },
            )
    }

    override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> =
        runProviderOperation("issue ${request.kind}") {
            val correlationId = UUID.randomUUID()
            val victim = request.target.toVictim()
            pending[request.kind.fingerprint(victim)] = correlationId
            val duration =
                when (val value = request.duration) {
                    PunishmentDuration.Permanent -> Duration.ZERO
                    is PunishmentDuration.Temporary -> value.duration
                }
            val punishment =
                libertyBans.drafter
                    .draftBuilder()
                    .type(request.kind.toLibertyType())
                    .victim(victim)
                    .operator(request.actor.toOperator())
                    .reason(request.reason)
                    .duration(duration)
                    .scope(request.scope.toServerScope())
                    .build()
                    .enactPunishment()
                    .toCompletableFuture()
                    .await()
            if (punishment.isEmpty) {
                pending.remove(request.kind.fingerprint(victim), correlationId)
                return@runProviderOperation BridgeOutcome.Rejected("LibertyBans did not apply the punishment")
            }
            val reference = PunishmentReference(descriptor.id, punishment.get().identifier.toString())
            BridgeOutcome.Success(OperationReceipt(descriptor.id, correlationId, ReceiptStatus.APPLIED, reference))
        }

    override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> =
        runProviderOperation("revoke punishment") {
            val correlationId = UUID.randomUUID()
            val revoked =
                when (val selector = request.selector) {
                    is RevocationSelector.ByReference -> {
                        val id =
                            selector.reference.externalId.toLongOrNull()
                                ?: return@runProviderOperation BridgeOutcome.Rejected("Invalid LibertyBans punishment ID")
                        libertyBans.revoker
                            .revokeById(id)
                            .undoAndGetPunishment()
                            .toCompletableFuture()
                            .await()
                    }
                    is RevocationSelector.ByTarget -> {
                        val victim = selector.target.toVictim()
                        pending[selector.kind.fingerprint(victim)] = correlationId
                        libertyBans.revoker
                            .revokeByTypeAndVictim(selector.kind.toLibertyType(), victim)
                            .undoAndGetPunishment()
                            .toCompletableFuture()
                            .await()
                    }
                }
            BridgeOutcome.Success(RevocationReceipt(descriptor.id, correlationId, if (revoked.isPresent) 1 else 0))
        }

    override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> =
        runProviderOperation("query active punishments") {
            val (uuid, address) = query.target.applicabilityIdentity()
            val records =
                query.kinds
                    .flatMap { kind ->
                        libertyBans.selector
                            .selectionByApplicabilityBuilder(uuid, address)
                            .type(kind.toLibertyType())
                            .scope(query.scope.toServerScope())
                            .selectActiveOnly()
                            .build()
                            .getAllSpecificPunishments()
                            .toCompletableFuture()
                            .await()
                            .map { it.toRecord() }
                    }.distinctBy { it.reference }
            BridgeOutcome.Success(records)
        }

    private fun emitApplied(punishment: Punishment) {
        val correlationId = pending.remove(punishment.type.toKind().fingerprint(punishment.victim))
        context.emit(
            BridgeEvent.PunishmentApplied(
                descriptor.id,
                context.clock.instant(),
                if (correlationId == null) EventOrigin.EXTERNAL_PROVIDER else EventOrigin.BRIDGE,
                correlationId,
                EventFidelity.AUTHORITATIVE_LOCAL,
                punishment.toRecord(),
            ),
        )
    }

    private fun emitRevoked(punishment: Punishment) {
        val correlationId = pending.remove(punishment.type.toKind().fingerprint(punishment.victim))
        context.emit(
            BridgeEvent.PunishmentRevoked(
                descriptor.id,
                context.clock.instant(),
                if (correlationId == null) EventOrigin.EXTERNAL_PROVIDER else EventOrigin.BRIDGE,
                correlationId,
                EventFidelity.AUTHORITATIVE_LOCAL,
                punishment.toRecord(),
                null,
            ),
        )
    }

    override fun close() {
        val eventBus = libertyBans.omnibus.eventBus
        eventBus.unregisterListener(punishListener)
        eventBus.unregisterListener(pardonListener)
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

    private fun PunishmentScope.toServerScope(): ServerScope =
        when (this) {
            PunishmentScope.CurrentServer -> libertyBans.scopeManager.currentServerScopeOrFallback()
            PunishmentScope.Global -> libertyBans.scopeManager.globalScope()
            is PunishmentScope.NamedServer -> libertyBans.scopeManager.specificScope(name)
        }

    private companion object {
        fun buildCapabilities(): Set<Capability> =
            buildSet {
                val allTargets = setOf(TargetKind.PLAYER, TargetKind.ADDRESS, TargetKind.PLAYER_AND_ADDRESS)
                val playerTargets = setOf(TargetKind.PLAYER, TargetKind.PLAYER_AND_ADDRESS)
                val durations = setOf(DurationMode.PERMANENT, DurationMode.TEMPORARY)
                val scopes = setOf(ScopeMode.CURRENT_SERVER, ScopeMode.GLOBAL, ScopeMode.NAMED_SERVER)
                for (kind in PunishmentKind.entries) {
                    val targets = if (kind == PunishmentKind.KICK) playerTargets else allTargets
                    val issueDurations = if (kind == PunishmentKind.KICK) setOf(DurationMode.PERMANENT) else durations
                    add(Capability(PunishmentOperation.ISSUE, kind, targets, issueDurations, scopes, EventFidelity.AUTHORITATIVE_LOCAL))
                    add(
                        Capability(
                            PunishmentOperation.OBSERVE_EXTERNAL,
                            kind,
                            targets,
                            issueDurations,
                            scopes,
                            EventFidelity.AUTHORITATIVE_LOCAL,
                        ),
                    )
                    if (kind != PunishmentKind.KICK) {
                        add(Capability(PunishmentOperation.REVOKE, kind, targets, durations, scopes, EventFidelity.AUTHORITATIVE_LOCAL))
                        add(Capability(PunishmentOperation.QUERY, kind, targets, durations, scopes, EventFidelity.AUTHORITATIVE_LOCAL))
                    }
                }
            }
    }
}

private fun PunishmentKind.toLibertyType(): PunishmentType =
    when (this) {
        PunishmentKind.BAN -> PunishmentType.BAN
        PunishmentKind.MUTE -> PunishmentType.MUTE
        PunishmentKind.WARNING -> PunishmentType.WARN
        PunishmentKind.KICK -> PunishmentType.KICK
    }

private fun PunishmentType.toKind(): PunishmentKind =
    when (this) {
        PunishmentType.BAN -> PunishmentKind.BAN
        PunishmentType.MUTE -> PunishmentKind.MUTE
        PunishmentType.WARN -> PunishmentKind.WARNING
        PunishmentType.KICK -> PunishmentKind.KICK
    }

private fun PunishmentTarget.toVictim(): Victim =
    when (this) {
        is PunishmentTarget.Player -> PlayerVictim.of(uuid)
        is PunishmentTarget.Address -> AddressVictim.of(address)
        is PunishmentTarget.PlayerAndAddress -> CompositeVictim.of(uuid, address)
    }

private fun PunishmentTarget.applicabilityIdentity(): Pair<UUID, NetworkAddress> =
    when (this) {
        is PunishmentTarget.Player -> uuid to CompositeVictim.WILDCARD_ADDRESS
        is PunishmentTarget.Address -> CompositeVictim.WILDCARD_UUID to NetworkAddress.of(address)
        is PunishmentTarget.PlayerAndAddress -> uuid to NetworkAddress.of(address)
    }

private fun PunishmentActor.toOperator(): Operator =
    when (this) {
        PunishmentActor.Console -> ConsoleOperator.INSTANCE
        is PunishmentActor.Player -> PlayerOperator.of(uuid)
        is PunishmentActor.System -> ConsoleOperator.INSTANCE
    }

private fun PunishmentKind.fingerprint(victim: Victim): String = "$name:$victim"

private fun Punishment.toRecord(): PunishmentRecord =
    PunishmentRecord(
        "libertybans",
        PunishmentReference("libertybans", identifier.toString()),
        type.toKind(),
        victim.toTarget(),
        operator.toActor(),
        reason,
        startDate,
        if (isPermanent) null else endDate,
        null,
        DataFidelity.PARTIAL,
    )

private fun Victim.toTarget(): PunishmentTarget =
    when (this) {
        is PlayerVictim -> PunishmentTarget.Player(uuid, "unknown")
        is AddressVictim -> PunishmentTarget.Address(address.toInetAddress())
        is CompositeVictim -> PunishmentTarget.PlayerAndAddress(uuid, "unknown", address.toInetAddress())
        else -> error("Unsupported LibertyBans victim: $this")
    }

private fun Operator.toActor(): PunishmentActor =
    when (this) {
        is PlayerOperator -> PunishmentActor.Player(uuid, "unknown")
        is ConsoleOperator -> PunishmentActor.Console
        else -> PunishmentActor.System("LibertyBans")
    }
