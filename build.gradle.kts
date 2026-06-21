import org.gradle.api.tasks.bundling.AbstractArchiveTask

group = "io.github.daisycatts"
version = providers.gradleProperty("releaseVersion").getOrElse("2.0.0-SNAPSHOT")

allprojects {
    group = rootProject.group
    version = rootProject.version

    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
