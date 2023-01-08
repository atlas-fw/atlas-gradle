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

package enterprises.stardust.atlas.gradle

import org.gradle.api.Project
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.*
import kotlin.properties.Delegates

/**
 * Caching utilities.
 *
 * @author xtrm
 * @since 0.0.1
 */
object AtlasCache {
    var project: Project by Delegates.notNull()
        internal set

    val cacheDir: Path by lazy {
        project.gradle.gradleUserHomeDir.resolve("caches")
            .resolve("atlas-gradle")
            .toPath()
            .also { it.createDirectories() }
    }

    /**
     * Caches a file from a URL.
     *
     * If the file is already cached and its [hashProvider] matches with the
     * provided one, the cached file is returned. Otherwise, the file is
     * downloaded from the provided [url] and cached.
     *
     * @param storePath the folder to where the file should be cached
     * @param fileName the name of the file to cache
     * @param url the URL to download the file from
     * @param hashProvider the SHA-1 hash of the file
     *
     * @return the cached file
     */
    fun cacheFile(
        storePath: Path,
        fileName: String,
        url: URL,
        hashProvider: (Path) -> String,
    ): Path {
        val hashFile = storePath.resolve("$fileName.hash")
        val file = storePath.resolve(fileName)

        // if the supposed hash matches the cached one, return the cached file
        // without downloading it again
        if (hashFile.exists()) {
            val cachedHash = hashFile.readText()
            if (cachedHash == hashProvider(file)) {
                return file
            }
        }

        file.deleteIfExists()

        downloadURL(url, file)
        hashFile.writeText(
            hashProvider(file),
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )

        return file
    }

    /**
     * Downloads a file from a URL.
     *
     * @param url the URL to download the file from
     * @param path the path to download to
     *
     * @return the downloaded file [Path]
     */
    fun downloadURL(
        url: URL,
        path: Path,
    ): Path = path.apply {
        parent.toFile().mkdirs()
        url.openStream().use { Files.copy(it, this) }
    }

    fun resolveDependencyPath(
        dependencyNotation: String,
    ): Pair<Path, String> {
        val data = dependencyNotation.split(":")
        if (data.size < 3 || data.size > 5) {
            throw IllegalArgumentException(
                "Invalid dependency: $dependencyNotation"
            )
        }
        return resolveDependencyPath(
            data[0],
            data[1],
            data[2],
            data.getOrNull(3),
            data.getOrNull(4) ?: "jar"
        )
    }

    fun resolveDependencyPath(
        group: String,
        name: String,
        version: String,
        classifier: String? = null,
        extension: String = "jar",
    ): Pair<Path, String> {
        val folder = cacheDir.resolve(group.replace(".", File.separator))
            .resolve(name)
            .resolve(version)
        val artifactName = "$name-$version" +
            classifier?.let { "-$it" }.orEmpty() +
            ".$extension"
        return folder to artifactName
    }
}
