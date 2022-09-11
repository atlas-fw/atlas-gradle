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
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension


open class AtlasPlugin : StargradPlugin() {
    override val id: String =
        "me.xtrm.atlas.gradle"
    private lateinit var atlasExtension: AtlasExtension

    override fun applyPlugin() {
        with(project) {
            applyPlugin<JavaLibraryPlugin>()

            repositories {
                mavenCentral()
                mavenLocal()
            }

            configurations {
                val api =
                    getByName(JavaPlugin.API_CONFIGURATION_NAME)
                val implementation =
                    getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)

                val loader = create(LOADER_CONFIGURATION)
                val mapping = create(MAPPING_CONFIGURATION)
                implementation.extendsFrom(loader)
                api.extendsFrom(mapping)
            }

            extensions.findByType(JavaPluginExtension::class.java)?.apply {
                sourceSets {
                    val mappingSet = create("mapping") {
                        it.java.srcDir("src/${it.name}/java")
                        it.resources.srcDir("src/${it.name}/resources")
                    }

                    forEach { set ->
                        dependencies.add(
                            set.implementationConfigurationName,
                            ATLAS_ANNOTATIONS
                        )
                    }

                    val genStubs = registerTask<GenStub>()
                    mappingSet {
                        println("stubs depend on ${this.compileJavaTaskName}")
                        genStubs.get().dependsOn(this.compileJavaTaskName)
                    }

                    filter { it.name != "mapping" }.forEach {
                        println("${it.compileJavaTaskName} depends on stubs")
                        tasks.getByName(it.compileJavaTaskName).dependsOn(genStubs)
                    }

                    afterEvaluate {
                        // task.reobf(project.tasks.getByName("jar"), object : Action<ArtifactSpec?>() {
                        //     fun execute(arg0: ArtifactSpec) {
                        //         val javaConv = project.convention.plugins["java"] as JavaPluginConvention?
                        //         arg0.setClasspath(javaConv!!.sourceSets.getByName("main").compileClasspath)
                        //     }
                        // })
                    }
                }
            }

            atlasExtension = registerExtension()

            val remapJar = registerTask<RemapJar>()
            tasks.getByName("assemble").dependsOn(remapJar)
        }
    }

    companion object {
        internal const val LOADER_CONFIGURATION = "loader"
        internal const val MAPPING_CONFIGURATION = "mapping"

        internal const val ATLAS_ANNOTATIONS =
            "me.xtrm.atlas:annotations:+"
    }
}
