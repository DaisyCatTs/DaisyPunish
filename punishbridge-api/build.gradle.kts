plugins {
    alias(libs.plugins.binary.compatibility)
    alias(libs.plugins.cyclonedx)
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)
}

publishing.publications.named<MavenPublication>("maven") {
    pom.licenses {
        license {
            name.set("Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
        }
    }
}
