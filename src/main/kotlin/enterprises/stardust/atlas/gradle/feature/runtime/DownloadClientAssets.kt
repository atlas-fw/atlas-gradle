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

import enterprises.stardust.atlas.gradle.AtlasCache
import enterprises.stardust.atlas.gradle.metadata.Artifact
import enterprises.stardust.atlas.gradle.metadata.LoggingFileType
import enterprises.stardust.stargrad.task.StargradTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import java.nio.file.Path
import javax.inject.Inject

/**
 * Downloads the assets along with the logging-related files
 *
 * @author lambdagg
 */
abstract class DownloadClientAssets @Inject constructor(
    @get:Input
    val assetIndexId: String,
    @get:Internal
    val loggingFileType: LoggingFileType?,
    @get:Internal
    val loggingArtifact: Artifact?,
) : StargradTask() {
    @get:OutputFile
    var loggingFile: Path? = loggingArtifact?.let {
        AtlasCache.cacheDir.resolve("logging").resolve(it.id!!)
    }
        private set

    // TODO assets

    override fun run() {
        // Logging-related stuff

        if (loggingArtifact != null && loggingFileType == null) {
            throw IllegalArgumentException(
                "loggingFileType cannot be null if loggingFile is defined"
            )
        }

        loggingFileType?.also {
            if (loggingArtifact == null) {
                throw IllegalArgumentException(
                    "loggingFile cannot be null if loggingFileType is defined"
                )
            }

            if (it != LoggingFileType.LOG4J2_XML) {
                println("Warning: Unknown logging file type")
                return@also
            }

            loggingFile = AtlasCache.cacheFile(
                AtlasCache.cacheDir.resolve("logging"),
                loggingArtifact.id!!,
                loggingArtifact.url,
            ) { loggingArtifact.sha1 }
        }
    }
}
