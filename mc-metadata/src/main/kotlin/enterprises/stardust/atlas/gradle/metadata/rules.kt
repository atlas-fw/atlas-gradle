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

import com.fasterxml.jackson.annotation.JsonProperty

interface RuleContext {
    fun matchesOs(osInfo: OSInfo): Boolean
    fun hasFeature(name: String): Boolean

    companion object
}

/**
 * @author xtrm
 * @since 0.0.1
 */
enum class RuleAction(
    val value: Boolean,
) {
    @JsonProperty("allow")
    ALLOW(true),

    @JsonProperty("disallow")
    DISALLOW(false),
}

/**
 * @author lambdagg
 * @since 0.0.1
 */
abstract class Ruleable {
    abstract val rules: List<Rule>

    fun rulesApply(ctx: RuleContext): Boolean {
        if (rules.isEmpty()) return true

        var allows = false
        for (rule in rules) {
            if (!rule.shouldApply(ctx)) return false
            allows = true
        }
        return allows
    }
}

/**
 * We either have set `name` and `version` values OR a set `arch` value.
 *
 * @author xtrm
 * @since 0.0.1
 */
data class OSInfo(
    val name: String?,
    val version: String?,
    val arch: String?,
)

/**
 * @author xtrm
 * @author lambdagg
 * @since 0.0.1
 */
data class Rule(
    val action: RuleAction,
    val features: Map<String, Boolean>?,
    val os: OSInfo?,
) {
    fun shouldApply(ctx: RuleContext): Boolean {
        if (os != null && !ctx.matchesOs(os)) {
            return !action.value
        }

        if (features != null) {
            for ((name, value) in features) {
                if (ctx.hasFeature(name) != value) {
                    return !action.value
                }
            }
        }

        return action.value
    }
}
