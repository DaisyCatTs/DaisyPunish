plugins {
    id("punishbridge.kotlin-library")
    id("punishbridge.published")
}

dependencies {
    implementation(project(":punishbridge-paper"))
    compileOnly(libs.paper.api)
    compileOnly(libs.litebans.api)
}

publishing.publications.named<MavenPublication>("maven") {
    pom.licenses {
        license {
            name.set("Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
}
