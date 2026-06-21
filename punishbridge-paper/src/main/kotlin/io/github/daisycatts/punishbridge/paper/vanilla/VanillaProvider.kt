package io.github.daisycatts.punishbridge.paper.vanilla

import com.destroystokyo.paper.profile.PlayerProfile
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
import io.github.daisycatts.punishbridge.paper.AbstractPaperProvider
import io.github.daisycatts.punishbridge.paper.PaperProviderContext
import io.github.daisycatts.punishbridge.paper.ProviderIds
import io.papermc.paper.ban.BanListType
import net.kyori.adventure.text.Component
import org.bukkit.BanEntry
import org.bukkit.ban.IpBanList
import org.bukkit.ban.ProfileBanList
import java.net.InetAddress
import java.time.Instant
import java.util.UUID

public class VanillaProvider(
    context: PaperProviderContext,
    providerId: String = ProviderIds.VANILLA,
    providerDisplayName: String = "Paper/Vanilla",
    providerTier: ProviderTier = ProviderTier.SYSTEM,
    providerVersion: String? = context.plugin.server.bukkitVersion,
) : AbstractPaperProvider(context) {
    override val descriptor: ProviderDescriptor =
        ProviderDescriptor(
            id = providerId,
            displayName = providerDisplayName,
            bridgeVersion = VanillaProvider::class.java.`package`.implementationVersion ?: "development",
            providerVersion = providerVersion,
            tier = providerTier,
        )

    override val capabilities: ProviderCapabilities = ProviderCapabilities(buildCapabilities())

    override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> =
        runProviderOperation("issue ${request.kind}") {
            val correlationId = UUID.randomUUID()
            when (request.kind) {
                PunishmentKind.BAN -> context.onServerThread { issueBan(request) }
                PunishmentKind.KICK -> context.onServerThread { kick(request) }
                else -> error("Unsupported capability reached vanilla provider")
            }
            context.emit(
                BridgeEvent.PunishmentApplied(
                    descriptor.id,
                    context.clock.instant(),
                    EventOrigin.BRIDGE,
                    correlationId,
                    EventFidelity.BRIDGE_ONLY,
                    request.toRecord(descriptor.id),
                ),
            )
            BridgeOutcome.Success(OperationReceipt(descriptor.id, correlationId, ReceiptStatus.APPLIED))
        }

    override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> =
        runProviderOperation("revoke punishment") {
            val correlationId = UUID.randomUUID()
            val count = context.onServerThread { revokeInternal(request.selector) }
            context.emit(
                BridgeEvent.PunishmentRevoked(
                    descriptor.id,
                    context.clock.instant(),
                    EventOrigin.BRIDGE,
                    correlationId,
                    EventFidelity.BRIDGE_ONLY,
                    null,
                    request.selector,
                ),
            )
            BridgeOutcome.Success(RevocationReceipt(descriptor.id, correlationId, count))
        }

    override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> =
        runProviderOperation("query active punishments") {
            BridgeOutcome.Success(context.onServerThread { findBans(query) })
        }

    private fun issueBan(request: PunishmentRequest) {
        val expiry = request.expiry(context.clock.instant())
        val source = request.actor.sourceName()
        when (val target = request.target) {
            is PunishmentTarget.Player -> addProfileBan(profile(target), request.reason, expiry, source)
            is PunishmentTarget.Address -> ipBans().addBan(target.address, request.reason, expiry, source)
            is PunishmentTarget.PlayerAndAddress -> {
                addProfileBan(profile(target), request.reason, expiry, source)
                ipBans().addBan(target.address, request.reason, expiry, source)
            }
        }
    }

    private fun kick(request: PunishmentRequest) {
        val target = request.target
        val uuid =
            when (target) {
                is PunishmentTarget.Player -> target.uuid
                is PunishmentTarget.PlayerAndAddress -> target.uuid
                is PunishmentTarget.Address -> error("Address-only targets cannot be kicked")
            }
        val player = context.plugin.server.getPlayer(uuid) ?: error("Cannot kick an offline player")
        player.kick(Component.text(request.reason))
    }

    private fun revokeInternal(selector: RevocationSelector): Int =
        when (selector) {
            is RevocationSelector.ByReference -> revokeReference(selector.reference)
            is RevocationSelector.ByTarget -> revokeTarget(selector)
        }

    private fun revokeReference(reference: PunishmentReference): Int {
        val parts = reference.externalId.split(':', limit = 2)
        if (parts.size != 2) return 0
        return when (parts[0]) {
            "profile" -> {
                val uuid = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return 0
                profileBans().pardon(context.plugin.server.createProfile(uuid))
                1
            }
            "ip" -> {
                val address = runCatching { InetAddress.getByName(parts[1]) }.getOrNull() ?: return 0
                ipBans().pardon(address)
                1
            }
            else -> 0
        }
    }

    private fun revokeTarget(selector: RevocationSelector.ByTarget): Int {
        if (selector.kind != PunishmentKind.BAN) return 0
        return when (val target = selector.target) {
            is PunishmentTarget.Player -> {
                profileBans().pardon(profile(target))
                1
            }
            is PunishmentTarget.Address -> {
                ipBans().pardon(target.address)
                1
            }
            is PunishmentTarget.PlayerAndAddress -> {
                profileBans().pardon(profile(target))
                ipBans().pardon(target.address)
                2
            }
        }
    }

    private fun findBans(query: PunishmentQuery): List<PunishmentRecord> {
        if (PunishmentKind.BAN !in query.kinds) return emptyList()
        val records = mutableListOf<PunishmentRecord>()
        when (val target = query.target) {
            is PunishmentTarget.Player ->
                getProfileBan(profile(target))?.let {
                    records += it.toRecord(descriptor.id, target, "profile:${target.uuid}")
                }
            is PunishmentTarget.Address ->
                ipBans().getBanEntry(target.address)?.let {
                    records += it.toRecord(descriptor.id, target, "ip:${target.address.hostAddress}")
                }
            is PunishmentTarget.PlayerAndAddress -> {
                getProfileBan(profile(target))?.let {
                    records += it.toRecord(descriptor.id, target, "profile:${target.uuid}")
                }
                ipBans().getBanEntry(target.address)?.let {
                    records += it.toRecord(descriptor.id, target, "ip:${target.address.hostAddress}")
                }
            }
        }
        return records
    }

    private fun profile(target: PunishmentTarget.Player): PlayerProfile = context.plugin.server.createProfile(target.uuid, target.username)

    private fun profile(target: PunishmentTarget.PlayerAndAddress): PlayerProfile =
        context.plugin.server.createProfile(target.uuid, target.username)

    private fun profileBans(): ProfileBanList = context.plugin.server.getBanList(BanListType.PROFILE)

    private fun ipBans(): IpBanList = context.plugin.server.getBanList(BanListType.IP)

    private fun addProfileBan(
        profile: PlayerProfile,
        reason: String,
        expiry: Instant?,
        source: String,
    ) {
        profileBans().addBan(profile, reason, expiry, source)
            ?: error("Paper did not create the requested profile ban")
    }

    private fun getProfileBan(profile: PlayerProfile): BanEntry<PlayerProfile>? = profileBans().getBanEntry(profile)

    override fun close(): Unit = Unit

    private companion object {
        fun buildCapabilities(): Set<Capability> {
            val banTargets = setOf(TargetKind.PLAYER, TargetKind.ADDRESS, TargetKind.PLAYER_AND_ADDRESS)
            val kickTargets = setOf(TargetKind.PLAYER, TargetKind.PLAYER_AND_ADDRESS)
            val durations = setOf(DurationMode.PERMANENT, DurationMode.TEMPORARY)
            val currentOnly = setOf(ScopeMode.CURRENT_SERVER)

            fun cap(
                operation: PunishmentOperation,
                kind: PunishmentKind,
                targets: Set<TargetKind>,
                durationModes: Set<DurationMode>,
            ): Capability = Capability(operation, kind, targets, durationModes, currentOnly, EventFidelity.BRIDGE_ONLY)

            return buildSet {
                for (operation in setOf(PunishmentOperation.ISSUE, PunishmentOperation.REVOKE, PunishmentOperation.QUERY)) {
                    add(cap(operation, PunishmentKind.BAN, banTargets, durations))
                }
                add(cap(PunishmentOperation.ISSUE, PunishmentKind.KICK, kickTargets, setOf(DurationMode.PERMANENT)))
            }
        }
    }
}

private fun PunishmentRequest.expiry(now: Instant): Instant? =
    when (val requestedDuration = duration) {
        PunishmentDuration.Permanent -> null
        is PunishmentDuration.Temporary -> now.plus(requestedDuration.duration)
    }

private fun PunishmentActor.sourceName(): String =
    when (this) {
        PunishmentActor.Console -> "Console"
        is PunishmentActor.Player -> username
        is PunishmentActor.System -> componentName
    }

private fun PunishmentRequest.toRecord(providerId: String): PunishmentRecord {
    val start = Instant.now()
    return PunishmentRecord(
        providerId,
        null,
        kind,
        target,
        actor,
        reason,
        start,
        expiry(start),
        scope,
        DataFidelity.COMPLETE,
    )
}

private fun BanEntry<*>.toRecord(
    providerId: String,
    target: PunishmentTarget,
    externalId: String,
): PunishmentRecord =
    PunishmentRecord(
        providerId,
        PunishmentReference(providerId, externalId),
        PunishmentKind.BAN,
        target,
        PunishmentActor.System(source),
        reason,
        created.toInstant(),
        expiration?.toInstant(),
        PunishmentScope.CurrentServer,
        DataFidelity.PARTIAL,
    )
