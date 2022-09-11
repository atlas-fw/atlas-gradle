object Coordinates {
    const val NAME = "atlas-gradle"
    const val DESC = "The Gradle plugin in use for development of Atlas Framework and Atlas Loader mods."
    const val VENDOR = "xtrm"

    const val GIT_HOST = "github.com"
    const val REPO_ID = "atlas-fw/$NAME"

    const val GROUP = "me.xtrm.atlas"
    const val VERSION = "0.0.1"
}

object Pom {
    val licenses = arrayOf(
        License("GNU LGPLv3 License", "https://spdx.org/licenses/LGPL-3.0-or-later.html")
    )
    val developers = arrayOf(
        Developer("xtrm")
    )
}

data class License(val name: String, val url: String, val distribution: String = "repo")
data class Developer(val id: String, val name: String = id)
