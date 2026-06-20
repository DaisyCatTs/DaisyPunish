package io.github.daisycatts.punishbridge.paper

import io.github.daisycatts.punishbridge.BridgeOutcome
import io.github.daisycatts.punishbridge.Capability
import io.github.daisycatts.punishbridge.DurationMode
import io.github.daisycatts.punishbridge.EventFidelity
import io.github.daisycatts.punishbridge.OperationReceipt
import io.github.daisycatts.punishbridge.ProviderCapabilities
import io.github.daisycatts.punishbridge.ProviderDescriptor
import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.PunishmentKind
import io.github.daisycatts.punishbridge.PunishmentOperation
import io.github.daisycatts.punishbridge.PunishmentQuery
import io.github.daisycatts.punishbridge.PunishmentRecord
import io.github.daisycatts.punishbridge.PunishmentRequest
import io.github.daisycatts.punishbridge.PunishmentTarget
import io.github.daisycatts.punishbridge.ReceiptStatus
import io.github.daisycatts.punishbridge.RevocationReceipt
import io.github.daisycatts.punishbridge.RevocationRequest
import io.github.daisycatts.punishbridge.ScopeMode
import io.github.daisycatts.punishbridge.TargetKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.math.max

class BridgeBehaviorTest {
    private val plugin: Plugin =
        mock(Plugin::class.java).also {
            `when`(it.name).thenReturn("PunishBridgeTest")
            `when`(it.logger).thenReturn(Logger.getLogger("PunishBridgeTest"))
        }

    @Test
    fun `issue after close reports unavailable`() {
        runBlocking {
            val bridge = bridge(RecordingProvider())
            bridge.close()

            val outcome = bridge.issue(banRequest())

            assertInstanceOf(BridgeOutcome.Unavailable::class.java, outcome)
        }
    }

    @Test
    fun `invalid request is rejected before reaching the provider`() =
        runBlocking {
            val provider = RecordingProvider()
            val bridge = bridge(provider)

            val outcome = bridge.issue(banRequest().copy(reason = "bad\ncommand"))

            assertInstanceOf(BridgeOutcome.Rejected::class.java, outcome)
            assertEquals(0, provider.started.get())
            bridge.close()
        }

    @Test
    fun `unsupported capability is reported without invoking the provider`() =
        runBlocking {
            val provider = RecordingProvider(capabilities = ProviderCapabilities.NONE)
            val bridge = bridge(provider)

            val outcome = bridge.issue(banRequest())

            assertInstanceOf(BridgeOutcome.Unsupported::class.java, outcome)
            assertEquals(0, provider.started.get())
            bridge.close()
        }

    @Test
    fun `a slow provider is failed by the operation timeout`() =
        runBlocking {
            val bridge = bridge(RecordingProvider(delayMillis = 2_000), timeout = Duration.ofMillis(100))

            val failed = assertInstanceOf(BridgeOutcome.Failed::class.java, bridge.issue(banRequest()))

            assertTrue(failed.message.contains("Timed out"))
            bridge.close()
        }

    @Test
    fun `operations on the same target never overlap`() =
        runBlocking {
            val provider = RecordingProvider(delayMillis = 50)
            val bridge = bridge(provider)
            val uuid = UUID.randomUUID()

            (1..4).map { launch { bridge.issue(banRequest(uuid)) } }.joinAll()

            assertEquals(1, provider.maxConcurrent.get())
            bridge.close()
        }

    @Test
    fun `operations on different targets run concurrently`() =
        runBlocking {
            val provider = RecordingProvider(delayMillis = 50)
            val bridge = bridge(provider)

            (1..4).map { launch { bridge.issue(banRequest()) } }.joinAll()

            assertTrue(provider.maxConcurrent.get() >= 2)
            bridge.close()
        }

    private fun bridge(
        provider: PaperPunishmentProvider,
        timeout: Duration = Duration.ofSeconds(5),
    ): PaperPunishmentBridge {
        val result =
            PaperPunishmentBridge
                .builder(plugin)
                .operationTimeout(timeout)
                .providerFactories(listOf(SingleProviderFactory(provider)))
                .build()
        return assertInstanceOf(BridgeStartResult.Ready::class.java, result).bridge as PaperPunishmentBridge
    }

    private fun banRequest(uuid: UUID = UUID.randomUUID()): PunishmentRequest =
        PunishmentRequest(PunishmentKind.BAN, PunishmentTarget.Player(uuid, "Daisy"), reason = "Automated test")

    private class SingleProviderFactory(
        private val provider: PaperPunishmentProvider,
    ) : PaperPunishmentProviderFactory {
        override val id: String = provider.descriptor.id
        override val tier: ProviderTier = provider.descriptor.tier

        override fun isAvailable(plugin: Plugin): Boolean = true

        override fun create(context: PaperProviderContext): PaperPunishmentProvider = provider
    }

    private class RecordingProvider(
        private val delayMillis: Long = 0,
        override val capabilities: ProviderCapabilities = banCapabilities(),
    ) : PaperPunishmentProvider {
        override val descriptor: ProviderDescriptor = ProviderDescriptor("fake", "Fake", "test", "test", ProviderTier.DEDICATED)
        val started: AtomicInteger = AtomicInteger(0)
        val maxConcurrent: AtomicInteger = AtomicInteger(0)
        private val inFlight: AtomicInteger = AtomicInteger(0)

        override suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt> {
            started.incrementAndGet()
            val current = inFlight.incrementAndGet()
            maxConcurrent.updateAndGet { max(it, current) }
            try {
                if (delayMillis > 0) delay(delayMillis)
            } finally {
                inFlight.decrementAndGet()
            }
            return BridgeOutcome.Success(OperationReceipt(descriptor.id, UUID.randomUUID(), ReceiptStatus.APPLIED))
        }

        override suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt> =
            BridgeOutcome.Success(RevocationReceipt(descriptor.id, UUID.randomUUID(), 0))

        override suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>> = BridgeOutcome.Success(emptyList())

        override fun close(): Unit = Unit

        private companion object {
            fun banCapabilities(): ProviderCapabilities =
                ProviderCapabilities(
                    setOf(
                        Capability(
                            PunishmentOperation.ISSUE,
                            PunishmentKind.BAN,
                            setOf(TargetKind.PLAYER),
                            setOf(DurationMode.PERMANENT),
                            setOf(ScopeMode.CURRENT_SERVER),
                            EventFidelity.BRIDGE_ONLY,
                        ),
                    ),
                )
        }
    }
}
