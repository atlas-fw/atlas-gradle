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

package enterprises.stardust.atlas.gradle.data

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.hash.Hashing
import enterprises.stardust.atlas.gradle.AtlasCache
import enterprises.stardust.atlas.gradle.objectMapper
import java.net.URL
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * A version json metadata.
 *
 * @author xtrm
 * @since 0.0.1
 */
data class VersionJson(
    val arguments: Arguments?,
    val assetIndex: Artifact,
    val assets: String,
    val complianceLevel: Int,
    val downloads: Downloads,
    val id: String,
    val javaVersion: JavaVersion,
    val libraries: List<Library>,
    val logging: Logging?,
    val mainClass: String,
    val minecraftArguments: String?,
    val minimumLauncherVersion: Int,
    val releaseTime: String,
    val time: String,
    val type: String
) {
    val gameArguments: List<*>
        get() = arguments?.game ?: minecraftArguments!!.split(" ")
    val jvmArguments: List<*>
        get() = arguments?.jvm ?: emptyList<String>() //TODO: Add JVM arguments

    companion object {
        @JvmStatic
        fun fetch(url: URL, hash: String? = null): VersionJson {
            val path = AtlasCache.cacheDir.resolve("versions")
                .also { it.createDirectories() }
                .resolve(
                    url.path.substringAfterLast("/")
                        .substringBefore(".json")
                )

            val file = AtlasCache.cacheFile(
                path,
                "version.json",
                url
            ) {
                if (hash != null) return@cacheFile hash
                if (!it.exists()) {
                    // if the file doesn't exist, return a random string to
                    // force the download
                    UUID.randomUUID().toString()
                } else {
                    // if the file exists, return the hash of the file
                    @Suppress("DEPRECATION")
                    Hashing.sha1().hashBytes(it.readBytes()).toString()
                }
            }

            return from(file.readText())
        }

        @JvmStatic
        fun from(json: String): VersionJson =
            objectMapper.readValue(
                json,
                VersionJson::class.java
            )
    }
}

data class Arguments(
    // todo
    val game: List<*>,
    val jvm: List<*>
)

data class Artifact(
    val id: String?,
    val path: String?,
    val sha1: String,
    val totalSize: Long?,
    val size: Long,
    val url: URL,
)

data class Downloads(
    val client: Artifact,
    @JsonProperty("client_mappings") val clientMappings: Artifact?,
    val server: Artifact?,
    @JsonProperty("server_mappings") val serverMappings: Artifact?,
    @JsonProperty("windows_server") val windowsServer: Artifact?,
)

data class JavaVersion(
    val component: String,
    val majorVersion: Int,
)

data class Library(
    val downloads: LibraryDownloads,
    val name: String,
    val natives: Map<String, String>?,
    val rules: List<Rule>?,
    val extract: Extract?,
)

data class LibraryDownloads(
    val artifact: Artifact?,
    val classifiers: Map<String, Artifact>?,
)

data class Rule(
    val action: RuleAction,
    val features: Map<String, Boolean>?,
    val os: OSInfo?,
)

data class OSInfo(
    val arch: String?,
    val name: String?,
    val version: String?,
)

enum class RuleAction {
    @JsonProperty("allow")
    ALLOW,
    @JsonProperty("disallow")
    DISALLOW,
}

data class Extract(
    val exclude: List<String>,
)

data class Logging(
    val client: LoggingInfo,
)

data class LoggingInfo(
    val argument: String,
    val file: Artifact,
    val type: String,
)
