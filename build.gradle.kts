import plugins.ShadowJar
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

plugins {
    with(Plugins) {
        // Language Plugins
        id("org.gradle.kotlin.kotlin-dsl") version "4.0.0-rc-2"

        // Git Repo Information
        id("org.ajoberstar.grgit") version GRGIT

        // Token Replacement
        id("net.kyori.blossom") version BLOSSOM

        // Dependency Shading
        id("com.github.johnrengelman.shadow") version SHADOW

        // Code Quality
        id("org.jlleitschuh.gradle.ktlint") version KTLINT

        // Documentation Generation
        id("org.jetbrains.dokka") version DOKKA

        // Gradle Plugin Portal Publication
        id("com.gradle.plugin-publish") version GRADLE_PLUGIN_PUBLISH
        `java-gradle-plugin`
        `maven-publish`
        signing
    }
}

// What JVM version should this project compile to
val targetVersion = "1.8"
// What JVM version this project is written in
val sourceVersion = "1.8"

// Add `include` configuration for ShadowJar
configurations {
    val include by creating
    // don't include in maven pom
    compileOnly.get().extendsFrom(include)
    // but also work in tests
    testImplementation.get().extendsFrom(include)

    // Makes all the configurations use the same Kotlin version.
    all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(Dependencies.KOTLIN)
            }
        }
    }
}

// Project Dependencies
dependencies {
    val include by configurations

    with(Dependencies) {
        compileOnly(gradleApi())

        include(project(":dev-runtime"))
        include(project(":mc-metadata"))

        kotlinModules.forEach {
            implementation("org.jetbrains.kotlin", "kotlin-$it", KOTLIN)
        }

        implementation("com.google.guava", "guava", GUAVA)
        implementation("com.fasterxml.jackson.core", "jackson-databind", JACKSON_DATABIND)
        implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", JACKSON_KOTLIN)

        implementation("enterprises.stardust", "stargrad", STARGRAD)
        implementation("fr.stardustenterprises", "plat4k", PLAT4K)

        include("com.github.atlas-fw", "annotations", "a63069a7b4")

        testImplementation("org.jetbrains.kotlin", "kotlin-test", KOTLIN)
    }
}

// Maven Repositories
repositories {
    mavenLocal()
    mavenCentral()

    Repositories.mavenUrls.forEach(::maven)
}

group = Coordinates.GROUP
version = Coordinates.VERSION

// The latest commit ID
val buildRevision: String = grgit.log()[0].id ?: "dev"

// Disable unneeded rules
ktlint {
    this.disabledRules.addAll(
        "no-wildcard-imports",
        "filename"
    )
}

blossom {
    mapOf(
        "project.name" to Coordinates.NAME,
        "project.version" to Coordinates.VERSION,
        "project.desc" to Coordinates.DESC,
        "project.rev" to buildRevision,
    ).mapKeys { "@${it.key}@" }.forEach { (key, value) ->
        replaceToken(key, value)
    }
}

allprojects {
    apply(plugin = "java-library")
    if (project.name != "dev-runtime") {
        apply(plugin = "org.jetbrains.kotlin.jvm")
    }

    tasks {
        // Configure JVM versions
        if (project.name != "dev-runtime") {
            compileKotlin {
                kotlinOptions {
                    languageVersion = "1.7"
                    jvmTarget = targetVersion
                    freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
                }
            }
        }

        compileJava {
            targetCompatibility = targetVersion
            sourceCompatibility = sourceVersion
        }
    }
}

