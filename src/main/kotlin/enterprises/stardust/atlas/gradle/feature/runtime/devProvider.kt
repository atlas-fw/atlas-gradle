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

import enterprises.stardust.atlas.dev.Entrypoint
import enterprises.stardust.atlas.gradle.AtlasCache
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

internal val runtimeJar: Path by lazy {
    val output = AtlasCache.cacheDir.resolve("dev-runtime.jar")
    if (output.exists() && !output.isDirectory()) {
        output.deleteIfExists()
    }

    val jar = Entrypoint::class.java.protectionDomain.codeSource.location
    JarFile(jar.path).use { jarFile ->
        ZipOutputStream(FileOutputStream(output.toString())).use { stream ->
            stream.setComment("Generated by atlas-gradle")
            stream.setLevel(9)
            jarFile.entries().toList()
                .filter {
                    it.name.startsWith("enterprises/stardust/atlas/dev") &&
                        !it.isDirectory
                }
                .forEach {
                    val input = jarFile.getInputStream(it)
                    stream.putNextEntry(ZipEntry(it.name))
                    stream.write(input.readBytes())
                    stream.closeEntry()
                }
        }
    }
    output
}
