import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("punishbridge.publishing")
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
                description.set("A typed Kotlin punishment-system bridge for Paper servers")
                url.set("https://github.com/DaisyCatTs/DaisyPunish")
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
