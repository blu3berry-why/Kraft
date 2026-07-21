package com.blu3berry.kraft.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

/**
 * One-line Kraft setup for Kotlin Multiplatform, Kotlin JVM, and Kotlin Android
 * modules.
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
        var kotlinPluginSeen = false
        project.pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_ID) {
            kotlinPluginSeen = true
            project.pluginManager.withPlugin(KSP_ID) {
                KraftKmpWiring.configure(project, kraftVersion(), extension)
            }
        }
        // Single-target Kotlin comes from one of two shapes:
        //   - kotlin-jvm / kotlin-android, applied explicitly, or
        //   - AGP 9's built-in Kotlin, where com.android.application/library
        //     compiles Kotlin itself and applying kotlin-android is an error.
        // On AGP 8 both an AGP id and kotlin-android are present, so the wiring
        // is guarded to run once -- otherwise the dependencies land twice.
        var singleTargetConfigured = false
        for (id in listOf(KOTLIN_JVM_ID, KOTLIN_ANDROID_ID, ANDROID_APPLICATION_ID, ANDROID_LIBRARY_ID)) {
            project.pluginManager.withPlugin(id) {
                kotlinPluginSeen = true
                project.pluginManager.withPlugin(KSP_ID) {
                    if (!singleTargetConfigured) {
                        singleTargetConfigured = true
                        KraftSingleTargetWiring.configure(project, kraftVersion(), extension)
                    }
                }
            }
        }
        project.afterEvaluate {
            checkRequiredPlugins(project, kotlinPluginSeen)
        }
    }

    private fun checkRequiredPlugins(project: Project, kotlinPluginSeen: Boolean) {
        if (!kotlinPluginSeen) {
            throw GradleException(
                """
                Kraft Gradle Plugin: '${project.path}' does not apply a supported Kotlin plugin.

                Kraft wires itself into one of these, applied in this module's plugins block
                before id("com.blu3berry.kraft"):
                How to fix, add one of:
                  1. Kotlin Multiplatform — `alias(libs.plugins.kotlinMultiplatform)` (id "$KOTLIN_MULTIPLATFORM_ID")
                  2. Kotlin JVM — `alias(libs.plugins.kotlinJvm)` (id "$KOTLIN_JVM_ID")
                  3. Kotlin Android — `alias(libs.plugins.kotlinAndroid)` (id "$KOTLIN_ANDROID_ID")
                  4. Android with AGP 9 built-in Kotlin — id "$ANDROID_APPLICATION_ID" or
                     "$ANDROID_LIBRARY_ID" on their own (do not also apply "$KOTLIN_ANDROID_ID")
                Details: https://blu3berry-why.github.io/Kraft/user-guide/getting-started/
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
        const val KOTLIN_JVM_ID = "org.jetbrains.kotlin.jvm"
        const val KOTLIN_ANDROID_ID = "org.jetbrains.kotlin.android"
        // AGP 9 compiles Kotlin itself (built-in Kotlin), so these ids mark a
        // Kotlin-bearing module even with no org.jetbrains.kotlin.* plugin.
        const val ANDROID_APPLICATION_ID = "com.android.application"
        const val ANDROID_LIBRARY_ID = "com.android.library"
        const val KSP_ID = "com.google.devtools.ksp"
        const val VERSION_RESOURCE = "kraft-plugin.properties"
        const val BROKEN_DISTRIBUTION_HINT =
            "This is a broken plugin artifact, not a configuration problem on your side — " +
                "please report it at https://github.com/blu3berry-why/Kraft/issues."
    }
}
