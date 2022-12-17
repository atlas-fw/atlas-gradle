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

import java.net.URL

/**
 * The current Version Manifest URL.
 */
internal const val MANIFEST_URL =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

/**
 * Representation of the Minecraft launcher's version manifest.
 *
 * @author xtrm
 * @since 0.0.1
 */
data class VersionManifest(
    val latest: LatestMetadata,
    val versions: List<VersionMetadata>
) {
    companion object {
        private val url: URL = URL(MANIFEST_URL)

        @JvmStatic
        fun fetch(): VersionManifest =
            objectMapper.readValue(
                url,
                VersionManifest::class.java
            )

        @JvmStatic
        fun parse(json: String): VersionManifest =
            objectMapper.readValue(
                json,
                VersionManifest::class.java
            )
    }
}

/**
 * Representation of the latest version metadata.
 *
 * @author xtrm
 * @since 0.0.1
 */
data class LatestMetadata(
    val release: String,
    val snapshot: String
)

/**
 * Representation of a Minecraft version metadata.
 *
 * @author xtrm
 * @since 0.0.1
 */
data class VersionMetadata(
    val id: String,
    val type: String,
    val url: URL,
    val time: String,
    val releaseTime: String,
    val sha1: String,
    val complianceLevel: Int,
)
