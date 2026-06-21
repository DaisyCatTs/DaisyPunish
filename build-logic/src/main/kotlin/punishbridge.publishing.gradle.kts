import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

plugins {
    `maven-publish`
    signing
}

configure<PublishingExtension> {
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

val signingKey = providers.environmentVariable("MAVEN_SIGNING_KEY")
if (signingKey.isPresent) {
    configure<SigningExtension> {
        useInMemoryPgpKeys(signingKey.get(), providers.environmentVariable("MAVEN_SIGNING_PASSWORD").orNull)
    }
    // Sign every publication, including ones declared after this convention is applied.
    val signing = the<SigningExtension>()
    the<PublishingExtension>().publications.configureEach {
        signing.sign(this)
    }
}
