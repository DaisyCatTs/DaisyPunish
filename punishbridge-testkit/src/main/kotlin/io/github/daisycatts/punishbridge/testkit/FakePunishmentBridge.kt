package io.github.daisycatts.punishbridge.testkit

import io.github.daisycatts.punishbridge.BridgeEvent
import io.github.daisycatts.punishbridge.BridgeOutcome
import io.github.daisycatts.punishbridge.OperationReceipt
import io.github.daisycatts.punishbridge.ProviderCapabilities
import io.github.daisycatts.punishbridge.ProviderDescriptor
import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.PunishmentBridge
import io.github.daisycatts.punishbridge.PunishmentQuery
import io.github.daisycatts.punishbridge.PunishmentRecord
import io.github.daisycatts.punishbridge.PunishmentRequest
import io.github.daisycatts.punishbridge.RevocationReceipt
import io.github.daisycatts.punishbridge.RevocationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

public class FakePunishmentBridge(
    override val provider: ProviderDescriptor =
        ProviderDescriptor(
            "fake",
            "Fake provider",
            "test",
            "test",
            ProviderTier.SYSTEM,
        ),
    override val capabilities: ProviderCapabilities = ProviderCapabilities.NONE,
) : PunishmentBridge {
    private val eventFlow: MutableSharedFlow<BridgeEvent> = MutableSharedFlow(extraBufferCapacity = 32)

    override val events: Flow<BridgeEvent> = eventFlow

    public var issueHandler: suspend (PunishmentRequest) -> BridgeOutcome<OperationReceipt> = {
        BridgeOutcome.Unavailable("No issue handler configured")
    }
    public var revokeHandler: suspend (RevocationRequest) -> BridgeOutcome<RevocationReceipt> = {
        BridgeOutcome.Unavailable("No revoke handler configured")
    }
    public var queryHandler: suspend (PunishmentQuery) -> BridgeOutcome<List<PunishmentRecord>> = {
        BridgeOutcome.Unavailable("No query handler configured")
    }

    override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> = issueHandler(request)

    override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> = revokeHandler(request)

    override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> = queryHandler(query)

    public fun emit(event: BridgeEvent): Boolean = eventFlow.tryEmit(event)

    override fun close(): Unit = Unit
}
