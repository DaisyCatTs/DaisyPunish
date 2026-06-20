dependencies {
    api(project(":punishbridge-api"))
    compileOnly(libs.paper.api)
    implementation(libs.coroutines.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.paper.api)
}

publishing.publications.named<MavenPublication>("maven") {
    pom.licenses {
        license {
            name.set("Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
}
