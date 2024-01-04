import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingLayer
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingsSpec
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.download.Download
import net.fabricmc.loom.util.srg.Tsrg2Writer
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.*
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.nio.file.FileSystems
import java.nio.file.StandardOpenOption
import kotlin.io.path.writeText

plugins {
    java
    `maven-publish`
    id("me.qoomon.git-versioning") version "6.3.+"
    id("org.cadixdev.licenser") version "0.6.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    // Used for mapping tools only, provides TSRG writer on top of mappings-io
    id("dev.architectury.loom") version "1.4-SNAPSHOT" apply false
}

val versionMc: String by rootProject
val versionForge: String by rootProject
val versionLoaderUpstream: String by rootProject
val versionYarn: String by project

group = "dev.su5ed.sinytra"
version = "0.0.0-SNAPSHOT"

gitVersioning.apply {
    rev {
        version =
            "\${describe.tag.version.major}.\${describe.tag.version.minor}.\${describe.tag.version.patch.plus.describe.distance}+$versionLoaderUpstream+$versionMc"
    }
}

license {
    header("HEADER")
    exclude("net/fabricmc/loader/impl/lib/gson/**")
    exclude("**/*.properties")
}

val yarnMappings: Configuration by configurations.creating
val shade: Configuration by configurations.creating

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

sourceSets {
    main {
        java {
            srcDir("src/main/legacyJava")
        }
    }
}

configurations.implementation {
    extendsFrom(shade)
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
    implementation(group = "net.neoforged", name = "neoforge", version = versionForge)
    yarnMappings(group = "net.fabricmc", name = "yarn", version = versionYarn)

    shade("net.minecraftforge:srgutils:0.5.4")
    implementation("org.ow2.sat4j:org.ow2.sat4j.core:2.3.6")
    implementation("org.ow2.sat4j:org.ow2.sat4j.pb:2.3.6")

    testCompileOnly("org.jetbrains:annotations:23.0.0")
    // Unit testing for mod metadata
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

val downloadMojmaps by tasks.registering {
    val outputDir = project.layout.buildDirectory.dir(name).get()
    val outputFile = outputDir.file("mojmap.tsrg")
    inputs.property("versionMc", versionMc)
    outputs.file(outputFile)
    extra["outputFile"] = outputFile.asFile

    doLast {
        val cache = project.layout.buildDirectory.dir("tmp/$name").get()
        val provider = MinecraftMetadataProvider(
            MinecraftMetadataProvider.Options(
                versionMc,
                Constants.VERSION_MANIFESTS,
                Constants.EXPERIMENTAL_VERSIONS,
                null,
                cache.file("version_manifest.json").asFile.toPath(),
                cache.file("experimental_version_manifest.json").asFile.toPath(),
                cache.file("minecraft-info.json").asFile.toPath()
            )
        ) { Download.create(it) }

        val clientMappingsPath = cache.file("mojang/client.txt").asFile.toPath()
        val serverMappingsPath = cache.file("mojang/server.txt").asFile.toPath()

        val clientMappings = provider.versionMeta.download("client_mappings")
        Download.create(clientMappings.url)
            .sha1(clientMappings.sha1)
            .downloadPath(clientMappingsPath)
        val serverMappings = provider.versionMeta.download("server_mappings")
        Download.create(serverMappings.url)
            .sha1(serverMappings.sha1)
            .downloadPath(serverMappingsPath)

        val mojMaps = MojangMappingLayer(
            versionMc,
            clientMappingsPath,
            serverMappingsPath,
            true,
            project.logger,
            MojangMappingsSpec.SilenceLicenseOption { true })
        val mappings = MemoryMappingTree()
        mojMaps.visit(mappings)
        outputFile.asFile.toPath().writeText(
            Tsrg2Writer.serialize(mappings),
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }
}

val createMappings by tasks.registering(GenerateMergedMappingsTask::class) {
    dependsOn(downloadMojmaps)
    inputYarnMappings.set { yarnMappings.singleFile }
    inputMojangMappings.set { downloadMojmaps.get().extra["outputFile"] as File }
}

tasks {
    setOf(jar, shadowJar).forEach { provider ->
        provider.configure {
            from(createMappings.flatMap { it.outputFile }) { rename { "mappings.tsrg" } }
            manifest.attributes(
                "FMLModType" to "LANGPROVIDER",
                "Automatic-Module-Name" to "net.fabricmc.loader",
                "Implementation-Version" to archiveVersion.get()
            )
        }
    }

    shadowJar {
        configurations = listOf(shade)
        relocate("net.minecraftforge.srgutils", "reloc.net.minecraftforge.srgutils")
        archiveClassifier.set("full")
    }

    withType<GenerateModuleMetadata> {
        isEnabled = false
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

@CacheableTask
open class GenerateMergedMappingsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputYarnMappings: RegularFileProperty = project.objects.fileProperty()

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputMojangMappings: RegularFileProperty = project.objects.fileProperty()

    @get:OutputFile
    val outputFile: RegularFileProperty =
        project.objects.fileProperty().convention(project.layout.buildDirectory.file("$name/output.tsrg"))

    @TaskAction
    fun execute() {
        // OFFICIAL -> MOJANG -> INTERMEDIARY
        val yarnTree = MemoryMappingTree()
        val renamer = MappingNsRenamer(yarnTree, mapOf(
            MappingsNamespace.NAMED.toString() to MappingsNamespace.MOJANG.toString()
        ))
        MappingReader.read(inputMojangMappings.get().asFile.toPath(), renamer)
        FileSystems.newFileSystem(inputYarnMappings.asFile.get().toPath()).use {
            val mappings = it.getPath("mappings", "mappings.tiny")
            val selector = MappingDstNsReorder(yarnTree, MappingsNamespace.INTERMEDIARY.toString())
            MappingReader.read(mappings, selector)
        }

        // Filter out entries for which there is no mojang mapping
        val filtered = MemoryMappingTree()
        val toOfiSource = MappingSourceNsSwitch(filtered, MappingsNamespace.OFFICIAL.toString())
        val toMojSource = MappingSourceNsSwitch(toOfiSource, MappingsNamespace.MOJANG.toString(), true)
        yarnTree.accept(toMojSource)

        // OFFICIAL -> INTERMEDIARY -> MOJANG
        val completed = MemoryMappingTree()
        val reorder = MappingDstNsReorder(completed, MappingsNamespace.INTERMEDIARY.toString(), MappingsNamespace.MOJANG.toString())
        val completer = MappingNsCompleter(
            reorder,
            mapOf(MappingsNamespace.INTERMEDIARY.toString() to MappingsNamespace.OFFICIAL.toString())
        )
        filtered.accept(completer)

        // OFFICIAL -> INTERMEDIARY -> MOJANG
        outputFile.get().asFile.toPath().writeText(
            Tsrg2Writer.serialize(completed),
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
    }
}