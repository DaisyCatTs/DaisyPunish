repositories {
    maven {
        url = uri("https://mvn-repo.arim.space/lesser-gpl3/")
    }
    maven {
        url = uri("https://mvn-repo.arim.space/gpl3/")
    }
     maven {
         url = uri("https://mvn-repo.arim.space/affero-gpl3/")
     }
}


dependencies {
    shadow(project(":core"))
    compileOnly("space.arim.libertybans:bans-api:1.1.2")
}