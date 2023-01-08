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

import enterprises.stardust.atlas.gradle.metadata.Library
import fr.stardustenterprises.plat4k.EnumArchitecture
import fr.stardustenterprises.plat4k.EnumOperatingSystem

internal fun findClassifier(
    library: Library,
    os: EnumOperatingSystem,
    arch: EnumArchitecture,
): String? = run {
    val natives = library.natives!!

    for (osName in os.aliases) {
        for (archName in arch.aliases) {
            val potentialTarget = "$osName-$archName"
            if (natives[potentialTarget] != null) {
                return@run natives[potentialTarget]
            }
        }

        if (natives[osName] != null) {
            return@run natives[osName]
        }
    }

    natives["default"]
}?.replace("\${arch}", arch.cpuType.bits.toString())

//    library.natives!!.let { natives ->
//        natives[os.name.lowercase()]
//            ?: os.aliases.mapNotNull { os ->
//                arch.aliases.mapNotNull { arch ->
//                    natives["$os-$arch"].takeIf { !it.isNullOrBlank() }
//                }.takeIf { it.isNotEmpty() }?.first()
//            }.takeIf { it.isNotEmpty() }?.first()
//            ?: natives["default"]
//    }
