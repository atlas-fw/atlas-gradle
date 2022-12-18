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

package enterprises.stardust.atlas.dev;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class Entrypoint {
    public static void main(String... args) {
        System.out.println("Atlas Framework Development Runtime");
        System.out.println("-----------------------------------");
        System.out.println("args.length = " + args.length);
        if (args.length != 0) {
            System.out.println(String.join(" ", args));
        }

        ClassLoader classLoader = Entrypoint.class.getClassLoader();
        System.out.println("Classloader: " + classLoader + " / " + classLoader.getClass().getName());
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
            System.out.println("URLs:");
            for (URL url : urlClassLoader.getURLs()) {
                System.out.println(" - " + url.toString());
            }
        }

        String mainClassName = "net.minecraft.server.MinecraftServer"; /*"net.minecraft.client.main.Main"*/
        System.out.println("Calling " + mainClassName);
        System.out.println("-----------------------------------");
        try {
            Class<?> mainClass = Class.forName(mainClassName);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            reflectiveOperationException.printStackTrace();
        }
    }
}
