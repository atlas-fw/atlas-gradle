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

import enterprises.stardust.atlas.dev.RuntimeMain
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.zip.ZipOutputStream

internal fun extractRuntimeJar(output: Path) {
    val jar = RuntimeMain::class.java.protectionDomain.codeSource.location
    JarFile(jar.path).use { jarFile ->
        ZipOutputStream(FileOutputStream(output.toString())).use { zos ->
            jarFile.entries().toList().forEach {
                if (it.name.startsWith("enterprises/stardust/atlas/dev/")) {
                    val input = jarFile.getInputStream(it)
                    zos.putNextEntry(it)
                    input.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }
}
