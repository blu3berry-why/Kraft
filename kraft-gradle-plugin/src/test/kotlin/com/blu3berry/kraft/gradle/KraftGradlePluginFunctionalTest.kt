package com.blu3berry.kraft.gradle

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Tag
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
    private val libsRepo = System.getProperty("kraft.test.libsRepo").replace('\\', '/')
    private val agpVersion = System.getProperty("kraft.test.agpVersion")
    private val agp9Version = System.getProperty("kraft.test.agp9Version")
    // AGP 9 requires Gradle >= 9.5.0, above the wrapper this build runs on, so
    // the built-in-Kotlin test drives TestKit on its own Gradle distribution.
    private val agp9GradleVersion = System.getProperty("kraft.test.agp9GradleVersion")
    private val compileSdk = System.getProperty("kraft.test.compileSdk")

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
                    maven(url = "file://$libsRepo")
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
        // Normalize path separators so the srcDir assertion holds on Windows.
        val output = result.output.replace('\\', '/')

        assertThat(output).contains("com.blu3berry.kraft:kraft-ksp:$pluginVersion")
        assertThat(output).contains("com.blu3berry.kraft:kraft-annotations:$pluginVersion")
        assertThat(output).contains("generated/ksp/metadata/commonMain/kotlin")
        assertThat(output).contains("kspCommonMainKotlinMetadata")
        assertThat(output).contains("kraft.moduleId")
    }

    /** Common plugins + kotlin header for probe-based test projects. */
    private fun buildScriptHeader(): String =
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
        """.trimIndent()

    /** Task that prints the effective KSP arguments map. */
    private fun probeTask(): String =
        """
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

    /**
     * The one expected-args contract shared by the DSL test and the raw-KSP-args
     * test. Both configuration styles must land on exactly these processor
     * options — if either path drifts, exactly one of the two tests breaks.
     */
    private fun assertEffectiveKraftArgs(output: String, expectedModuleId: String) {
        assertThat(output).contains("kraft.moduleId=$expectedModuleId")
        assertThat(output).contains("kraft.functionNameFormat=to\${target}From\${source}")
        assertThat(output).contains("kraft.side.dto.name=Dto")
        assertThat(output).contains("kraft.side.dto.packagePattern=com.example.dto.**")
        assertThat(output).contains("kraft.side.domain.name=Domain")
        assertThat(output).contains("kraft.side.domain.packagePattern=com.example.domain.**")
        assertThat(output).contains("kraft.side.domain.emitMode=FULL_NAME_ONLY")
        assertThat(output).contains("kraft.side.domain.template=map{side}")
    }

    @Test
    fun `kraft DSL translates sides, functionNameFormat and moduleId override into KSP args`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            ${buildScriptHeader()}

            kraft {
                moduleId.set("customModuleId")
                functionNameFormat.set("to\${'$'}{target}From\${'$'}{source}")
                side("dto") {
                    // sideName omitted: defaults to the slot capitalized ("Dto").
                    packagePattern.set("com.example.dto.**")
                }
                side("domain") {
                    sideName.set("Domain")
                    packagePattern.set("com.example.domain.**")
                    emitMode.set("FULL_NAME_ONLY")
                    template.set("map{side}")
                }
            }

            ${probeTask()}
            """.trimIndent()
        )

        val output = runner("kraftArgsProbe").build().output

        assertEffectiveKraftArgs(output, expectedModuleId = "customModuleId")
    }

    @Test
    fun `raw ksp args pass through the plugin unchanged`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            ${buildScriptHeader()}

            // Same effective configuration as the DSL test, written the raw way.
            // No kraft { } block: the plugin must not touch user-set kraft.* args
            // (moduleId is the exception — left unset here, so the plugin's
            // project-path default applies).
            ksp {
                arg("kraft.functionNameFormat", "to\${'$'}{target}From\${'$'}{source}")
                arg("kraft.side.dto.name", "Dto")
                arg("kraft.side.dto.packagePattern", "com.example.dto.**")
                arg("kraft.side.domain.name", "Domain")
                arg("kraft.side.domain.packagePattern", "com.example.domain.**")
                arg("kraft.side.domain.emitMode", "FULL_NAME_ONLY")
                arg("kraft.side.domain.template", "map{side}")
            }

            ${probeTask()}
            """.trimIndent()
        )

        val output = runner("kraftArgsProbe").build().output

        // Root project path is ":" — the plugin's moduleId default.
        assertEffectiveKraftArgs(output, expectedModuleId = ":")
    }

    @Test
    fun `kraft DSL side without packagePattern fails with actionable message`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            ${buildScriptHeader()}

            kraft {
                side("dto") { }
            }
            """.trimIndent()
        )

        val result = runner("help").buildAndFail()

        assertThat(result.output).contains("side 'dto'")
        assertThat(result.output).contains("packagePattern")
        assertThat(result.output).contains("How to fix")
    }

    @Test
    fun `kraft DSL invalid emitMode fails in DSL terms with the valid values`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            ${buildScriptHeader()}

            kraft {
                side("dto") {
                    packagePattern.set("com.example.dto.**")
                    emitMode.set("EVERYTHING")
                }
            }
            """.trimIndent()
        )

        val result = runner("help").buildAndFail()

        assertThat(result.output).contains("emitMode \"EVERYTHING\"")
        assertThat(result.output).contains("not a valid AliasEmitMode")
        assertThat(result.output).contains("BOTH, FULL_NAME_ONLY")
    }

    @Test
    fun `warns when a side option is set both via raw ksp arg and the DSL`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            ${buildScriptHeader()}

            ksp {
                arg("kraft.side.dto.name", "RawDto")
                arg("kraft.functionNameFormat", "rawFormat")
            }

            kraft {
                functionNameFormat.set("dslFormat")
                side("dto") { packagePattern.set("com.example.dto.**") }
            }

            ${probeTask()}
            """.trimIndent()
        )

        val output = runner("kraftArgsProbe").build().output

        // Both the side key and the top-level key warn on collision.
        assertThat(output).contains("'kraft.side.dto.name' is set to \"RawDto\"")
        assertThat(output).contains("'kraft.functionNameFormat' is set to \"rawFormat\"")
        assertThat(output).contains("the DSL value wins")
        // And the DSL values did win.
        assertThat(output).contains("kraft.side.dto.name=Dto")
        assertThat(output).contains("kraft.functionNameFormat=dslFormat")
    }

    private fun writeE2eSources(sourceRoot: String = "src/commonMain/kotlin") {
        fun src(path: String, content: String) {
            File(projectDir, "$sourceRoot/$path")
                .apply { parentFile.mkdirs() }
                .writeText(content.trimIndent())
        }
        src(
            "com/example/domain/Category.kt",
            """
            package com.example.domain
            data class Category(val id: Int, val label: String, val createdAt: String)
            """
        )
        src(
            "com/example/dto/CategoryDto.kt",
            """
            package com.example.dto
            data class CategoryDto(val id: Int, val label: String, val createdAt: Long)
            """
        )
        src(
            "com/example/mapper/CategoryMapper.kt",
            """
            package com.example.mapper
            import com.example.domain.Category
            import com.example.dto.CategoryDto
            import com.blu3berry.kraft.config.KraftConverter
            import com.blu3berry.kraft.config.MapConfig

            // Global converter: resolves the Long -> String mismatch on createdAt
            // and makes the processor emit this module's converter registry.
            @KraftConverter
            fun Long.toCreatedAtString(): String = toString()

            @MapConfig(source = CategoryDto::class, target = Category::class)
            object CategoryMapper
            """
        )
    }

    @Test
    fun `end to end - plugin-wired project generates and compiles side-aliased mappers`() {
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
                functionNameFormat.set("into\${'$'}{target}")
                side("dto") { packagePattern.set("com.example.dto.**") }
                side("domain") { packagePattern.set("com.example.domain.**") }
            }

            // A raw KSP arg must coexist with the DSL (and the plugin's
            // moduleId default must not clobber it).
            ksp {
                arg("kraft.moduleId", "e2eModule")
            }
            """.trimIndent()
        )
        writeE2eSources()

        // compileKotlinJvm exercises the whole chain: the plugin's task
        // ordering pulls kspCommonMainKotlinMetadata first, the processor runs
        // with the DSL-emitted options, and the generated mapper must compile.
        // --configuration-cache guards the plugin's afterEvaluate/provider
        // wiring against config-cache regressions before release.
        runner("compileKotlinJvm", "--configuration-cache").build()

        val generatedFiles = File(projectDir, "build/generated/ksp/metadata/commonMain/kotlin")
            .walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
        val generated = generatedFiles.joinToString("\n") { it.readText() }

        // functionNameFormat drives the verbose mapper name.
        assertThat(generated).contains("fun CategoryDto.intoCategory(")
        // The domain side (from the DSL) produces the short alias delegate.
        assertThat(generated).contains("fun CategoryDto.toDomain(")
        assertThat(generated).containsMatch("fun CategoryDto\\.toDomain\\([^)]*\\)[^=]*=\\s*intoCategory\\(")
        // The @KraftConverter resolved the Long -> String property mismatch.
        assertThat(generated).contains("toCreatedAtString(")
        // The raw ksp { arg("kraft.moduleId", …) } named the converter registry.
        assertThat(generatedFiles.map { it.name }.filter { it.contains("e2eModule") }).isNotEmpty()
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
    fun `fails with actionable message when no Kotlin plugin is applied`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                `java-library`
                id("com.blu3berry.kraft") version "$pluginVersion"
            }
            """.trimIndent()
        )

        val result = runner("help").buildAndFail()

        assertThat(result.output).contains("does not apply a supported Kotlin plugin")
        assertThat(result.output).contains("org.jetbrains.kotlin.multiplatform")
        assertThat(result.output).contains("org.jetbrains.kotlin.jvm")
        assertThat(result.output).contains("org.jetbrains.kotlin.android")
    }

    @Test
    fun `end to end - kotlin-jvm module generates and compiles side-aliased mappers`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.blu3berry.kraft") version "$pluginVersion"
            }

            kraft {
                functionNameFormat.set("into\${'$'}{target}")
                side("domain") { packagePattern.set("com.example.domain.**") }
            }
            """.trimIndent()
        )
        writeE2eSources(sourceRoot = "src/main/kotlin")

        // On kotlin-jvm KSP wires generated sources and task ordering itself; the
        // plugin only pins the deps and emits the args — a green compile plus the
        // side alias proves the whole single-target chain.
        runner("compileKotlin", "--configuration-cache").build()

        val generated = File(projectDir, "build/generated/ksp/main/kotlin")
            .walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }
            .joinToString("\n") { it.readText() }

        assertThat(generated).contains("fun CategoryDto.intoCategory(")
        assertThat(generated).contains("fun CategoryDto.toDomain(")
        assertThat(generated).contains("toCreatedAtString(")
    }

    @Test
    fun `end to end - kotlin-android module generates and compiles side-aliased mappers`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library") version "$agpVersion"
                id("org.jetbrains.kotlin.android") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.blu3berry.kraft") version "$pluginVersion"
            }

            android {
                namespace = "com.example.kraft"
                compileSdk = $compileSdk
            }

            kotlin {
                jvmToolchain(17)
            }

            kraft {
                functionNameFormat.set("into\${'$'}{target}")
                side("domain") { packagePattern.set("com.example.domain.**") }
            }
            """.trimIndent()
        )
        writeE2eSources(sourceRoot = "src/main/kotlin")

        runner("compileDebugKotlin").build()

        val generated = File(projectDir, "build/generated/ksp/debug/kotlin")
            .walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }
            .joinToString("\n") { it.readText() }

        assertThat(generated).contains("fun CategoryDto.intoCategory(")
        assertThat(generated).contains("fun CategoryDto.toDomain(")
        assertThat(generated).contains("toCreatedAtString(")
    }

    /**
     * AGP 9 ships built-in Kotlin: an Android module applies only
     * com.android.library (no org.jetbrains.kotlin.android -- applying it there
     * is an error, the kotlin extension already exists). Kraft must recognise
     * that shape as a Kotlin-bearing module and wire itself the same way.
     */
    @Tag("agp9")
    @Test
    fun `end to end - AGP 9 built-in Kotlin module generates and compiles side-aliased mappers`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library") version "$agp9Version"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.blu3berry.kraft") version "$pluginVersion"
            }

            android {
                namespace = "com.example.kraft"
                compileSdk = $compileSdk
            }

            kraft {
                functionNameFormat.set("into\${'$'}{target}")
                side("domain") { packagePattern.set("com.example.domain.**") }
            }
            """.trimIndent()
        )
        // KSP still registers its generated dir through kotlin.sourceSets, which
        // built-in Kotlin rejects; AGP ships this opt-out for exactly that case.
        File(projectDir, "gradle.properties").writeText("android.disallowKotlinSourceSets=false\n")
        writeE2eSources(sourceRoot = "src/main/kotlin")

        runner("compileDebugKotlin").withGradleVersion(agp9GradleVersion).build()

        val generated = File(projectDir, "build/generated/ksp/debug/kotlin")
            .walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }
            .joinToString("\n") { it.readText() }

        assertThat(generated).contains("fun CategoryDto.intoCategory(")
        assertThat(generated).contains("fun CategoryDto.toDomain(")
        assertThat(generated).contains("toCreatedAtString(")
    }

    /**
     * Regression guard for the idempotence fix: on AGP 8 a module applies BOTH
     * com.android.library and org.jetbrains.kotlin.android. Recognising the AGP
     * ids must not make Kraft wire itself twice and add duplicate dependencies.
     */
    @Tag("android")
    @Test
    fun `AGP 8 module with kotlin-android wires kraft dependencies exactly once`() {
        writeSettings()
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library") version "$agpVersion"
                id("org.jetbrains.kotlin.android") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
                id("com.blu3berry.kraft") version "$pluginVersion"
            }

            android {
                namespace = "com.example.kraft"
                compileSdk = $compileSdk
            }

            tasks.register("kraftDepProbe") {
                val ksp = configurations.getByName("ksp")
                    .dependencies.filter { it.group == "com.blu3berry.kraft" }
                    .map { "${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
                val impl = configurations.getByName("implementation")
                    .dependencies.filter { it.group == "com.blu3berry.kraft" }
                    .map { "${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
                doLast {
                    println("KSPCOUNT=" + ksp.size)
                    println("IMPLCOUNT=" + impl.size)
                }
            }
            """.trimIndent()
        )

        val output = runner("kraftDepProbe").build().output

        assertThat(output).contains("KSPCOUNT=1")
        assertThat(output).contains("IMPLCOUNT=1")
    }
}
