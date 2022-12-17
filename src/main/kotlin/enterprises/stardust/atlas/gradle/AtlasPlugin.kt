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
import enterprises.stardust.atlas.gradle.feature.remap.RemapJar
import enterprises.stardust.atlas.gradle.feature.runtime.Download
import enterprises.stardust.atlas.gradle.feature.runtime.runtimeJar
import enterprises.stardust.atlas.gradle.feature.stubgen.GenStubs
import enterprises.stardust.atlas.gradle.metadata.VersionJson
import enterprises.stardust.atlas.gradle.metadata.VersionManifest
import enterprises.stardust.atlas.gradle.metadata.fetch
import enterprises.stardust.atlas.gradle.metadata.from
import enterprises.stardust.stargrad.StargradPlugin
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.internal.hash.Hashing
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
    ) = with(project) {
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

        val clientHash = AtlasCache.resolveDependencyPath(
            dep.group + ":" + dep.name + ":" + dep.version + ":client",
        ).let { (parent, name) ->
            val file = parent.resolve(name)
            if (file.exists())
                Hashing.sha1().hashBytes(file.readBytes()).toString()
            else
                null
        } ?: UUID.randomUUID().toString()

        val downloadClient = project.tasks.register(
            "downloadClient",
            Download::class.java,
            dep.group + ":" + dep.name + ":" + dep.version + ":client",
            versionJson.downloads.client.url,
            versionJson.downloads.client.sha1,
            clientHash,
        ).also {
            it.get().group = "atlas gradle"
        }

        project.tasks.register(
            "runClient",
            JavaExec::class.java,
        ) {
            it.group = "atlas gradle"
            it.dependsOn(downloadClient)

            it.mainClass.set("enterprises.stardust.atlas.dev.Entrypoint")
            it.classpath += files(
                runtimeJar,
                downloadClient.get().target,
                configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME),
            )
            it.jvmArgs = listOf<String>()
            it.args = listOf<String>()
        }

        if (versionJson.downloads.server == null) return@with

        val serverHash = AtlasCache.resolveDependencyPath(
            dep.group + ":" + dep.name + ":" + dep.version + ":server",
        ).let { (parent, name) ->
            val file = parent.resolve(name)
            if (file.exists())
                Hashing.sha1().hashBytes(file.readBytes()).toString()
            else
                null
        } ?: UUID.randomUUID().toString()

        val downloadServer = project.tasks.register(
            "downloadServer",
            Download::class.java,
            dep.group + ":" + dep.name + ":" + dep.version + ":server",
            versionJson.downloads.server!!.url,
            versionJson.downloads.server!!.sha1,
            serverHash,
        ).also {
            it.get().group = "atlas gradle"
        }

        project.tasks.register(
            "runServer",
            JavaExec::class.java,
        ) {
            it.group = "atlas gradle"
            it.dependsOn(downloadServer)

            it.mainClass.set("enterprises.stardust.atlas.dev.Entrypoint")
            it.classpath += files(
                runtimeJar,
                downloadServer.get().target,
                configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME),
            )
            it.jvmArgs = listOf<String>()
            it.args = listOf<String>()
        }
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
            ╭──( Atlas Gradle Plugin )──•
            │ ▶ Information:
            │ Atlas is currently in alpha, and as such, the API is
            │ subject to change. If you're interested in contributing,
            │ please visit https://stardust.enterprises/discord
            ╰───────────────────────────•
        """.trimIndent()

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
