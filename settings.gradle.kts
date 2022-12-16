rootProject.name = "atlas-gradle"

pluginManagement.repositories {
    mavenLocal()
    gradlePluginPortal()
}
include("dev-runtime")
include("mc-metadata")
