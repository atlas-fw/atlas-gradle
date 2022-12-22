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
import fr.stardustenterprises.plat4k.EnumArchitecture
import fr.stardustenterprises.plat4k.EnumOperatingSystem
import fr.stardustenterprises.plat4k.Platform
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path
import javax.inject.Inject

abstract class ExtractClientNatives @Inject constructor(
    private val libraries: List<Library>,
) : DefaultTask() {
    @get:InputFiles
    val inputFiles: ConfigurableFileCollection

    @Internal
    private val ctx = RuleContext.withCurrentPlatform()

    init {
        val config = project.configurations.getByName(
            AtlasPlugin.ATLAS_CLIENT_RUNTIME
        ).resolve()
        val (os, arch) = Platform.currentPlatform.let {
            it.operatingSystem to it.architecture
        }

        val files = mutableListOf<Path>()
        libraries.filter { it.rulesApply(ctx) }.forEach { library ->
            if (library.extract == null || library.natives.isNullOrEmpty()) {
                return@forEach
            }
            val targetClassifier = findClassifier(library, os, arch)
                ?: return@forEach

            val artifact = library.downloads.classifiers?.get(targetClassifier)
                ?: return@forEach

            val file = config.single {
                it.name == artifact.path!!.substringAfterLast('/')
            }
            files.add(file.toPath())
        }
        inputFiles = project.files(*files.toTypedArray())
    }

    @TaskAction
    fun run() {
        val (os, arch) = Platform.currentPlatform.let {
            it.operatingSystem to it.architecture
        }
        libraries.filter { it.rulesApply(ctx) }.forEach { library ->
            if (library.extract == null || library.natives.isNullOrEmpty()) {
                return@forEach
            }
            val targetClassifier = findClassifier(library, os, arch)
                ?: return@forEach

            val artifact = library.downloads.classifiers?.get(targetClassifier)
                ?: return@forEach

            if (inputFiles.any { it.name == artifact.path.substringAfterLast('/') })

        }
    }

    private fun findClassifier(
        library: Library,
        os: EnumOperatingSystem,
        arch: EnumArchitecture,
    ): String? =
        library.natives!!.let { natives ->
            natives[os.name.lowercase()]
                ?: os.aliases.mapNotNull { os ->
                    arch.aliases.mapNotNull { arch ->
                        natives["$os-$arch"].takeIf { !it.isNullOrBlank() }
                    }.takeIf { it.isNotEmpty() }?.first()
                }.takeIf { it.isNotEmpty() }?.first()
                ?: natives["default"]
        }

}

