import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.util.srg.SrgMerger
import net.fabricmc.loom.util.srg.Tsrg2Writer
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingDstNsReorder
import net.fabricmc.mappingio.adapter.MappingNsCompleter
import net.fabricmc.mappingio.adapter.MappingNsRenamer
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.minecraftforge.gradle.common.util.RunConfig
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.srgutils.IMappingFile.Format
import java.nio.file.FileSystems
import java.nio.file.StandardOpenOption
import kotlin.io.path.writeText

plugins {
    java
    `maven-publish`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("me.qoomon.git-versioning") version "6.3.+"
    id("org.cadixdev.licenser") version "0.6.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    // Used for mapping tools only, provides TSRG writer on top of mappings-io
    id("dev.architectury.loom") version "1.2-SNAPSHOT" apply false
}

val versionMc: String by rootProject
val versionForge: String by rootProject
val versionLoaderUpstream: String by rootProject
val versionYarn: String by project

group = "dev.su5ed.sinytra"
version = "0.0.0-SNAPSHOT"

gitVersioning.apply {
    rev {
        version = "\${describe.tag.version.major}.\${describe.tag.version.minor}.\${describe.tag.version.patch.plus.describe.distance}+$versionLoaderUpstream+$versionMc"
    }
}

license {
    header("HEADER")
    exclude("net/fabricmc/loader/impl/lib/gson/**")
    exclude("**/*.properties")
}

val yarnMappings: Configuration by configurations.creating
val shade: Configuration by configurations.creating

val createObfToMcp by tasks.registering(GenerateSRG::class) {
    notch = true
    srg.set(tasks.extractSrg.flatMap { it.output })
    mappings.set(minecraft.mappings)
    format.set(Format.TSRG)
}

val createMappings by tasks.registering(GenerateMergedMappingsTask::class) {
    inputYarnMappings.set { yarnMappings.singleFile }
    inputSrgMappings.set(tasks.extractSrg.flatMap { it.output })
}

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
        name = "MinecraftForge"
        url = uri("https://maven.minecraftforge.net")
    }
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net")
    }
}

dependencies {
    minecraft(group = "net.minecraftforge", name = "forge", version = "$versionMc-$versionForge")
    yarnMappings(group = "net.fabricmc", name = "yarn", version = versionYarn)

    shade("net.minecraftforge:srgutils:0.5.4")
    implementation("org.ow2.sat4j:org.ow2.sat4j.core:2.3.6")
    implementation("org.ow2.sat4j:org.ow2.sat4j.pb:2.3.6")

    testCompileOnly("org.jetbrains:annotations:23.0.0")
    // Unit testing for mod metadata
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
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

@CacheableTask
open class GenerateMergedMappingsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputYarnMappings: RegularFileProperty = project.objects.fileProperty()

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputSrgMappings: RegularFileProperty = project.objects.fileProperty()

    @get:OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty().convention(project.layout.buildDirectory.file("$name/output.tsrg"))

    @TaskAction
    fun execute() {
        // OFFICIAL -> SRG -> INTERMEDIARY
        val yarnTree = MemoryMappingTree()
        val renamed = MappingNsRenamer(yarnTree, mapOf(
            "left" to MappingsNamespace.OFFICIAL.toString(),
            "right" to MappingsNamespace.SRG.toString()
        ))
        MappingReader.read(inputSrgMappings.asFile.get().toPath(), renamed)
        FileSystems.newFileSystem(inputYarnMappings.asFile.get().toPath()).use {
            val mappings = it.getPath("mappings", "mappings.tiny")
            val selector = MappingDstNsReorder(yarnTree, MappingsNamespace.INTERMEDIARY.toString())
            MappingReader.read(mappings, selector)
        }

        // Complete INTERMEDIARY from OFFICIAL
        val completed = MemoryMappingTree()
        val completer = MappingNsCompleter(completed, mapOf(MappingsNamespace.INTERMEDIARY.toString() to MappingsNamespace.OFFICIAL.toString()))
        // Remove SRG, but also add NAMED because SrgMerger demands it
        val selector = MappingDstNsReorder(completer, MappingsNamespace.INTERMEDIARY.toString(), MappingsNamespace.NAMED.toString())
        yarnTree.accept(selector)

        val officialTreePath = temporaryDir.resolve("mappings-base.tsrg").toPath()
        officialTreePath.writeText(Tsrg2Writer.serialize(completed), Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

        // OFFICIAL -> SRG -> INTERMEDIARY -> NAMED
        val mergedTree = SrgMerger.mergeSrg(inputSrgMappings.asFile.get().toPath(), officialTreePath, null, true)
        @Suppress("INACCESSIBLE_TYPE")
        mergedTree.classes.forEach { c: MappingTree.ClassMapping -> c.methods.forEach { it.args.clear() } }

        // OFFICIAL -> SRG -> INTERMEDIARY
        val filteredTree = MemoryMappingTree()
        val destFiler = MappingDstNsReorder(filteredTree, MappingsNamespace.SRG.toString(), MappingsNamespace.INTERMEDIARY.toString())
        mergedTree.accept(destFiler)
        outputFile.get().asFile.toPath().writeText(Tsrg2Writer.serialize(filteredTree), Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
}