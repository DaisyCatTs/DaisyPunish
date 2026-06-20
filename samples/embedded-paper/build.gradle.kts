plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

dependencies {
    implementation(project(":punishbridge-paper"))
    implementation(project(":punishbridge-provider-litebans"))
    compileOnly(libs.paper.api)
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("io.github.daisycatts.punishbridge", "io.github.daisycatts.sample.libs.punishbridge")
}

tasks.build { dependsOn(tasks.shadowJar) }

tasks.runServer {
    minecraftVersion("1.21.11")
}
