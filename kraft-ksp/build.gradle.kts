import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    id("maven-publish")
    signing
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name != "jvm") {
                tasks.withType<PublishToMavenRepository>().configureEach {
                    onlyIf { publication.name == "jvm" }
                }
            }
            groupId = "com.blu3berry.kraft"
            artifactId = "kraft-ksp"
            version = (project.properties["kraft.version"] as? String) ?: "0.0.0-SNAPSHOT"

            artifact(javadocJar)

            pom {
                name.set("kraft-ksp")
                description.set("KSP annotation processor for the Kraft mapper generator")
                url.set("https://github.com/blu3berry-why/Kraft")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("blu3berry-why")
                        name.set("blu3berry")
                        url.set("https://github.com/blu3berry-why")
                    }
                }
                scm {
                    url.set("https://github.com/blu3berry-why/Kraft")
                    connection.set("scm:git:git://github.com/blu3berry-why/Kraft.git")
                    developerConnection.set("scm:git:ssh://github.com/blu3berry-why/Kraft.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/blu3berry-why/Kraft")

            credentials {
                username = project.findProperty("gpr.user") as String?
                password = project.findProperty("gpr.token") as String?
            }
        }
        maven {
            name = "MavenCentral"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")

            credentials {
                username = findProperty("maven.central.username") as String?
                password = findProperty("maven.central.password") as String?
            }
        }
    }
}

signing {
    isRequired = findProperty("signing.key") != null
    useInMemoryPgpKeys(
        findProperty("signing.keyId") as String?,
        findProperty("signing.key") as String?,
        findProperty("signing.password") as String?
    )
    sign(publishing.publications)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
