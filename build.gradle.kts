plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev") version "2.0.141"
    id("net.neoforged.licenser") version "0.7.5"
    id("net.neoforged.gradleutils") version "5.1.0"
    id("com.gradleup.shadow") version "9.4.1"
}

val versionMc: String by rootProject
val versionNeoForge: String by rootProject
val versionLoaderUpstream: String by rootProject

group = "org.sinytra"
version = "0.0.0-SNAPSHOT"

gradleutils.version {
    branches {
        suffixBranch()
        suffixExemptedBranch(versionMc)
        suffixExemptedBranch("26.1.x")
    }
}
version = "${gradleutils.version}+$versionLoaderUpstream+$versionMc"
println("Version: $version")

license {
    header("HEADER")
    exclude("net/fabricmc/loader/impl/lib/gson/**")
    exclude("**/*.properties")
}

val shade: Configuration by configurations.creating

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

sourceSets {
    main {
        java {
            srcDir("src/main/legacyJava")
        }
    }
}

configurations {
    implementation {
        extendsFrom(shade)
    }
}

neoForge {
    enable {
        version = versionNeoForge
        isDisableRecompilation = true
    }
}

repositories {
    mavenCentral()
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net")
    }
    maven {
        name = "Mojank"
        url = uri("https://libraries.minecraft.net/")
    }
    maven {
        name = "NeoForged"
        url = uri("https://maven.neoforged.net/releases")
    }
}

dependencies {
    shade(jarJar("org.ow2.sat4j:org.ow2.sat4j.core:2.3.6")!!)
    shade(jarJar("org.ow2.sat4j:org.ow2.sat4j.pb:2.3.6")!!)

    testCompileOnly("org.jetbrains:annotations:23.0.0")
    // Unit testing for mod metadata
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

tasks {
    setOf(jar, shadowJar).forEach { provider ->
        provider.configure {
            manifest.attributes(
                "FMLModType" to "LIBRARY",
                "Automatic-Module-Name" to "net.fabricmc.loader",
                "Implementation-Version" to archiveVersion.get()
            )
        }
    }

    shadowJar {
        configurations = listOf(shade)
        relocate("org.sat4j", "reloc.org.sat4j")
        archiveClassifier.set("full")
    }

    assemble {
        dependsOn(shadowJar)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
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
