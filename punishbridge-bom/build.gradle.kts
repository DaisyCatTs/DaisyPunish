plugins {
    `java-platform`
    id("punishbridge.publishing")
}

javaPlatform.allowDependencies()

dependencies {
    constraints {
        rootProject.subprojects
            .filter { it.name.startsWith("punishbridge-") && it.name != project.name }
            .forEach { api(project(it.path)) }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            pom {
                name.set("PunishBridge BOM")
                description.set("Version alignment for PunishBridge modules")
                url.set("https://github.com/DaisyCatTs/DaisyPunish")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
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
}
