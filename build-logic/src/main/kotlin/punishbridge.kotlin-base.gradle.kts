import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

configure<KotlinJvmProjectExtension> {
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
        exceptionFormat = TestExceptionFormat.FULL
    }
}
