plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kraft.publish) apply true
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
                    onlyIf { publication.name == "jvm" }
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
