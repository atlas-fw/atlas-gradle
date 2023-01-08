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

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URL
import java.util.*


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
    val type: String,
) {
    val gameArguments: List<*>
        get() = arguments?.game ?: minecraftArguments!!.split(" ")
    val jvmArguments: List<*>
        get() = arguments?.jvm ?: emptyList<String>() //TODO: Add JVM arguments

    companion object
}

data class Arguments(
    // todo
    val game: List<*>,
    val jvm: List<*>,
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
    override val rules: List<Rule> = emptyList(),
    val extract: Extract?,
) : Ruleable()

data class LibraryDownloads(
    val artifact: Artifact?,
    val classifiers: Map<String, Artifact>?,
)

data class Extract(
    val exclude: List<String>,
)

data class Logging(
    val client: LoggingInfo?,
)

data class LoggingInfo(
    val argument: String,
    val file: Artifact,
    val type: LoggingFileType,
)

enum class LoggingFileType {
    @JsonProperty("log4j2-xml") LOG4J2_XML,
    @JsonEnumDefaultValue UNKNOWN
}
