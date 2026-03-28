plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("maven-publish")
    signing
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
            artifactId = "kraft-core"
            version = (project.properties["kraft.version"] as? String) ?: "0.0.0-SNAPSHOT"

            artifact(javadocJar)

            pom {
                name.set("kraft-core")
                description.set("Core processor model and SPI for the Kraft mapper generator")
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