subprojects {
    group = Coordinates.GROUP
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    dokkaHtml {
        val moduleFile = File(projectDir, "MODULE.temp.md")

        run {
            // In order to have a description on the rendered docs, we have to have
            // a file with the # Module thingy in it. That's what we're
            // automagically creating here.

            doFirst {
                moduleFile.writeText("# Module ${Coordinates.NAME}\n${Coordinates.DESC}")
            }

            doLast {
                moduleFile.delete()
            }
        }

        moduleName.set(Coordinates.NAME)

        dokkaSourceSets.configureEach {
            displayName.set("${Coordinates.NAME} on ${Coordinates.GIT_HOST}")
            includes.from(moduleFile.path)

            skipDeprecated.set(false)
            includeNonPublic.set(false)
            skipEmptyPackages.set(true)
            reportUndocumented.set(true)
            suppressObviousFunctions.set(true)

            // Link the source to the documentation
            sourceLink {
                localDirectory.set(file("src"))
                remoteUrl.set(URL("https://${Coordinates.GIT_HOST}/${Coordinates.REPO_ID}/tree/trunk/src"))
            }

            // External documentation link template
//            externalDocumentationLink {
//                url.set(URL("https://javadoc.io/doc/net.java.dev.jna/jna/5.10.0/"))
//            }
        }
    }

    // The original artifact, we just have to add the API source output and the
    // LICENSE file.
    jar {
        fun normalizeVersion(versionLiteral: String): String {
            val regex = Regex("(\\d+\\.\\d+\\.\\d+).*")
            val match = regex.matchEntire(versionLiteral)
            require(match != null) {
                "Version '$versionLiteral' does not match version pattern, e.g. 1.0.0-QUALIFIER"
            }
            return match.groupValues[1]
        }

        val buildTimeAndDate = OffsetDateTime.now()
        val buildDate = DateTimeFormatter.ISO_LOCAL_DATE.format(buildTimeAndDate)
        val buildTime = DateTimeFormatter.ofPattern("HH:mm:ss.SSSZ").format(buildTimeAndDate)

        val javaVersion = System.getProperty("java.version")
        val javaVendor = System.getProperty("java.vendor")
        val javaVmVersion = System.getProperty("java.vm.version")

        with(Coordinates) {
            mapOf(
                "Created-By" to "$javaVersion ($javaVendor $javaVmVersion)",
                "Build-Date" to buildDate,
                "Build-Time" to buildTime,
                "Build-Revision" to buildRevision,

                "Specification-Title" to project.name,
                "Specification-Version" to normalizeVersion(project.version.toString()),
                "Specification-Vendor" to VENDOR,

                "Implementation-Title" to NAME,
                "Implementation-Version" to VERSION,
                "Implementation-Vendor" to VENDOR,

                "Bundle-Name" to NAME,
                "Bundle-Description" to DESC,
                "Bundle-DocURL" to "https://$GIT_HOST/$REPO_ID",
                "Bundle-Vendor" to VENDOR,
                "Bundle-SymbolicName" to "$GROUP.$NAME"
            ).forEach { (k, v) ->
                manifest.attributes[k] = v
            }
        }
        from("LICENSE")
    }

    // Source artifact, including everything the 'main' does but not compiled.
    create("sourcesJar", Jar::class) {
        group = "build"

        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)

        this.manifest.from(jar.get().manifest)

        from("LICENSE")
    }

    // The Javadoc artifact, containing the Dokka output and the LICENSE file.
    create("javadocJar", Jar::class) {
        group = "build"

        val dokkaHtml = getByName("dokkaHtml")

        archiveClassifier.set("javadoc")
        dependsOn(dokkaHtml)
        from(dokkaHtml)

        from("LICENSE")
    }

    // Configure ShadowJar
    shadowJar {
        val include by project.configurations

        this.configurations.clear()
        this.configurations += include

        from("LICENSE")

        this.archiveClassifier.set(ShadowJar.classifier)
        this.manifest.inheritFrom(jar.get().manifest)

        ShadowJar.packageRemappings.forEach(this::relocate)
    }
}

// Define the default artifacts' tasks
val defaultArtifactTasks = arrayOf(
    tasks["sourcesJar"],
//    tasks["javadocJar"],
)

// Declare the artifacts
artifacts {
    defaultArtifactTasks.forEach(::archives)
    archives(tasks.shadowJar)
}

gradlePlugin {
    plugins {
        create("default") {
            displayName = "Atlas Gradle"
            description = ""
            id = "enterprises.stardust.atlas.gradle"
            implementationClass = "enterprises.stardust.atlas.gradle.AtlasPlugin"
        }
    }
}

pluginBundle {
    vcsUrl = "https://github.com/${Coordinates.REPO_ID}"
    website = "https://github.com/${Coordinates.REPO_ID}"
    tags = listOf("atlas-framework")
}

publishing.publications {
    // Sets up the Maven integration.
    create("mavenJava", MavenPublication::class.java) {
        from(components["java"])
        defaultArtifactTasks.forEach(::artifact)

        with(Coordinates) {
            pom {
                name.set(NAME)
                description.set(DESC)
                url.set("https://$GIT_HOST/$REPO_ID")

                with(Pom) {
                    licenses {
                        licenses.forEach {
                            license {
                                name.set(it.name)
                                url.set(it.url)
                                distribution.set(it.distribution)
                            }
                        }
                    }

                    developers {
                        developers.forEach {
                            developer {
                                id.set(it.id)
                                name.set(it.name)
                            }
                        }
                    }
                }

                scm {
                    connection.set("scm:git:git://$GIT_HOST/$REPO_ID.git")
                    developerConnection.set("scm:git:ssh://$GIT_HOST/$REPO_ID.git")
                    url.set("https://$GIT_HOST/$REPO_ID")
                }
            }
        }

        // Configure the signing extension to sign this Maven artifact.
        signing {
            isRequired = project.properties["signing.keyId"] != null
            sign(this@create)
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${Coordinates.REPO_ID}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
