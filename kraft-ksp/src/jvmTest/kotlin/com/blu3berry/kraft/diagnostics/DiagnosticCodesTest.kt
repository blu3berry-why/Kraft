package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Covers the `diagnostics/diagnostic-codes` scenarios end to end, through a real KSP
 * compilation: a diagnostic carries a stable code, ends with its docs link, keeps the
 * code for the same input, and keeps its pre-code explanation text.
 */
@OptIn(ExperimentalCompilerApi::class)
class DiagnosticCodesTest {

    private fun requiredPropertySource(): SourceFile = SourceFile.kotlin(
        "Models.kt",
        """
        data class A(val name: String)
        data class B(val name: String, val total: Int)

        @com.blu3berry.kraft.config.MapConfig(source = A::class, target = B::class)
        object AToB
        """
    )

    @Test
    fun `required-property error carries an E code before its first sentence`() {
        val result = TestKspRunner.compile(requiredPropertySource())

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).containsMatch(
            Regex("""\[KRAFT-E\d{3}] Required property 'total' in target type""").toPattern()
        )
    }

    @Test
    fun `error ends with its See link`() {
        val result = TestKspRunner.compile(requiredPropertySource())

        val code = Regex("""\[(KRAFT-E\d{3})] Required property""").find(result.messages)!!.groupValues[1]
        val expectedLink =
            "See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#${code.lowercase()}"
        val lastNonBlank = result.messages.lines().last { it.isNotBlank() }

        assertThat(lastNonBlank.trim()).isEqualTo(expectedLink)
    }

    @Test
    fun `the same input produces the same code`() {
        val first = TestKspRunner.compile(requiredPropertySource())
        val second = TestKspRunner.compile(requiredPropertySource())

        // Regression pin: a released code never changes meaning. If this literal has to
        // change, the catalogue was renumbered, which the stability requirement forbids.
        assertThat(first.messages).contains("[KRAFT-E004] Required property 'total'")
        assertThat(second.messages).contains("[KRAFT-E004] Required property 'total'")
    }

    @Test
    fun `nullable String to String keeps its explanation and fallback snippet`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val name: String?)
            data class Dst(val name: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).contains("only nullability differs")
        assertThat(result.messages).contains("@MapUsing(target = \"name\")")
    }

    @Test
    fun `a warning carries a W code`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val name: String)
            data class UserDto(val name: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = User::class,
                target = UserDto::class,
                ignoredMappings = [com.blu3berry.kraft.config.MapIgnoreField("legacyId")]
            )
            object UserMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).containsMatch(Regex("""\[KRAFT-W\d{3}]""").toPattern())
    }
}
