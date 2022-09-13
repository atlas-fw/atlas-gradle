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

package me.xtrm.atlas.gradle

import fr.stardustenterprises.stargrad.StargradPlugin
import me.xtrm.atlas.gradle.ext.AtlasExtension
import me.xtrm.atlas.gradle.task.GenStub
import me.xtrm.atlas.gradle.task.RemapJar
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar


open class AtlasPlugin : StargradPlugin() {
    override val id: String =
        "me.xtrm.atlas.gradle"
    private lateinit var atlasExtension: AtlasExtension

    override fun applyPlugin() {
        with(project) {
            applyPlugin<JavaLibraryPlugin>()

            extensions.findByType(JavaPluginExtension::class.java)?.apply {
                repositories {
                    mavenCentral()
                    mavenLocal()
                }

                atlasExtension = registerExtension()
                registerTask<RemapJar>().also {
                    tasks.getByName("assemble").dependsOn(it)
                }

                val genStubs = registerTask<GenStub>()
                sourceSets.filter { it.name != MAPPING_SOURCESET }.forEach {
                    tasks.getByName(it.compileJavaTaskName).dependsOn(genStubs)
                }

                configurations {
                    fun createAndExtend(name: String, extendsFrom: String) =
                        create(name).also { getByName(extendsFrom).extendsFrom(it) }

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
                        mappingSet.compileOnlyConfigurationName,
                        ATLAS_ANNOTATIONS
                    )

                    (tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).apply {
                        from(mappingSet.output)
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
            }
        }
    }

    companion object {
        internal const val MAPPING_SOURCESET = "mappings"

        internal const val LOADER_CONFIGURATION = "loader"
        internal const val MAPPING_CONFIGURATION = "mapping"
        internal const val REMAPPED_CONFIGURATION = "atlasInternalRemapped"

        internal const val ATLAS_ANNOTATIONS =
            "me.xtrm.atlas:annotations:0.0.1"
    }
}
