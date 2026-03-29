plugins {
    `maven-publish`
    signing
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        withType<MavenPublication>().configureEach {
            groupId = "com.blu3berry.kraft"
            version = (project.properties["kraft.version"] as? String) ?: "0.0.0-SNAPSHOT"

            artifact(javadocJar)

            pom {
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
    }
}

signing {
    useGpgCmd()
    isRequired = findProperty("signing.gnupg.keyName") != null
    sign(publishing.publications)
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}
