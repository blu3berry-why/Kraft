package com.blu3berry.kraft.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

/**
 * One-line Kraft setup for Kotlin Multiplatform modules.
 *
 * ```kotlin
 * plugins {
 *     alias(libs.plugins.kotlinMultiplatform)
 *     alias(libs.plugins.ksp)
 *     id("com.blu3berry.kraft") version "<kraft version>"
 * }
 * ```
 *
 * Replaces the per-module boilerplate every Kraft consumer used to copy:
 * - adds `kraft-ksp` (kspCommonMainMetadata) and `kraft-annotations`
 *   (commonMainImplementation) pinned to this plugin's own version — which
 *   structurally enforces Kraft's same-version rule across modules
 * - wires `build/generated/ksp/metadata/commonMain/kotlin` into commonMain
 * - makes every Kotlin compilation depend on `kspCommonMainKotlinMetadata`
 * - defaults the `kraft.moduleId` KSP option to the project path so delegate
 *   diagnostics name real modules
 *
 * The Kotlin Multiplatform and KSP plugins are required but deliberately NOT
 * applied by this plugin: pulling them in would pin their versions and inherit
 * exactly the Kotlin-version coupling Kraft avoids by being KSP-based. Missing
 * plugins fail the build with instructions instead.
 *
 * Implementation note: this class must not reference Kotlin-plugin or KSP types
 * anywhere — including lambda parameter types — because Gradle's class decoration
 * reflects over all declared methods at plugin-apply time, and those types are
 * only on the classpath when the user applied the corresponding plugins. All
 * typed wiring lives in [KraftKmpWiring], which is loaded lazily inside the
 * `withPlugin` callbacks.
 */
class KraftGradlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("kraft", KraftExtension::class.java)
        var multiplatformSeen = false
        project.pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_ID) {
            multiplatformSeen = true
            project.pluginManager.withPlugin(KSP_ID) {
                KraftKmpWiring.configure(project, kraftVersion(), extension)
            }
        }
        project.afterEvaluate {
            checkRequiredPlugins(project, multiplatformSeen)
        }
    }

    private fun checkRequiredPlugins(project: Project, multiplatformSeen: Boolean) {
        if (!multiplatformSeen) {
            throw GradleException(
                """
                Kraft Gradle Plugin: '${project.path}' does not apply the Kotlin Multiplatform plugin.

                The plugin currently supports Kotlin Multiplatform modules only.
                How to fix:
                  1. Add `alias(libs.plugins.kotlinMultiplatform)` (id "$KOTLIN_MULTIPLATFORM_ID")
                     to this module's plugins block, before id("com.blu3berry.kraft"), or
                  2. For a single-platform JVM/Android module, apply Kraft manually for now:
                     see https://blu3berry-why.github.io/Kraft/user-guide/getting-started/
                """.trimIndent()
            )
        }
        if (!project.pluginManager.hasPlugin(KSP_ID)) {
            throw GradleException(
                """
                Kraft Gradle Plugin: '${project.path}' does not apply the KSP plugin.

                Kraft generates mappers with KSP, and deliberately does not force a KSP
                version on your build (your KSP version must match your Kotlin version).
                How to fix:
                  Add `alias(libs.plugins.ksp)` (id "$KSP_ID") to this module's plugins
                  block, before id("com.blu3berry.kraft").
                """.trimIndent()
            )
        }
    }

    /**
     * This plugin's own version, injected into kraft-plugin.properties at build time.
     * Used to pin kraft-ksp/kraft-annotations so all modules of a build resolve the
     * same Kraft version (delegates are discovered by version-specific names).
     */
    private fun kraftVersion(): String {
        val resource = javaClass.classLoader.getResourceAsStream(VERSION_RESOURCE)
            ?: throw GradleException(
                "Kraft Gradle Plugin: missing $VERSION_RESOURCE on the plugin classpath. " +
                    BROKEN_DISTRIBUTION_HINT
            )
        val properties = Properties()
        resource.use(properties::load)
        return properties.getProperty("version")
            ?: throw GradleException(
                "Kraft Gradle Plugin: no 'version' entry in $VERSION_RESOURCE. " +
                    BROKEN_DISTRIBUTION_HINT
            )
    }

    private companion object {
        const val KOTLIN_MULTIPLATFORM_ID = "org.jetbrains.kotlin.multiplatform"
        const val KSP_ID = "com.google.devtools.ksp"
        const val VERSION_RESOURCE = "kraft-plugin.properties"
        const val BROKEN_DISTRIBUTION_HINT =
            "This is a broken plugin artifact, not a configuration problem on your side — " +
                "please report it at https://github.com/blu3berry-why/Kraft/issues."
    }
}
