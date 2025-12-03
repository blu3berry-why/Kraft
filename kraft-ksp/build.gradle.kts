plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)   // KSP plugin
    id("maven-publish")       // for publishing to GitHub Packages
}

kotlin {
    jvm() // KSP processors run ONLY on JVM

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":kraft-annotations"))

                // KSP API for processors
                implementation(libs.ksp.api)

                // KotlinPoet for code generation
                implementation(libs.kotlinpoet)
                implementation(libs.kotlinpoet.ksp)
            }
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }

        val jvmTest by getting

    }

}

ksp {
    arg("kraft.functionNameFormat", "to${'$'}{target}From${'$'}{source}")
}

// Publishing to GitHub Packages (add missing part)
publishing {
    publications {
        withType<MavenPublication> {
            // ONLY publish the multiplatform metadata
            if (name != "jvm") {
                tasks.withType<PublishToMavenRepository>().configureEach {
                    onlyIf { publication.name == "jvm" }
                }
            }
            groupId = "hu.nova.blu3berry.kraft"
            artifactId = "kraft-ksp"
            version = project.properties["kraft.version"] as String
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