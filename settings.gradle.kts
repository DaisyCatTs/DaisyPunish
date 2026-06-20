pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://repo.essentialsx.net/releases/")
        maven("https://mvn-repo.arim.space/lesser-gpl3/")
        maven("https://mvn-repo.arim.space/gpl3/")
        maven("https://mvn-repo.arim.space/affero-gpl3/")
        ivy("https://github.com/EssentialsX/Essentials/releases/download/") {
            name = "EssentialsXGitHubReleases"
            patternLayout {
                artifact("[revision]/[artifact]-[revision].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("net.essentialsx", "EssentialsX") }
        }
    }
}

rootProject.name = "PunishBridge"

include(
    "punishbridge-api",
    "punishbridge-paper",
    "punishbridge-provider-litebans",
    "punishbridge-provider-advancedban",
    "punishbridge-provider-essentialsx",
    "punishbridge-provider-libertybans",
    "punishbridge-testkit",
    "punishbridge-bom",
    "samples:embedded-paper",
)
