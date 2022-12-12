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

import enterprises.stardust.atlas.gradle.runtime.RuntimeConfiguration
import enterprises.stardust.atlas.gradle.stubgen.StubConfiguration
import enterprises.stardust.stargrad.ext.Extension
import enterprises.stardust.stargrad.ext.StargradExtension
import org.gradle.api.Project
import org.gradle.internal.hash.Hashing
import kotlin.random.Random

@Extension("atlas")
open class AtlasExtension(
    project: Project,
    plugin: AtlasPlugin
) : StargradExtension<AtlasPlugin>(project, plugin) {
    val runtime = RuntimeConfiguration(objects)
    val stub = StubConfiguration(objects)

    fun runtime(block: RuntimeConfiguration.() -> Unit) =
        runtime.block()

    fun stub(block: StubConfiguration.() -> Unit) =
        stub.block()

    fun api(vararg modules: String): String {
        val version = "local-" + Hashing.md5().hashBytes(Random.nextBytes(256))
        return "me.xtrm.atlas:api:$version"
    }
}
