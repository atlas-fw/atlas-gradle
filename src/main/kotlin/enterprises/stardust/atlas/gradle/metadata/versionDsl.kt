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

package enterprises.stardust.atlas.gradle.metadata

import com.google.common.hash.Hashing
import enterprises.stardust.atlas.gradle.AtlasCache
import enterprises.stardust.atlas.gradle.objectMapper
import java.net.URL
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText

internal val manifestUrl: URL by lazy { URL(MANIFEST_URL) }

fun VersionManifest.Companion.fetch(): VersionManifest =
    objectMapper.readValue(manifestUrl, VersionManifest::class.java)

fun VersionManifest.Companion.from(json: String): VersionManifest =
    objectMapper.readValue(json, VersionManifest::class.java)

fun VersionManifest.json(): String = objectMapper.writeValueAsString(this)

@Suppress("DEPRECATION") // https://youtu.be/caluozb2s1c?t=9
fun VersionJson.Companion.fetch(url: URL, hash: String? = null): VersionJson {
    val path = AtlasCache.cacheDir.resolve("versions")
        .also { it.createDirectories() }
    val fileName = url.path.substringAfterLast("/")

    val file = AtlasCache.cacheFile(path, fileName, url) {
        hash ?: if (!it.exists()) {
            // if the file doesn't exist, return a random string
            // to force the download
            UUID.randomUUID().toString()
        } else {
            // if the file exists, return the hash of the file
            Hashing.sha1().hashBytes(it.readBytes()).toString()
        }
    }

    return VersionJson.from(file.readText())
}

fun VersionJson.Companion.from(json: String): VersionJson =
    objectMapper.readValue(json, VersionJson::class.java)

fun VersionJson.json(): String = objectMapper.writeValueAsString(this)
