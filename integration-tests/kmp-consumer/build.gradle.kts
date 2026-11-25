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
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

ksp {
    arg("kraft.moduleId", "itestConsumer")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":kraft-ksp"))
}

// Roadmap item 17: the TraceEvent mapper's converter is a `@file:OptIn` converter from the
// producer's klib. Compiling proves the opt-in reached the generated mapper (the marker is
// ERROR level); this asserts the annotation itself, so a regression names the cause.
val verifyCrossModuleOptIn by tasks.registering {
    // A local, not a script-level val: doLast must not capture the script (configuration cache).
    val generatedKspDir = layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin")
    description = "Asserts the cross-module opt-in marker lands on the generated TraceEvent mapper."
    dependsOn("kspCommonMainKotlinMetadata")
    inputs.dir(generatedKspDir)
    doLast {
        val mapperSignature = "fun TraceEvent.toTraceEventDto()"
        val file = generatedKspDir.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .firstOrNull { mapperSignature in it.readText() }
            ?: throw GradleException("No generated file declares `$mapperSignature`.")
        val header = file.readText().substringBefore(mapperSignature).lines().takeLast(4)
        check(header.any { "@OptIn(ExperimentalProducerApi::class)" in it }) {
            "Generated mapper in ${file.name} lacks @OptIn(ExperimentalProducerApi::class):\n" +
                file.readText()
        }
    }
}

tasks.matching { it.name == "jvmTest" || it.name == "compileKotlinJs" }.configureEach {
    dependsOn(verifyCrossModuleOptIn)
}
