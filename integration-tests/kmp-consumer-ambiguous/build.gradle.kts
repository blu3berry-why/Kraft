plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":kraft-annotations"))
                implementation(project(":integration-tests:kmp-producer"))
                implementation(project(":integration-tests:kmp-producer2"))
            }
        }
    }
}

ksp {
    arg("kraft.moduleId", "itestConsumerAmbiguous")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":kraft-ksp"))
}
