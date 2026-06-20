package io.github.daisycatts.sample

import io.github.daisycatts.punishbridge.PunishmentBridge
import io.github.daisycatts.punishbridge.paper.BridgeStartResult
import io.github.daisycatts.punishbridge.paper.PaperPunishmentBridge
import org.bukkit.plugin.java.JavaPlugin

public class EmbeddedSamplePlugin : JavaPlugin() {
    private var bridge: PunishmentBridge? = null

    override fun onEnable() {
        when (val result = PaperPunishmentBridge.builder(this).currentServer(server.name).build()) {
            is BridgeStartResult.Ready -> bridge = result.bridge
            is BridgeStartResult.Conflict -> error("Choose a punishment provider: ${result.providerIds}")
            is BridgeStartResult.Unavailable -> error(result.reason)
            is BridgeStartResult.Failed -> throw result.cause
        }
    }

    override fun onDisable() {
        bridge?.close()
        bridge = null
    }
}
