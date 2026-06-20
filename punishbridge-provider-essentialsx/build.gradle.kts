dependencies {
    implementation(project(":punishbridge-paper"))
    compileOnly(libs.paper.api)
    compileOnly(libs.essentialsx)
}

publishing.publications.named<MavenPublication>("maven") {
    pom.licenses {
        license {
            name.set("GNU General Public License v3.0")
            url.set("https://www.gnu.org/licenses/gpl-3.0.html")
        }
    }
}
