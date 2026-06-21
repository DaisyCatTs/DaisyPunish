plugins {
    `kotlin-dsl`
}

/** Resolve a plugin's marker artifact from the version catalog so convention plugins can apply it. */
fun pluginDependency(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    implementation(pluginDependency(libs.plugins.kotlin.jvm))
    implementation(pluginDependency(libs.plugins.dokka))
    implementation(pluginDependency(libs.plugins.detekt))
    implementation(pluginDependency(libs.plugins.ktlint))

    // Expose the version catalog to the precompiled convention plugins.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
