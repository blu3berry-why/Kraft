@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kraft.publish) apply true
}
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    js(IR) {
        browser()
    }

    wasmJs {
        browser()
    }

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }

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
        val jsMain by getting
        val jsTest by getting
        val wasmJsMain by getting
        val wasmJsTest by getting

    }
}

publishing {
    publications {
        withType<MavenPublication>().configureEach {
            when (name) {
                "kotlinMultiplatform" -> artifactId = "kraft-annotations"
                "jvm" -> artifactId = "kraft-annotations-jvm"
                "iosX64" -> artifactId = "kraft-annotations-iosx64"
                "iosArm64" -> artifactId = "kraft-annotations-iosarm64"
                "iosSimulatorArm64" -> artifactId = "kraft-annotations-iossimarm64"
                "js" -> artifactId = "kraft-annotations-js"
                "wasmJs" -> artifactId = "kraft-annotations-wasmjs"
            }

            pom {
                name.set("kraft-annotations")
                description.set("Annotation definitions for the Kraft compile-time mapper generator")
            }
        }
    }
}
