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

import enterprises.stardust.atlas.gradle.data.VersionManifest
import enterprises.stardust.atlas.gradle.remap.RemapJar
import enterprises.stardust.atlas.gradle.stubgen.GenStubs
import enterprises.stardust.stargrad.StargradPlugin
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

open class AtlasPlugin : StargradPlugin() {
    override val id: String = "enterprises.stardust.atlas.gradle"
    internal lateinit var atlasExtension: AtlasExtension
    internal lateinit var versionManifest: VersionManifest

    override fun applyPlugin() {
        with(project) {
            applyPlugin<JavaLibraryPlugin>()

            val cacheDir = gradle.gradleUserHomeDir.resolve("caches").resolve("atlas-gradle")
            cacheDir.takeIf { !it.exists() }?.mkdirs()

            versionManifest = fetchVersionManifest(
                cacheDir.resolve("version_manifest_v2.json").toPath()
            )
            println(
                PROPAGANDA.format(
                    versionManifest.latest.release,
                    versionManifest.latest.snapshot
                )
            )

            extensions.findByType(JavaPluginExtension::class.java)!! {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    maven {
                        it.name = "Jitpack"
                        it.url = uri("https://jitpack.io")
                    }
                    flatDir {
                        it.dirs(cacheDir)
                    }
                }

                atlasExtension = registerExtension()
                registerTask<RemapJar>().also {
                    tasks.getByName("assemble").dependsOn(it)
                }
                val genStubs = registerTask<GenStubs>()

                configurations {
                    fun createAndExtend(name: String, extendsFrom: String) =
                        maybeCreate(name).also { getByName(extendsFrom).extendsFrom(it) }

                    createAndExtend(LOADER_CONFIGURATION, JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
                    createAndExtend(MAPPING_CONFIGURATION, JavaPlugin.API_CONFIGURATION_NAME)
                    createAndExtend(REMAPPED_CONFIGURATION, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
                }

                sourceSets {
                    val mappingSet = create(MAPPING_SOURCESET)

                    // This is pretty shit since it's also adding the
                    // kotlin runtime to the classpath of the mappings set.
                    // This is unfortunately unavoidable to prevent annoying
                    // compiler warnings about missing annotations constants.
                    dependencies.add(
                        mappingSet.compileOnlyConfigurationName, ATLAS_ANNOTATIONS
                    )

                    (tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).apply {
                        from(mappingSet.output)
                    }

                    filter { it.name != MAPPING_SOURCESET }.forEach {
                        tasks.getByName(it.compileJavaTaskName).dependsOn(genStubs)
                    }

                    tasks.create(mappingSet.jarTaskName, Jar::class.java) {
                        it.from(mappingSet.output)
                        it.archiveClassifier.set(mappingSet.name)
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
    }

    override fun afterEvaluate() {
        with(project) {
            extensions.findByType(JavaPluginExtension::class.java)!! {
                configurations {
                    val runtimeOnly = getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)

                    if (
                        runtimeOnly.dependencies
                            .any { it.group != MOJANG_GROUP && it.name == MINECRAFT_ID }
                    ) {
                        throw IllegalStateException(
                            "The group of a Minecraft dependency must be $MOJANG_GROUP"
                        )
                    }

                    val runtimesFound = runtimeOnly.dependencies.filter { dep ->
                        dep.group == MOJANG_GROUP && dep.name == MINECRAFT_ID
                    }.onEach { runtimeOnly.dependencies.remove(it) }

                    if (runtimesFound.isEmpty()) return@configurations

                    if (runtimesFound.size > 1) {
                        throw IllegalStateException(
                            "Found multiple Minecraft runtimes in the runtimeOnly configuration: %s".format(
                                runtimesFound.joinToString { it.version.toString() }
                            )
                        )
                    }

                    val minecraftRuntime = runtimesFound.first()
                }
            }
        }
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
            return manifest
        }

        // check if current is latest
        try {
            val freshManifest = VersionManifest.fetch()
            Files.write(
                path,
                freshManifest.toString().toByteArray(),
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
            ╭──(  )──•
            │ ▶ Minecraft information:
            │ Latest version: %s
            │ Latest snapshot: %s
            │
            │ ▶ Atlas information:
            │ Atlas is currently in alpha, and as such, the API is
            │ subject to change. If you're interested in contributing,
            │ please visit https://stardust.enterprises/discord
            ╰────────•
        """.trimIndent()

        internal const val MOJANG_GROUP = "com.mojang"
        internal const val MINECRAFT_ID = "minecraft"

        internal const val MAPPING_SOURCESET = "mappings"

        internal const val LOADER_CONFIGURATION = "loader"
        internal const val MAPPING_CONFIGURATION = "mapping"
        internal const val REMAPPED_CONFIGURATION = "atlasInternalRemapped"

        internal const val ATLAS_ANNOTATIONS = "com.github.atlas-fw:annotations:a63069a7b4"
    }
}
