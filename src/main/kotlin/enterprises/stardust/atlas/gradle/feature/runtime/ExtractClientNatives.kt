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

package enterprises.stardust.atlas.gradle.feature.runtime

import enterprises.stardust.atlas.gradle.AtlasPlugin
import enterprises.stardust.atlas.gradle.metadata.Library
import enterprises.stardust.atlas.gradle.metadata.RuleContext
import enterprises.stardust.atlas.gradle.metadata.withCurrentPlatform
import fr.stardustenterprises.plat4k.Platform
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ExtractClientNatives @Inject constructor(
    libraries: List<Library>,
) : DefaultTask() {

    @get:InputFiles
    val inputFiles: FileCollection

    init {
        val ctx = RuleContext.withCurrentPlatform()
        val os = Platform.currentPlatform.operatingSystem
        val config = project.configurations.getByName(
            AtlasPlugin.ATLAS_CLIENT_RUNTIME
        ).resolve()

        var inputs: FileCollection = project.files()
        libraries.filter { it.rulesApply(ctx) }.forEach { library ->
            if (library.natives.isNullOrEmpty()) {
                return@forEach
            }

            println("Extracting natives for ${library.name}")

            /*config.forEach { file ->
                library.downloads.classifiers
                    ?.filter { it.key.startsWith("natives-") }
                    ?.filter { it.key.endsWith(os.name.lowercase()) }
                    ?.forEach files@{ (_, download) ->
                        val fileName = download.path!!.substringAfterLast("/")
                        if (file.name == fileName) {
                            println("==>>>> Found ${file.path} for ${library.name}")
                            inputs += project.files(file)
                            return@files
                        }
                    }
            }*/
        }
        inputFiles = inputs
    }

    @TaskAction
    fun run() {
        val os = Platform.currentPlatform.operatingSystem
        inputFiles.files.forEach {
            println(" - $it")
        }
    }
}
