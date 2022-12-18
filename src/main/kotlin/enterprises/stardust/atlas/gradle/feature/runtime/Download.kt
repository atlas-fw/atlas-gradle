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

import com.google.common.hash.Hashing
import enterprises.stardust.atlas.gradle.AtlasCache
import enterprises.stardust.stargrad.task.StargradTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import java.net.URL
import java.nio.file.Path
import javax.inject.Inject
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText

abstract class Download @Inject constructor(
    @get:Input
    val dependencyId: String,
    @get:Input
    val url: URL,
    @get:Input
    val expectedHash: String,
    @get:Input
    val currentHash: String,
) : StargradTask() {
    @get:OutputFile
    abstract val target: Property<Path>

    @get:OutputFile
    abstract val hashFile: Property<Path>

    init {
        val (parent, fileName) = AtlasCache.resolveDependencyPath(dependencyId)
        target.set(parent.resolve(fileName))
        hashFile.set(parent.resolve("$fileName.hash"))
    }

    @Suppress("DEPRECATION")
    override fun run() {
        if (hashFile.get().exists() &&
            hashFile.get().readText() == this.expectedHash) return

        target.get().also {
            if (it.exists()) {
                val hash = Hashing.sha1().hashBytes(it.readBytes()).toString()
                if (hash == this.expectedHash) return
            }

            AtlasCache.cacheFile(it.parent, it.name, url) { currentHash }
        }
    }
}
