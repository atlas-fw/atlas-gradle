group = "enterprises.stardust"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    with(Dependencies) {
        implementation("com.fasterxml.jackson.core", "jackson-annotations", JACKSON_ANNOTATIONS)
    }
}
