import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.binary.compatibility) apply false
    alias(libs.plugins.cyclonedx) apply false
    alias(libs.plugins.shadow) apply false
    `maven-publish`
}

group = "io.github.daisycatts"
version = providers.gradleProperty("releaseVersion").getOrElse("2.0.0-SNAPSHOT")

val junitBomDependency = libs.junit.bom
val junitJupiterDependency = libs.junit.jupiter
val junitLauncherDependency = libs.junit.launcher

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

subprojects {
    if (name == "punishbridge-bom") return@subprojects
    if (path == ":samples" || path.startsWith(":samples:")) return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
        withJavadocJar()
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            allWarningsAsErrors.set(true)
            freeCompilerArgs.add("-Xjsr305=strict")
            explicitApi()
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.named<Jar>("javadocJar") {
        dependsOn("dokkaGeneratePublicationHtml")
        from(layout.buildDirectory.dir("dokka/html"))
    }

    dependencies {
        "testImplementation"(platform(junitBomDependency))
        "testImplementation"(junitJupiterDependency)
        "testRuntimeOnly"(junitLauncherDependency)
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set("A typed Kotlin punishment-system bridge for Paper servers")
                    url.set("https://github.com/DaisyCatTs/DaisyPunish")
                    scm {
                        connection.set("scm:git:https://github.com/DaisyCatTs/DaisyPunish.git")
                        developerConnection.set("scm:git:ssh://git@github.com/DaisyCatTs/DaisyPunish.git")
                        url.set("https://github.com/DaisyCatTs/DaisyPunish")
                    }
                    developers {
                        developer {
                            id.set("DaisyCatTs")
                            name.set("DaisyCatTs")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "Central"
                url = uri(
                    if (version.toString().endsWith("SNAPSHOT")) {
                        "https://central.sonatype.com/repository/maven-snapshots/"
                    } else {
                        "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                    },
                )
                credentials {
                    username = providers.environmentVariable("CENTRAL_USERNAME").orNull
                    password = providers.environmentVariable("CENTRAL_TOKEN").orNull
                }
            }
        }
    }

    extensions.configure<SigningExtension> {
        val key = providers.environmentVariable("MAVEN_SIGNING_KEY")
        val password = providers.environmentVariable("MAVEN_SIGNING_PASSWORD")
        if (key.isPresent) {
            useInMemoryPgpKeys(key.get(), password.orNull)
            sign(extensions.getByType<PublishingExtension>().publications)
        }
    }
}
