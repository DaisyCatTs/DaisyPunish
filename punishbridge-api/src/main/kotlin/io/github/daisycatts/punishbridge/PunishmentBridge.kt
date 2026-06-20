package io.github.daisycatts.punishbridge

import kotlinx.coroutines.flow.Flow

public interface PunishmentBridge : AutoCloseable {
    public val provider: ProviderDescriptor
    public val capabilities: ProviderCapabilities
    public val events: Flow<BridgeEvent>

    public suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt>

    public suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt>

    public suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>>

    public suspend fun isMuted(target: PunishmentTarget): BridgeOutcome<Boolean> =
        findActive(PunishmentQuery(target, setOf(PunishmentKind.MUTE))).map { it.isNotEmpty() }

    public suspend fun isBanned(target: PunishmentTarget): BridgeOutcome<Boolean> =
        findActive(PunishmentQuery(target, setOf(PunishmentKind.BAN))).map { it.isNotEmpty() }

    override fun close()
}
