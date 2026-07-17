package com.blu3berry.kraft.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * The Kotlin-plugin/KSP-typed half of [KraftGradlePlugin]. Kept in its own class
 * so the JVM only loads these types after both plugins are confirmed applied —
 * [KraftGradlePlugin] itself must stay free of them (see its implementation note).
 */
internal object KraftKmpWiring {

    private const val KSP_METADATA_TASK = "kspCommonMainKotlinMetadata"
    private const val SIDE_PREFIX = "kraft.side."

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

        // Emit KSP args after evaluation so the user's `kraft { }` block is fully
        // populated first. The processor only reads these via env.options; the DSL
        // is a typed front-end that produces exactly the raw args a user could set
        // by hand in `ksp { arg(...) }` (which still coexists — both write one map).
        project.afterEvaluate {
            project.extensions.configure(KspExtension::class.java) { ksp ->
                ksp.arg("kraft.moduleId", extension.moduleId.getOrElse(project.path))
                extension.functionNameFormat.orNull?.let { ksp.arg("kraft.functionNameFormat", it) }

                for (side in extension.sides) {
                    val slot = side.name
                    val displayName = side.sideName.getOrElse(slot.replaceFirstChar { it.uppercase() })
                    val pattern = requireNotNull(side.packagePattern.orNull) {
                        "Kraft: side '$slot' in the kraft { } block is missing `packagePattern`."
                    }
                    ksp.arg("$SIDE_PREFIX$slot.name", displayName)
                    ksp.arg("$SIDE_PREFIX$slot.packagePattern", pattern)
                    side.template.orNull?.let { ksp.arg("$SIDE_PREFIX$slot.template", it) }
                    side.emitMode.orNull?.let { ksp.arg("$SIDE_PREFIX$slot.emitMode", it) }
                }
            }
        }
    }
}
