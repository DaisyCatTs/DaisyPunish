import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

plugins {
    `maven-publish`
    signing
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
    repositories {
        maven {
            name = "Central"
            url =
                uri(
                    if (version.toString().endsWith("SNAPSHOT")) {
                        "https://central.sonatype.com/repository/maven-snapshots/"
                    } else {
                        "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                    },
                )
            credentials {
                username = providers.environmentVariable("CENTRAL_USERNAME").orNull
                password = providers.environmentVariable("CENTRAL_TOKEN").orNull
            }
        }
    }
}

configure<SigningExtension> {
    val key = providers.environmentVariable("MAVEN_SIGNING_KEY")
    val password = providers.environmentVariable("MAVEN_SIGNING_PASSWORD")
    if (key.isPresent) {
        useInMemoryPgpKeys(key.get(), password.orNull)
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}
