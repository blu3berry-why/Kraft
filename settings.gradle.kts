rootProject.name = "Kraft"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradleup.nmcp.settings").version("1.4.4")
}

nmcpSettings {
    centralPortal {
        username = providers.gradleProperty("maven.central.username").orNull
        password = providers.gradleProperty("maven.central.password").orNull
        publishingType = "USER_MANAGED"
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":kraft-annotations")
include(":kraft-core")
include(":kraft-ksp")
include(":integration-tests:kmp-producer")
include(":integration-tests:kmp-consumer")
