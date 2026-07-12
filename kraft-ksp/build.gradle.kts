import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kraft.publish) apply true
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            optIn.add("com.blu3berry.kraft.ExperimentalKraftApi")
        }
    }
    jvmToolchain(17)

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":kraft-core"))

                // KotlinPoet for code generation
                implementation(libs.kotlinpoet)
                implementation(libs.kotlinpoet.ksp)
            }
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.compile.testing)
                implementation(libs.kotlin.compile.testing.ksp)

                implementation(libs.junit.jupiter)
                implementation(libs.truth)

                // You usually also need your own KSP module to load the processor:
                implementation(project(":kraft-annotations"))
            }
        }

    }

}

ksp {
    arg("kraft.functionNameFormat", "to${'$'}{target}From${'$'}{source}")
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name != "jvm") {
                tasks.withType<PublishToMavenRepository>().configureEach {
                    onlyIf { publication.name == "jvm" }
                }
            }
            artifactId = "kraft-ksp"

            pom {
                name.set("kraft-ksp")
                description.set("KSP annotation processor for the Kraft mapper generator")
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
