package io.github.daisycatts.punishbridge.paper

import io.github.daisycatts.punishbridge.BridgeOutcome
import io.github.daisycatts.punishbridge.OperationReceipt
import io.github.daisycatts.punishbridge.ProviderCapabilities
import io.github.daisycatts.punishbridge.ProviderDescriptor
import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.PunishmentQuery
import io.github.daisycatts.punishbridge.PunishmentRecord
import io.github.daisycatts.punishbridge.PunishmentRequest
import io.github.daisycatts.punishbridge.RevocationReceipt
import io.github.daisycatts.punishbridge.RevocationRequest
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.logging.Logger

class ProviderSelectionTest {
    private val plugin: Plugin =
        mock(Plugin::class.java).also {
            `when`(it.name).thenReturn("PunishBridgeTest")
            `when`(it.logger).thenReturn(Logger.getLogger("PunishBridgeTest"))
        }

    @Test
    fun `dedicated provider wins over fallback`() {
        val result =
            PaperPunishmentBridge
                .builder(plugin)
                .providerFactories(
                    listOf(
                        FakeFactory("essentialsx", ProviderTier.FALLBACK),
                        FakeFactory("litebans", ProviderTier.DEDICATED),
                    ),
                ).build()

        val ready = assertInstanceOf(BridgeStartResult.Ready::class.java, result)
        assertEquals("litebans", ready.bridge.provider.id)
        ready.bridge.close()
    }

    @Test
    fun `multiple dedicated providers fail clearly`() {
        val result =
            PaperPunishmentBridge
                .builder(plugin)
                .providerFactories(
                    listOf(
                        FakeFactory("libertybans", ProviderTier.DEDICATED),
                        FakeFactory("litebans", ProviderTier.DEDICATED),
                        FakeFactory("essentialsx", ProviderTier.FALLBACK),
                    ),
                ).build()

        val conflict = assertInstanceOf(BridgeStartResult.Conflict::class.java, result)
        assertEquals(listOf("libertybans", "litebans"), conflict.providerIds)
    }

    @Test
    fun `explicit provider resolves a dedicated conflict`() {
        val result =
            PaperPunishmentBridge
                .builder(plugin)
                .provider("libertybans")
                .providerFactories(
                    listOf(
                        FakeFactory("libertybans", ProviderTier.DEDICATED),
                        FakeFactory("litebans", ProviderTier.DEDICATED),
                    ),
                ).build()

        val ready = assertInstanceOf(BridgeStartResult.Ready::class.java, result)
        assertEquals("libertybans", ready.bridge.provider.id)
        ready.bridge.close()
    }

    private class FakeFactory(
        override val id: String,
        override val tier: ProviderTier,
    ) : PaperPunishmentProviderFactory {
        override fun isAvailable(plugin: Plugin): Boolean = true

        override fun create(context: PaperProviderContext): PaperPunishmentProvider = FakeProvider(id, tier)
    }

    private class FakeProvider(
        id: String,
        tier: ProviderTier,
    ) : PaperPunishmentProvider {
        override val descriptor: ProviderDescriptor = ProviderDescriptor(id, id, "test", "test", tier)
        override val capabilities: ProviderCapabilities = ProviderCapabilities.NONE

        override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> = BridgeOutcome.Unavailable("unused")

        override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> = BridgeOutcome.Unavailable("unused")

        override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> = BridgeOutcome.Success(emptyList())

        override fun close(): Unit = Unit
    }
}
