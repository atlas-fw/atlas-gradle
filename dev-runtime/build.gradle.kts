plugins {
    `java-library`
    application
}

group = "enterprises.stardust.atlas"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
