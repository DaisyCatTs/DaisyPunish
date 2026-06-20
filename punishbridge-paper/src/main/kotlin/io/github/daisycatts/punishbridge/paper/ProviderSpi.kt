package io.github.daisycatts.punishbridge.paper

import io.github.daisycatts.punishbridge.BridgeEvent
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
import kotlinx.coroutines.CoroutineDispatcher
import org.bukkit.plugin.Plugin
import java.time.Clock
import java.time.Duration

public interface PaperPunishmentProvider : AutoCloseable {
    public val descriptor: ProviderDescriptor
    public val capabilities: ProviderCapabilities

    public suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt>

    public suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt>

    public suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>>

    override fun close()
}

public interface PaperPunishmentProviderFactory {
    public val id: String
    public val tier: ProviderTier

    public fun isAvailable(plugin: Plugin): Boolean

    public fun create(context: PaperProviderContext): PaperPunishmentProvider
}

public class PaperProviderContext(
    public val plugin: Plugin,
    public val currentServerName: String,
    public val operationTimeout: Duration,
    public val clock: Clock,
    public val blockingDispatcher: CoroutineDispatcher,
    private val eventSink: (BridgeEvent) -> Unit,
) {
    public fun emit(event: BridgeEvent): Unit = eventSink(event)

    public suspend fun <T> onServerThread(action: () -> T): T = plugin.server.onServerThread(plugin, action)
}
