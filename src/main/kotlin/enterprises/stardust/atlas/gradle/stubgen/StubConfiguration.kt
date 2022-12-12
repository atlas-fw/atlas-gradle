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

package enterprises.stardust.atlas.gradle.stubgen

import org.gradle.api.model.ObjectFactory

class StubConfiguration(@Suppress("UNUSED_PARAMETER") objects: ObjectFactory) {
    /**
     * Mapped names overrides, used by the stub-engine
     * when generating stub classes.
     *
     * You can use this variable to force a certain mapping class
     * to have a particular stub name.
     *
     * Example:
     * ```kt
     * atlas {
     *     stub {
     *         val className = "me.xtrm.atlas.api.client.MinecraftClient"
     *
     *         // Something like this would force the `MinecraftClient` mapping class
     *         // to generate a stub class named `net.minecraft.src.Minecraft`, regardless
     *         // of the mapping class' provided names.
     *         overrides += mapOf(
     *             className to "net.minecraft.src.Minecraft"
     *         )
     *
     *         // Likewise, this also works for class members.
     *         overrides += mapOf(
     *             "$className.instance" to "INSTANCE"
     *             "$className.runTask(Ljava/lang/Runnable;)V" to "scheduleTask"
     *         )
     *     }
     * }
     * ```
     */
    val overrides: MutableMap<String, String> = mutableMapOf()
}
