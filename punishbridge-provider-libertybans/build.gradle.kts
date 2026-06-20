dependencies {
    implementation(project(":punishbridge-paper"))
    compileOnly(libs.paper.api)
    compileOnly(libs.libertybans.api)
    implementation(libs.coroutines.jdk8)
}

publishing.publications.named<MavenPublication>("maven") {
    pom.licenses {
        license {
            name.set("GNU Affero General Public License v3.0")
            url.set("https://www.gnu.org/licenses/agpl-3.0.html")
        }
    }
}
