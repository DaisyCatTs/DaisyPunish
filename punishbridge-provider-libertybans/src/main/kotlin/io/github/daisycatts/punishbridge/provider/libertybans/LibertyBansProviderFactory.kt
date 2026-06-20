package io.github.daisycatts.punishbridge.provider.libertybans

import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.paper.PaperProviderContext
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProvider
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProviderFactory
import org.bukkit.plugin.Plugin

public class LibertyBansProviderFactory : PaperPunishmentProviderFactory {
    override val id: String = "libertybans"
    override val tier: ProviderTier = ProviderTier.DEDICATED

    override fun isAvailable(plugin: Plugin): Boolean =
        plugin.server.pluginManager
            .getPlugin("LibertyBans")
            ?.isEnabled == true

    override fun create(context: PaperProviderContext): PaperPunishmentProvider = LibertyBansProvider(context)
}
