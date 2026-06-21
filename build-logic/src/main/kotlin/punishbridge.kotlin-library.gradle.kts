import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("punishbridge.kotlin-base")
    id("org.jetbrains.dokka")
}

val libs = the<LibrariesForLibs>()

configure<JavaPluginExtension> {
    withSourcesJar()
    withJavadocJar()
}

tasks.named<Jar>("javadocJar") {
    dependsOn("dokkaGeneratePublicationHtml")
    from(layout.buildDirectory.dir("dokka/html"))
}

dependencies {
    "testImplementation"(platform(libs.junit.bom))
    "testImplementation"(libs.junit.jupiter)
    "testRuntimeOnly"(libs.junit.launcher)
}
