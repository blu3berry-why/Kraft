package com.blu3berry.kraft.gradle

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests running real Gradle builds via TestKit. The happy path asserts
 * the exact wiring the plugin replaces (the boilerplate consumers used to copy);
 * the error paths assert the actionable messages for missing prerequisites.
 */
class KraftGradlePluginFunctionalTest {

    private val kotlinVersion = System.getProperty("kraft.test.kotlinVersion")
    private val kspVersion = System.getProperty("kraft.test.kspVersion")
    private val pluginVersion = System.getProperty("kraft.test.pluginVersion")
    private val testRepo = System.getProperty("kraft.test.repo").replace('\\', '/')

    @TempDir
    lateinit var projectDir: File

    private fun writeSettings() {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    maven(url = "file://$testRepo")
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "kraft-plugin-test"
            """.trimIndent()
        )
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments, "--stacktrace")

    @Test
    fun `wires dependencies, sources, task deps and moduleId on a KMP module`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.blu3berry.kraft") version "$pluginVersion"
            }

            kotlin {
                jvm()
                js { nodejs() }
            }

            tasks.register("kraftProbe") {
                // Probe at configuration time (project state is off-limits in doLast
                // under the configuration cache; `extensions` there is the Task's own).
                val ksp = configurations.getByName("kspCommonMainMetadata")
                    .dependencies.map { "${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
                val ann = configurations.getByName("commonMainImplementation")
                    .dependencies.map { "${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
                val srcDirs = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
                    .sourceSets.getByName("commonMain").kotlin.srcDirs.map { it.path }
                val compileDeps = tasks.getByName("compileKotlinJvm").dependsOn.map { it.toString() }
                val kspExt = project.extensions.getByName("ksp")
                val kspArgs = kspExt.javaClass.methods
                    .firstOrNull { it.name == "getArguments" }
                    ?.invoke(kspExt)
                    .toString()
                doLast {
                    println("KSPDEPS=" + ksp)
                    println("ANNDEPS=" + ann)
                    println("SRCDIRS=" + srcDirs)
                    println("COMPILEDEPS=" + compileDeps)
                    println("KSPARGS=" + kspArgs)
                }
            }
            """.trimIndent()
        )

        val result = runner("kraftProbe").build()
        val output = result.output

        assertThat(output).contains("com.blu3berry.kraft:kraft-ksp:$pluginVersion")
        assertThat(output).contains("com.blu3berry.kraft:kraft-annotations:$pluginVersion")
        assertThat(output).contains("generated/ksp/metadata/commonMain/kotlin")
        assertThat(output).contains("kspCommonMainKotlinMetadata")
        assertThat(output).contains("kraft.moduleId")
    }

    @Test
    fun `kraft DSL translates sides, functionNameFormat and moduleId override into KSP args`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.blu3berry.kraft") version "$pluginVersion"
            }

            kotlin {
                jvm()
                js { nodejs() }
            }

            kraft {
                moduleId.set("customModuleId")
                functionNameFormat.set("to\${'$'}{target}From\${'$'}{source}")
                side("dto") {
                    packagePattern.set("com.example.dto.**")
                }
                side("domain") {
                    sideName.set("Domain")
                    packagePattern.set("com.example.domain.**")
                    emitMode.set("FULL_NAME_ONLY")
                }
            }

            tasks.register("kraftArgsProbe") {
                // Capture at configuration time (task realization runs after the
                // plugin's afterEvaluate, which is where the DSL emits its args).
                val kspExt = project.extensions.getByName("ksp")
                val kspArgs = kspExt.javaClass.methods
                    .firstOrNull { it.name == "getArguments" }
                    ?.invoke(kspExt)
                    .toString()
                doLast {
                    println("KSPARGS=" + kspArgs)
                }
            }
            """.trimIndent()
        )

        val output = runner("kraftArgsProbe").build().output

        // moduleId override wins over the project-path default.
        assertThat(output).contains("kraft.moduleId=customModuleId")
        assertThat(output).contains("kraft.functionNameFormat=to\${target}From\${source}")
        // dto side: name defaults to the slot capitalized.
        assertThat(output).contains("kraft.side.dto.name=Dto")
        assertThat(output).contains("kraft.side.dto.packagePattern=com.example.dto.**")
        // domain side: explicit name + emitMode carried through.
        assertThat(output).contains("kraft.side.domain.name=Domain")
        assertThat(output).contains("kraft.side.domain.packagePattern=com.example.domain.**")
        assertThat(output).contains("kraft.side.domain.emitMode=FULL_NAME_ONLY")
    }

    @Test
    fun `fails with actionable message when KSP plugin is missing`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
                id("com.blu3berry.kraft") version "$pluginVersion"
            }

            kotlin {
                jvm()
                js { nodejs() }
            }
            """.trimIndent()
        )

        val result = runner("help").buildAndFail()

        assertThat(result.output).contains("does not apply the KSP plugin")
        assertThat(result.output).contains("com.google.devtools.ksp")
    }

    @Test
    fun `fails with actionable message on a non-KMP module`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
                id("com.blu3berry.kraft") version "$pluginVersion"
            }
            """.trimIndent()
        )

        val result = runner("help").buildAndFail()

        assertThat(result.output).contains("does not apply the Kotlin Multiplatform plugin")
        assertThat(result.output).contains("supports Kotlin Multiplatform modules only")
    }
}
