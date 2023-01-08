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

import fr.stardustenterprises.plat4k.Platform

class RuleContextImpl(
    private val platform: Platform,
    private val flags: Set<String>,
) : RuleContext {
    override fun matchesOs(osInfo: OSInfo): Boolean {
        val os = platform.operatingSystem
        val arch = platform.architecture
        val osVersion = System.getProperty("os.version", "unknown")

        fun matchesSingular(osName: String, osArch: String): Boolean {
            fun unequalsNotNull(val1: String?, val2: String) =
                val1 != null && !val2.equals(val1, ignoreCase = true)

            return !(
                unequalsNotNull(osInfo.name, osName) ||
                    unequalsNotNull(osInfo.version, osVersion) ||
                    unequalsNotNull(osInfo.arch, osArch)
                )
        }

        for (osName in os.aliases) {
            for (archName in arch.aliases) {
                if (matchesSingular(osName, archName)) return true
            }
        }
        return false
    }

    override fun hasFeature(name: String): Boolean = this.flags.contains(name)
}

fun RuleContext.Companion.withCurrentPlatform(
    flags: Set<String>,
): RuleContext = Platform.currentPlatform.run {
    RuleContextImpl(
        this,
        flags,
    )
}

private val currentPlatformNoFlags by lazy {
    RuleContext.withCurrentPlatform(emptySet())
}

fun RuleContext.Companion.withCurrentPlatform(): RuleContext =
    currentPlatformNoFlags
