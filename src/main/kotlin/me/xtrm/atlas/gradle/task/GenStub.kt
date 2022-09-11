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

package me.xtrm.atlas.gradle.task

import fr.stardustenterprises.stargrad.task.StargradTask
import fr.stardustenterprises.stargrad.task.Task
import me.xtrm.atlas.gradle.AtlasPlugin
import java.io.File.separator

@Task("genStubs", group = "atlas gradle")
open class GenStub : StargradTask() {
    private val home by lazy {
        project.gradle.gradleUserHomeDir.resolve(
            "caches" + separator + "atlas"
        ).also { it.mkdirs() }
    }

    override fun run() {
        project.configurations.getByName(AtlasPlugin.MAPPING_CONFIGURATION)
            .resolvedConfiguration
            .resolvedArtifacts
            .forEach {
                println("resolved: $it")
            }
    }
}
