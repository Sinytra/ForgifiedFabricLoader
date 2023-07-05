import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.minecraftforge.gradle.common.util.RunConfig
import java.nio.file.FileSystems
import java.nio.file.StandardOpenOption
import net.fabricmc.loom.util.srg.SrgMerger
import net.fabricmc.loom.util.srg.Tsrg2Writer
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingDstNsReorder
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG
import net.minecraftforge.srgutils.IMappingFile.Format
import kotlin.io.path.writeText

plugins {
    java
    `maven-publish`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    // Used for mapping tools only, provides TSRG writer on top of mappings-io
    id("dev.architectury.loom") version "1.2-SNAPSHOT" apply false
}

// TODO Api compat check
val versionMc: String by rootProject
val versionLoaderUpstream: String by rootProject
val versionYarn: String by project
val implVersion = "1.0.4"

group = "dev.su5ed.sinytra"
version = "$implVersion+$versionLoaderUpstream"

val yarnMappings: Configuration by configurations.creating

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
    yarnMappings(group = "net.fabricmc", name = "yarn", version = versionYarn)

    implementation("net.minecraftforge:srgutils:0.5.4")
}

tasks {
    jar {
        from(createMappings.flatMap { it.outputFile }) { rename { "mappings.tsrg" } }
    }

    withType<GenerateModuleMetadata> {
        isEnabled = false
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
        // OFFICIAL -> INTERMEDIARY -> NAMED
        val yarnTree = MemoryMappingTree()
        FileSystems.newFileSystem(inputYarnMappings.asFile.get().toPath()).use {
            val mappings = it.getPath("mappings", "mappings.tiny")
            MappingReader.read(mappings, yarnTree)
        }

        val officialTreePath = temporaryDir.resolve("mappings-base.tsrg").toPath()
        officialTreePath.writeText(Tsrg2Writer.serialize(yarnTree), Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

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