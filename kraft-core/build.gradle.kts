plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.kraft.publish) apply true
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            optIn.add("com.blu3berry.kraft.ExperimentalKraftApi")
        }
    }
    jvmToolchain(17)

    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":kraft-annotations"))
                api(libs.ksp.api)
            }
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name != "jvm") {
                tasks.withType<PublishToMavenRepository>().configureEach {
                    // Capture at configuration time: reading task.publication
                    // inside onlyIf breaks under the configuration cache.
                    val publicationName = publication.name
                    onlyIf { publicationName == "jvm" }
                }
            }
            artifactId = "kraft-core"

            pom {
                name.set("kraft-core")
                description.set("Core processor model and SPI for the Kraft mapper generator")
            }
        }
    }
}
