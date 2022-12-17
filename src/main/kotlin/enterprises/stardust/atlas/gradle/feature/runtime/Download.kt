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
import enterprises.stardust.stargrad.task.StargradTask
import org.gradle.internal.hash.Hashing
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText

open class Download(
    val url: URL,
    val target: Path,
    val hash: String,
) : StargradTask() {
    override fun run() {
        val hashFile = target.parent.resolve(target.name + ".hash")
        if (hashFile.exists()) {
            val hash = hashFile.readText()
            if (hash == this.hash) {
                return
            }
        }
        if (target.exists()) {
            val currentHash =
                Hashing.sha1().hashBytes(target.readBytes()).toString()
            if (currentHash == hash) {
                return
            }
        }
        AtlasCache.cacheFile(
            target.parent,
            target.name,
            url,
        ) { hash }
    }
}
