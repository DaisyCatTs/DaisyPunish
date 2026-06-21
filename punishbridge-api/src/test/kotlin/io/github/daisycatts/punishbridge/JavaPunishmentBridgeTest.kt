package io.github.daisycatts.punishbridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.TimeUnit

class JavaPunishmentBridgeTest {
    @Test
    fun `issue delegates to the wrapped bridge and completes`() {
        val bridge = JavaPunishmentBridge(FakeBridge())

        val outcome =
            bridge
                .issue(
                    PunishmentRequest(
                        PunishmentKind.BAN,
                        PunishmentTarget.Player(UUID.randomUUID(), "Daisy"),
                        reason = "Automated test",
                    ),
                ).toCompletableFuture()
                .get(5, TimeUnit.SECONDS)

        assertInstanceOf(BridgeOutcome.Accepted::class.java, outcome)
        bridge.close()
    }

    @Test
    fun `isMuted maps a non-empty query to true`() {
        val bridge = JavaPunishmentBridge(FakeBridge())

        val outcome =
            bridge
                .isMuted(PunishmentTarget.Address(InetAddress.getByName("192.0.2.1")))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)

        assertEquals(BridgeOutcome.Success(true), outcome)
        bridge.close()
    }

    private class FakeBridge : PunishmentBridge {
        override val provider: ProviderDescriptor = ProviderDescriptor("fake", "Fake", "test", "test", ProviderTier.SYSTEM)
        override val capabilities: ProviderCapabilities = ProviderCapabilities.NONE
        override val events: Flow<BridgeEvent> = MutableSharedFlow()

        override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> =
            BridgeOutcome.Accepted(OperationReceipt("fake", UUID.randomUUID(), ReceiptStatus.ACCEPTED))

        override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> =
            BridgeOutcome.Success(RevocationReceipt("fake", UUID.randomUUID(), 1))

        override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> =
            BridgeOutcome.Success(
                listOf(
                    PunishmentRecord(
                        "fake",
                        null,
                        query.kinds.first(),
                        query.target,
                        null,
                        null,
                        null,
                        null,
                        null,
                        DataFidelity.PARTIAL,
                    ),
                ),
            )

        override fun close(): Unit = Unit
    }
}
