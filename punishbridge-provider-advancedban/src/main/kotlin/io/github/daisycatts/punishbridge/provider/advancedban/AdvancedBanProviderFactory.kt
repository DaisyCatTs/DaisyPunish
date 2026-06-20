package io.github.daisycatts.punishbridge.provider.advancedban

import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.paper.PaperProviderContext
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProvider
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProviderFactory
import org.bukkit.plugin.Plugin

public class AdvancedBanProviderFactory : PaperPunishmentProviderFactory {
    override val id: String = "advancedban"
    override val tier: ProviderTier = ProviderTier.DEDICATED

    override fun isAvailable(plugin: Plugin): Boolean =
        plugin.server.pluginManager
            .getPlugin("AdvancedBan")
            ?.isEnabled == true

    override fun create(context: PaperProviderContext): PaperPunishmentProvider = AdvancedBanProvider(context)
}
