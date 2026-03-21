import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1" apply true
    `java-library`
    `maven-publish`
}


subprojects {
    apply {
        plugin("java-library")
        plugin("maven-publish")
        plugin("com.github.johnrengelman.shadow")
    }

    version = "1.1.0"
    java.sourceCompatibility = JavaVersion.VERSION_16
    java.targetCompatibility = JavaVersion.VERSION_16

    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            url = uri("https://jitpack.io")
        }
        maven {
            url = uri("https://repo.codemc.io/repository/maven-public/")
        }
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.17-R0.1-SNAPSHOT")
        compileOnly("com.gitlab.ruany:LiteBansAPI:0.5.0")
        compileOnly("com.github.DevLeoko:AdvancedBan:v2.3.0")
    }

    tasks {
        val shadowJar = named<ShadowJar>("shadowJar") {
            configurations = listOf(project.configurations.getByName("shadow"))
            archiveFileName.set("PunishBridge.jar")
        }

        build {
            dependsOn(shadowJar)
        }
    }

    publishing {
        publications {
            create<MavenPublication>("punishbridge") {
                from(components["java"])
                groupId = "rs.jamie"
                artifactId = "punishbridge-${project.name}"
                version = "0.0.1"
            }
        }
        repositories {
            mavenLocal()
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}