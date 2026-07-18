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

    // Project-level values of com.blu3berry.kraft.config.AliasEmitMode (INHERIT
    // is per-mapper only). Validated here so a DSL user gets the error in DSL
    // terms instead of the processor's raw kraft.side.* option syntax.
    private val VALID_EMIT_MODES = setOf("BOTH", "FULL_NAME_ONLY")

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
                // The project-path default must not clobber a moduleId the user
                // already set via a raw `ksp { arg(...) }` block; an explicit
                // `kraft { moduleId = … }` still wins over both.
                if (extension.moduleId.isPresent || "kraft.moduleId" !in ksp.arguments) {
                    ksp.arg("kraft.moduleId", extension.moduleId.getOrElse(project.path))
                }
                extension.functionNameFormat.orNull?.let { ksp.arg("kraft.functionNameFormat", it) }

                for (side in extension.sides) {
                    val slot = side.name
                    require(slot.isNotBlank()) {
                        """
                        Kraft: a side in the kraft { } block of '${project.path}' has a blank slot name.

                        How to fix:
                          Give every side a non-blank slot, e.g. side("dto") { … } — the slot
                          becomes the kraft.side.<slot>.* option key and the default alias name.
                        """.trimIndent()
                    }
                    val displayName = side.sideName.getOrElse(slot.replaceFirstChar { it.uppercase() })
                    val pattern = requireNotNull(side.packagePattern.orNull) {
                        """
                        Kraft: side '$slot' in the kraft { } block of '${project.path}' is missing `packagePattern`.

                        How to fix:
                          side("$slot") { packagePattern.set("com.example.$slot.**") }
                          The pattern is a package glob matching the classes that belong to this side.
                        """.trimIndent()
                    }
                    val emitMode = side.emitMode.orNull
                    require(emitMode == null || emitMode in VALID_EMIT_MODES) {
                        """
                        Kraft: side '$slot' in the kraft { } block of '${project.path}' has emitMode "$emitMode",
                        which is not a valid AliasEmitMode.

                        How to fix:
                          Use one of: ${VALID_EMIT_MODES.joinToString()} — e.g. side("$slot") { emitMode.set("BOTH") }
                          (INHERIT is only valid per-mapper via @MapConfig(aliasEmitMode = …).)
                        """.trimIndent()
                    }
                    emitSideArg(project, ksp, "$SIDE_PREFIX$slot.name", displayName)
                    emitSideArg(project, ksp, "$SIDE_PREFIX$slot.packagePattern", pattern)
                    side.template.orNull?.let { emitSideArg(project, ksp, "$SIDE_PREFIX$slot.template", it) }
                    emitMode?.let { emitSideArg(project, ksp, "$SIDE_PREFIX$slot.emitMode", it) }
                }
            }
        }
    }

    /**
     * Writes one DSL-derived side option, warning when it overwrites a value the
     * user also set via a raw `ksp { arg(...) }` block — the DSL runs later
     * (afterEvaluate) and wins, which should never happen silently.
     */
    private fun emitSideArg(project: Project, ksp: KspExtension, key: String, value: String) {
        val existing = ksp.arguments[key]
        if (existing != null && existing != value) {
            project.logger.warn(
                "Kraft: '$key' is set to \"$existing\" via ksp { arg(...) } and to \"$value\" via the " +
                    "kraft { } DSL; the DSL value wins. Remove one of the two to silence this warning."
            )
        }
        ksp.arg(key, value)
    }
}
