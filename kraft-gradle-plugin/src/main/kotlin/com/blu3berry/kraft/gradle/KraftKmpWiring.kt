package com.blu3berry.kraft.gradle

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Wiring for Kotlin Multiplatform modules. Kotlin-plugin-typed, so it must only
 * be loaded after the KMP and KSP plugins are confirmed applied —
 * [KraftGradlePlugin] itself must stay free of these types (see its
 * implementation note). The platform-independent DSL-to-KSP-args translation
 * lives in [KraftKspArgEmitter].
 */
internal object KraftKmpWiring {

    private const val KSP_METADATA_TASK = "kspCommonMainKotlinMetadata"

    fun configure(project: Project, kraftVersion: String, extension: KraftExtension) {
        project.dependencies.add("kspCommonMainMetadata", "com.blu3berry.kraft:kraft-ksp:$kraftVersion")
        project.dependencies.add(
            "commonMainImplementation",
            "com.blu3berry.kraft:kraft-annotations:$kraftVersion"
        )

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.sourceSets.named("commonMain") { commonMain ->
            commonMain.kotlin.srcDir(
                project.layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin")
            )
        }

        project.tasks.withType(KotlinCompilationTask::class.java).configureEach { task ->
            if (task.name != KSP_METADATA_TASK) {
                task.dependsOn(KSP_METADATA_TASK)
            }
        }

        KraftKspArgEmitter.register(project, extension)
    }
}
