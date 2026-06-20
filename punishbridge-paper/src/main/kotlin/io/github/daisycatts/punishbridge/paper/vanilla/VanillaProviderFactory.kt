package io.github.daisycatts.punishbridge.paper.vanilla

import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.paper.PaperProviderContext
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProvider
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProviderFactory
import org.bukkit.plugin.Plugin

public class VanillaProviderFactory : PaperPunishmentProviderFactory {
    override val id: String = "vanilla"
    override val tier: ProviderTier = ProviderTier.SYSTEM

    override fun isAvailable(plugin: Plugin): Boolean = plugin.isEnabled

    override fun create(context: PaperProviderContext): PaperPunishmentProvider = VanillaProvider(context)
}
