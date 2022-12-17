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

package enterprises.stardust.atlas.gradle.feature.stubgen

import enterprises.stardust.atlas.gradle.AtlasPlugin
import enterprises.stardust.stargrad.task.StargradTask
import enterprises.stardust.stargrad.task.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.io.File
import java.util.zip.ZipFile

@Task("genStubs", group = "atlas gradle")
open class GenStubs : StargradTask() {
    // TODO: change input to mapping metadata declarations directly
    //  since a mapping jar can have no mapping classes and would trigger
    //  useless processing
    @get:InputFiles
    lateinit var sourceJars: FileCollection
        private set

    @get:OutputFile
    lateinit var outputFile: File

    internal fun init() {
        val sourceMappingJars = mutableListOf<File>()
        with(project) {
            val mappingsJar = tasks.findByName(AtlasPlugin.FACADES_SOURCESET + "Jar") as? AbstractArchiveTask
            mappingsJar?.archiveFile?.orNull?.asFile
                ?.also(sourceMappingJars::add)

            val config = project.configurations.getByName(AtlasPlugin.FACADE_CONFIGURATION)
            config.resolve().forEach(sourceMappingJars::add)
        }
        this.sourceJars = project.files(*sourceMappingJars.toTypedArray())
        this.outputFile = project.buildDir.resolve("atlas")
            .resolve("cache")
            .also { it.mkdirs() }
            .resolve("atlas-stub.jar")
    }

    override fun run() {
        if (sourceJars.isEmpty) {
            return
        }
        println("Scanning ${sourceJars.files.size} mapping jars...")
        sourceJars.map { it.absoluteFile }.forEach { println(it) }

        sourceJars.forEach { file ->
            val zip = ZipFile(file)
            zip.entries().iterator().forEach { entry ->
                if (entry.name.endsWith(".mappings.json")) {
                    val stream = zip.getInputStream(entry)
                    val jsonText = stream.bufferedReader().readLines()

                    stream.close()
                }
            }
        }
    }
}
