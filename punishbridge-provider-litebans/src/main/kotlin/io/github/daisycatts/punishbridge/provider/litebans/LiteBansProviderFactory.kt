package io.github.daisycatts.punishbridge.provider.litebans

import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.paper.PaperProviderContext
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProvider
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProviderFactory
import org.bukkit.plugin.Plugin

public class LiteBansProviderFactory : PaperPunishmentProviderFactory {
    override val id: String = "litebans"
    override val tier: ProviderTier = ProviderTier.DEDICATED

    override fun isAvailable(plugin: Plugin): Boolean =
        plugin.server.pluginManager
            .getPlugin("LiteBans")
            ?.isEnabled == true

    override fun create(context: PaperProviderContext): PaperPunishmentProvider = LiteBansProvider(context)
}
