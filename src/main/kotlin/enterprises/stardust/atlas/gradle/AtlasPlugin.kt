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
import enterprises.stardust.atlas.gradle.data.VersionJson
import enterprises.stardust.atlas.gradle.data.VersionManifest
import enterprises.stardust.atlas.gradle.feature.remap.RemapJar
import enterprises.stardust.atlas.gradle.feature.runtime.Download
import enterprises.stardust.atlas.gradle.feature.stubgen.GenStubs
import enterprises.stardust.stargrad.StargradPlugin
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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

    private lateinit var downloadClient: Download
    private lateinit var downloadServer: Download

    override fun applyPlugin() {
        with(project) {
            AtlasCache.project = this

            applyPlugin<JavaLibraryPlugin>()

            versionManifest = fetchVersionManifest(
                AtlasCache.cacheDir.resolve("version_manifest_v2.json")
            )
            println(
                PROPAGANDA.format(
                    versionManifest.latest.release, versionManifest.latest.snapshot
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

            downloadClient = tasks.create("downloadClient", Download::class.java)

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
                val facadesSet = create(FACADES_SOURCESET)

                // This is pretty shit since it's also adding the
                // kotlin runtime to the classpath of the mappings set.
                // This is unfortunately unavoidable to prevent annoying
                // compiler warnings about missing annotations constants.
                dependencies.add(
                    facadesSet.compileOnlyConfigurationName, ATLAS_ANNOTATIONS
                )

                (tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).apply {
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
                "Found multiple runtimes in the runtimeOnly configuration: "
                    + runtimesFound.joinToString { it.version.toString() }
            )
        }

        val minecraftRuntime = runtimesFound.first()
        handleMinecraftRuntime(minecraftRuntime)
    }

    private fun handleMinecraftRuntime(
        minecraftRuntime: Dependency,
    ) = with(project) {
        println("Found Minecraft runtime: $minecraftRuntime")

        //download appropriate runtime
        val versionMeta = versionManifest.versions.firstOrNull {
            it.id == minecraftRuntime.version
        } ?: throw (IllegalStateException(
            "Could not find Minecraft version ${minecraftRuntime.version}"
        ).also {
            logger.error(it.message, it)
        })

        val versionJson = VersionJson.fetch(versionMeta.url)
        val client = versionJson.downloads.client
        val server = versionJson.downloads.server
        if (server == null) {
            logger.warn("Server download not available for $minecraftRuntime")
        }

        print("Downloading Minecraft client JAR...")
        System.out.flush()
        val clientJar = AtlasCache.cacheDependency(
            minecraftRuntime.group!!,
            minecraftRuntime.name,
            minecraftRuntime.version!!,
            classifier = "client",
            url = client.url,
        ) { client.sha1 }
        println(" Done")

        if (server != null) {
            print("Downloading Minecraft server JAR...")
            System.out.flush()
            val serverJar = AtlasCache.cacheDependency(
                minecraftRuntime.group!!,
                minecraftRuntime.name,
                minecraftRuntime.version!!,
                classifier = "server",
                url = server.url,
            ) { server.sha1 }
            println(" Done")
        }

        //download libraries
        // -> download proper natives

    }

    private fun fetchVersionManifest(path: Path): VersionManifest {
        var fetched = false
        val manifest = path.takeIf { Files.exists(it) }?.let {
            VersionManifest.parse(it.readText())
        } ?: run {
            fetched = true
            VersionManifest.fetch()
        }

        // if freshly fetched, just return it
        if (fetched) {
            Files.write(
                path,
                objectMapper.writeValueAsString(manifest).toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            return manifest
        }

        // check if current is latest
        try {
            val freshManifest = VersionManifest.fetch()
            Files.write(
                path,
                objectMapper.writeValueAsString(freshManifest).toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            return freshManifest
        } catch (exception: IOException) {
            System.err.println("Failed to fetch latest version manifest, using cached version.")
            if (System.getProperty("atlas.gradle.debug", "false").toBoolean()) {
                exception.printStackTrace()
            }
        }
        return manifest
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

        internal const val ATLAS_ANNOTATIONS = "com.github.atlas-fw:annotations:a63069a7b4"
    }
}
