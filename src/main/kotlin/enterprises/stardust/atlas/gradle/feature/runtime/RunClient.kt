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

package enterprises.stardust.atlas.gradle.feature.runtime

import enterprises.stardust.stargrad.task.Task
import org.gradle.api.tasks.JavaExec

@Task("runClient", group = "atlas gradle")
open class RunClient : JavaExec() {
    init {
        group = "atlas gradle"
        mainClass.set("enterprises.stardust.atlas.dev.runtime.RuntimeLauncher")
        classpath = project.configurations.getByName("runtime")
        classpath += project.configurations.getByName("runtimeClasspath")
        workingDir = project.file("run").also { it.mkdirs() }
        args = listOf(
            "--gameDir", workingDir.absolutePath,
            "--assetsDir", project.file("assets").absolutePath,
            "--assetIndex", "1.16",
            "--accessToken", "atlas-gradle",
            "--version", "1.16.5",
            "--userProperties", "{}",
            "--username", "atlas-gradle",
        )
    }
}
