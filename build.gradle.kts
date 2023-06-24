import net.minecraftforge.gradle.common.util.RunConfig

plugins {
    java
    `maven-publish`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
}

// TODO Api compat check
val versionMc: String by rootProject
val versionLoaderUpstream: String by rootProject
val implVersion = "1.0.2"

group = "dev.su5ed.sinytra"
version = "$implVersion+$versionLoaderUpstream"

java {
    withSourcesJar()
}

minecraft {
    mappings("official", versionMc)

    runs {
        val config = Action<RunConfig> {
            property("forge.logging.console.level", "debug")
            property("forge.logging.markers", "REGISTRIES,SCAN,FMLHANDSHAKE")
            workingDirectory = project.file("run").canonicalPath

            mods {
                create("fabric_loader") {
                    sources(sourceSets.main.get())
                }
            }
        }

        create("client", config)
        create("server", config)
    }
}

sourceSets {
    main {
        java {
            srcDirs("src/main/legacyJava")
        }
    }
}

repositories {
    mavenCentral()
    maven {
        name = "MinecraftForge"
        url = uri("https://maven.minecraftforge.net")
    }
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net")
    }
}

dependencies {
    minecraft(group = "net.minecraftforge", name = "forge", version = "$versionMc-45.0.64")

    implementation("net.minecraftforge:fmlloader:1.19.4-45.1.0")
    implementation("net.minecraftforge:srgutils:0.5.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

tasks.withType<GenerateModuleMetadata> {
    isEnabled = false
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            fg.component(this)
        }
    }

    repositories {
        maven {
            name = "Su5eD"
            url = uri("https://maven.su5ed.dev/releases")
            credentials {
                username = System.getenv("MAVEN_USER") ?: "not"
                password = System.getenv("MAVEN_PASSWORD") ?: "set"
            }
        }
    }
}