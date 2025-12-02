
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("maven-publish")
}
kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Define source sets and add dependencies for each target
    sourceSets {

        val commonMain by getting {
            kotlin.srcDir("src/commonMain/kotlin")
            dependencies {
                // Add any dependencies needed for the common code

            }
        }

        val commonTest by getting
        val jvmMain by getting
        val jvmTest by getting

    }

}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YOUR_GH_USER/kraft")

            credentials {
                username = project.findProperty("gpr.user") as String?
                password = project.findProperty("gpr.token") as String?
            }
        }
    }

    publications {
        withType<MavenPublication> {
            groupId = "hu.nova.blu3berry.kraft"
            artifactId = "kraft-annotations"
            version = "0.1.0"
        }
    }
}