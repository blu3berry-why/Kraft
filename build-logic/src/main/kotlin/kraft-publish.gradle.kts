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
}

// Sign only when a real (Central) publish was requested: local publishes for
// testing (mavenLocal, the plugin's functional-test repo) must not hang on a
// gpg passphrase prompt in non-interactive shells.
val realPublishRequested = gradle.startParameter.taskNames.any {
    it.contains("publish", ignoreCase = true) &&
        !it.contains("MavenLocal") &&
        !it.contains("TestRepository")
}
if (realPublishRequested) {
    signing {
        useGpgCmd()
        isRequired = findProperty("signing.gnupg.keyName") != null
        sign(publishing.publications)
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    mustRunAfter(tasks.withType<Sign>())
}
