package io.github.daisycatts.punishbridge.provider.essentialsx

import io.github.daisycatts.punishbridge.ProviderTier
import io.github.daisycatts.punishbridge.paper.PaperProviderContext
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProvider
import io.github.daisycatts.punishbridge.paper.PaperPunishmentProviderFactory
import org.bukkit.plugin.Plugin

public class EssentialsXProviderFactory : PaperPunishmentProviderFactory {
    override val id: String = "essentialsx"
    override val tier: ProviderTier = ProviderTier.FALLBACK

    override fun isAvailable(plugin: Plugin): Boolean =
        plugin.server.pluginManager
            .getPlugin("Essentials")
            ?.isEnabled == true

    override fun create(context: PaperProviderContext): PaperPunishmentProvider = EssentialsXProvider(context)
}
