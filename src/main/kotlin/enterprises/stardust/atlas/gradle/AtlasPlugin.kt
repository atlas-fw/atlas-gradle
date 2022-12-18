/*
 * This file is part of atlas-gradle.
 *
 * atlas-gradle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * atlas-gradle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with atlas-gradle.  If not, see <https://www.gnu.org/licenses/>.
 */

package enterprises.stardust.atlas.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.hash.Hashing
import enterprises.stardust.atlas.gradle.feature.remap.RemapJar
import enterprises.stardust.atlas.gradle.feature.runtime.Download
import enterprises.stardust.atlas.gradle.feature.runtime.DownloadClientLibs
import enterprises.stardust.atlas.gradle.feature.runtime.runtimeJar
import enterprises.stardust.atlas.gradle.feature.stubgen.GenStubs
import enterprises.stardust.atlas.gradle.metadata.VersionJson
import enterprises.stardust.atlas.gradle.metadata.VersionManifest
import enterprises.stardust.atlas.gradle.metadata.fetch
import enterprises.stardust.atlas.gradle.metadata.from
import enterprises.stardust.stargrad.StargradPlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * Shared instance of Jackson's [ObjectMapper], configured with Kotlin adapters.
 */
internal val objectMapper: ObjectMapper = jacksonObjectMapper()

/**
 * Atlas Gradle plugin.
 *
 * @author xtrm
 * @since 0.0.1
 */
open class AtlasPlugin : StargradPlugin() {
    override val id: String = "enterprises.stardust.atlas.gradle"
    internal lateinit var atlasExtension: AtlasExtension
    internal lateinit var versionManifest: VersionManifest

    override fun applyPlugin() {
        with(project) {
            AtlasCache.project = this

            applyPlugin<JavaLibraryPlugin>()

            versionManifest = fetchVersionManifest()
            println(
                PROPAGANDA.format(
                    versionManifest.latest.release,
                    versionManifest.latest.snapshot
                )
            )

            repositories {
                mavenLocal()
                mavenCentral()
                maven {
                    it.name = "Jitpack"
                    it.url = uri("https://jitpack.io")
                }
            }

            atlasExtension = registerExtension()
            registerTask<RemapJar>().also {
                tasks.getByName("assemble").dependsOn(it)
            }

            val genStubs = registerTask<GenStubs>()
            configurations {
                fun createAndExtend(name: String, extendsFrom: String) =
                    maybeCreate(name).also {
                        getByName(extendsFrom).extendsFrom(it)
                    }

                createAndExtend(
                    FACADE_CONFIGURATION,
                    JavaPlugin.API_CONFIGURATION_NAME
                )
                createAndExtend(
                    REMAPPED_CONFIGURATION,
                    JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
                )
                maybeCreate(ATLAS_RUNTIME_CONFIGURATION)
            }

            extensions.findByType(JavaPluginExtension::class.java)!!.sourceSets {
                val facadesSet = maybeCreate(FACADES_SOURCESET)

                // This is pretty shit since it's also adding the kotlin runtime
                // to the classpath of the mappings set. This is unfortunately
                // unavoidable to prevent annoying compiler warnings about
                // missing annotations constants.
                dependencies.add(
                    facadesSet.compileOnlyConfigurationName,
                    ATLAS_ANNOTATIONS
                )

                (tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar) {
                    from(facadesSet.output)
                }

                filter { it.name != FACADES_SOURCESET }.forEach {
                    tasks.getByName(it.compileJavaTaskName).dependsOn(genStubs)
                }

                tasks.create(facadesSet.jarTaskName, Jar::class.java) {
                    it.from(facadesSet.output)
                    it.archiveClassifier.set(facadesSet.name)
                    // Let's hope this isn't a terrible idea down the line
                    it.duplicatesStrategy = DuplicatesStrategy.INCLUDE
                }.also {
                    genStubs.get().dependsOn(it)
                }
            }

            afterEvaluate {
                genStubs.get().init()
            }
        }
    }

    override fun afterEvaluate() {
        val runtime = project.configurations.getByName(ATLAS_RUNTIME_CONFIGURATION)

        if (runtime.dependencies.any { it.group != MOJANG_GROUP && it.name == MINECRAFT_ID }) {
            throw IllegalStateException(
                "The group of a Minecraft dependency must be $MOJANG_GROUP"
            )
        }

        val runtimesFound = runtime.dependencies.filter { dep ->
            dep.group == MOJANG_GROUP && dep.name == MINECRAFT_ID
        }.onEach { runtime.dependencies.remove(it) }

        if (runtimesFound.isEmpty()) return
        if (runtimesFound.size > 1) {
            throw IllegalStateException(
                "Found multiple runtimes in the runtimeOnly configuration: " +
                    runtimesFound.joinToString { it.version.toString() }
            )
        }

        val minecraftRuntime = runtimesFound.first()
        handleMinecraftRuntime(minecraftRuntime)
    }

