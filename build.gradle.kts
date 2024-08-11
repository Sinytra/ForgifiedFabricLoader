import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingLayer
import net.fabricmc.loom.configuration.providers.mappings.mojmap.MojangMappingsSpec
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMetadataProvider
import net.fabricmc.loom.util.download.Download
import net.fabricmc.loom.util.download.DownloadBuilder
import net.fabricmc.loom.util.srg.Tsrg2Writer
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.MappingWriter
import net.fabricmc.mappingio.adapter.*
import net.fabricmc.mappingio.format.MappingFormat
import net.fabricmc.mappingio.tree.MemoryMappingTree
import java.nio.file.FileSystems
import java.nio.file.StandardOpenOption
import java.util.function.Function
import kotlin.io.path.writeText

plugins {
    java
    `maven-publish`
    id("org.cadixdev.licenser") version "0.6.1"
    id("net.neoforged.gradleutils").version("3.0.0-alpha.10")
    // Used for mapping tools only, provides TSRG writer on top of mappings-io
    id("dev.architectury.loom") version "1.7-SNAPSHOT"
}

val versionMc: String by rootProject
val versionForge: String by rootProject
val versionLoaderUpstream: String by rootProject
val versionYarn: String by project

group = "org.sinytra"
version = "0.0.0-SNAPSHOT"

gradleutils.version {
    branches {
        suffixBranch()
        suffixExemptedBranch(versionMc)
        suffixExemptedBranch("1.21.x")
    }
}
version = "${gradleutils.version}+$versionLoaderUpstream+$versionMc"
println("Version: $version")

license {
    header("HEADER")
    exclude("net/fabricmc/loader/impl/lib/gson/**")
    exclude("**/*.properties")
}

val yarnMappings: Configuration by configurations.creating

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
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
    minecraft(group = "com.mojang", name = "minecraft", version = versionMc)
    mappings(loom.officialMojangMappings())
    neoForge(group = "net.neoforged", name = "neoforge", version = versionForge)
    yarnMappings(group = "net.fabricmc", name = "yarn", version = versionYarn)

    api(include("net.minecraftforge:srgutils:0.5.4")!!)
    implementation(include("org.ow2.sat4j:org.ow2.sat4j.core:2.3.6")!!)
    implementation(include("org.ow2.sat4j:org.ow2.sat4j.pb:2.3.6")!!)

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
        val provider =
            MinecraftMetadataProvider::class.java.declaredConstructors[0].apply { isAccessible = true }.newInstance(
                MinecraftMetadataProvider.Options.create(versionMc, project),
                Function<String, DownloadBuilder> { Download.create(it) }
            ) as MinecraftMetadataProvider

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

val createTinyMappings by tasks.registering {
    dependsOn(createMappings)
    val output = layout.buildDirectory.dir(name).get().file("output.tiny")
    outputs.file(output)

    doFirst { 
        val mappings = MemoryMappingTree()
        MappingReader.read(createMappings.get().outputFile.get().asFile.toPath(), mappings)
        mappings.accept(MappingWriter.create(output.asFile.toPath(), MappingFormat.TINY_FILE))
    }
}

tasks {
    jar {
        from(createMappings.flatMap { it.outputFile }) { rename { "mappings.tsrg" } }
        from(createTinyMappings.map { it.outputs.files.singleFile }) { rename { "mappings/mappings.tiny" } }
        manifest.attributes(
            "FMLModType" to "LIBRARY",
            "Automatic-Module-Name" to "net.fabricmc.loader",
            "Implementation-Version" to archiveVersion.get()
        )
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
        val renamer = MappingNsRenamer(
            yarnTree, mapOf(
                MappingsNamespace.NAMED.toString() to MappingsNamespace.MOJANG.toString()
            )
        )
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
        val toInterSource = MappingSourceNsSwitch(toMojSource, MappingsNamespace.INTERMEDIARY.toString(), true)
        yarnTree.accept(toInterSource)

        // OFFICIAL -> INTERMEDIARY -> MOJANG
        val completed = MemoryMappingTree()
        val reorder = MappingDstNsReorder(
            completed,
            MappingsNamespace.INTERMEDIARY.toString(),
            MappingsNamespace.MOJANG.toString()
        )
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
