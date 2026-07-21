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
        var kmpSeen = false
        var kotlinPluginSeen = false
        project.pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_ID) {
            kmpSeen = true
            kotlinPluginSeen = true
            project.pluginManager.withPlugin(KSP_ID) {
                KraftKmpWiring.configure(project, kraftVersion(), extension)
            }
        }
        // kotlin-jvm and kotlin-android are mutually exclusive with each other and
        // with KMP, so at most one of these two branches fires.
        var singleTargetSeen = false
        for (id in listOf(KOTLIN_JVM_ID, KOTLIN_ANDROID_ID)) {
            project.pluginManager.withPlugin(id) {
                singleTargetSeen = true
                kotlinPluginSeen = true
                project.pluginManager.withPlugin(KSP_ID) {
                    KraftSingleTargetWiring.configure(project, kraftVersion(), extension)
                }
            }
        }
        // AGP 9 compiles Kotlin itself, so an Android module is Kotlin-bearing with
        // no org.jetbrains.kotlin.* plugin at all. These ids are handled separately
        // rather than added to the loop above, because they are ALSO present in
        // shapes that another branch already owns:
        //   - a KMP module targeting Android applies com.android.library too, and
        //     the KMP wiring is the correct one there;
        //   - on AGP 8, an Android module applies com.android.library AND
        //     kotlin-android.
        // In both cases wiring from the AGP id as well would add the flat ksp and
        // implementation dependencies on top of the ones the owning branch added.
        // The check runs inside the KSP callback, which is the last of the three
        // plugins to be applied in any working build (KSP configures itself from
        // the Kotlin plugin, so it must come after it), so by then the owning
        // branch has claimed the module.
        for (id in listOf(ANDROID_APPLICATION_ID, ANDROID_LIBRARY_ID)) {
            project.pluginManager.withPlugin(id) {
                kotlinPluginSeen = true
                project.pluginManager.withPlugin(KSP_ID) {
                    if (!kmpSeen && !singleTargetSeen) {
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