    private fun handleMinecraftRuntime(
        dep: Dependency,
    ): Unit = with(project) {
        println("Found Minecraft runtime: ${dep.group}:${dep.name}:${dep.version}")

        // download appropriate runtime
        val versionMeta = versionManifest.versions.firstOrNull {
            it.id == dep.version
        } ?: throw IllegalStateException(
            "Could not find Minecraft version ${dep.version}"
        ).also {
            logger.error(it.message, it)
        }
        val versionJson = VersionJson.fetch(versionMeta.url)

        setupMinecraft(this, dep, versionJson, "client")

        if (versionJson.downloads.server == null) return@with
        setupMinecraft(this, dep, versionJson, "server")
    }

    @Suppress("DEPRECATION")
    private fun setupMinecraft(
        project: Project,
        dep: Dependency,
        versionJson: VersionJson,
        side: String,
    ) = with(project) {
        val notation = "${dep.group}:${dep.name}:${dep.version}:$side"
        val taskSuffix = side.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault())
            else it.toString()
        }
        val hash = AtlasCache.resolveDependencyPath(notation)
            .let { (parent, name) ->
                parent.resolve(name).run {
                    if (exists()) Hashing.sha1().hashBytes(readBytes())
                    else UUID.randomUUID()
                }.toString()
            }

        val artifact =
            if (side == "client") versionJson.downloads.client
            else versionJson.downloads.server!!

        val downloadTask = tasks.create(
            "download$taskSuffix",
            Download::class.java,
            notation,
            artifact.url,
            artifact.sha1,
            hash,
        ).also { it.group = TASK_GROUP }

        val downloadLibsTask =
            if (side == "client")
                tasks.create(
                    DOWNLOAD_CLIENT_LIBS_TASK,
                    DownloadClientLibs::class.java,
                    versionJson.libraries,
                ).also { it.group = TASK_GROUP }
            else null

        tasks.create(
            "run$taskSuffix",
            JavaExec::class.java,
        ) { exec ->
            exec.group = TASK_GROUP
            exec.dependsOn(downloadTask)
            downloadLibsTask?.let { exec.dependsOn(it) }

            exec.mainClass.set("enterprises.stardust.atlas.dev.Entrypoint")
            exec.workingDir = projectDir.resolve("run/$side")
                .also { workingDir -> exec.doFirst { workingDir.mkdirs() } }
            exec.classpath += files(
                runtimeJar,
                downloadTask.target,
                configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME),
            )
            exec.jvmArgs = listOf<String>()
            exec.args = listOf<String>()
        }

        Unit
    }

    private fun fetchVersionManifest(): VersionManifest {
        val filePath = AtlasCache.cacheDir.resolve("version_metadata_v2.json")
        val skipDownload = filePath.exists()

        try {
            val manifest = VersionManifest.fetch()
            Files.write(
                filePath,
                objectMapper.writeValueAsString(manifest).toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        } catch (throwable: Throwable) {
            if (skipDownload) {
                throwable.printStackTrace()
                println("Failed to fetch version manifest, using cached version")
            } else {
                throw throwable
            }
        }

        return VersionManifest.from(filePath.readText())
    }

    companion object {
        private val PROPAGANDA = """
            /--( Atlas Gradle Plugin )--o
            | > Information:
            | Atlas is currently in alpha, and as such, the API is
            | subject to change. If you're interested in contributing,
            | please visit https://stardust.enterprises/discord
            \---------------------------o
        """.trimIndent()

        internal const val TASK_GROUP = "atlas framework"
        internal const val DOWNLOAD_CLIENT_LIBS_TASK = "downloadClientLibs"

        internal const val MOJANG_GROUP = "com.mojang"
        internal const val MINECRAFT_ID = "minecraft"

        internal const val FACADES_SOURCESET = "facades"

        internal const val FACADE_CONFIGURATION = "facade"
        internal const val ATLAS_RUNTIME_CONFIGURATION = "atlasRuntime"
        internal const val REMAPPED_CONFIGURATION = "atlasInternalRemapped"

        internal const val ATLAS_ANNOTATIONS =
            "com.github.atlas-fw:annotations:a63069a7b4"
    }
}
