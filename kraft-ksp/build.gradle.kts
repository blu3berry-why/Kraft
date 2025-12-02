plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias (libs.plugins.ksp)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(libs.ksp.api) // KSP dependency
                implementation(libs.kotlinpoet)
                implementation(libs.kotlinpoet.ksp)
                implementation(project(":kraft-annotations"))
            }
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }
    }
}

ksp {
    arg("kraft.functionNameFormat", "to${'$'}{target}From${'$'}{source}")
}