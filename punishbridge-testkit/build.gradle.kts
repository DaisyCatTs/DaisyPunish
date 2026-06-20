dependencies {
    api(project(":punishbridge-api"))
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
}

publishing.publications.named<MavenPublication>("maven") {
    pom.licenses {
        license {
            name.set("Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
}
