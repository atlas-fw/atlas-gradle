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

import enterprises.stardust.stargrad.task.StargradTask
import enterprises.stardust.stargrad.task.Task
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import kotlin.reflect.KClass

internal inline fun <reified T> Project.applyPlugin() =
    this.pluginManager.apply(T::class.java)

internal operator fun <T: Any> T?.invoke(block: T.() -> Unit) =
    this?.block()

/**
 * Wrapper around the usual task registration to make it work with [Task] when
 * the task class cannot extend [StargradTask].
 */
internal fun <T: DefaultTask> KClass<T>.createTask(
    project: Project,
    init: T.() -> Unit = {}
): T {
    val taskAnnotation = this.java.getAnnotation(Task::class.java)
    return project.tasks.create(taskAnnotation.name, this.java) {
        it.group = taskAnnotation.group
        init(it)
    }
}
