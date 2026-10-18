package com.blu3berry.kraft.propertyresolver.rules

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class RequiredFieldErrorRuleTest {

    @Test
    fun `error - required target property with no matching source emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String)

            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val age: Int)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E004]")
        assertThat(result.messages).contains("age")
        assertThat(result.messages.lines().first { it.contains("[KRAFT-E004]") })
            .startsWith("[KRAFT-E004] Required property 'age' in target type")
        assertThat(result.messages)
            .contains("Or make the property nullable (Kraft then passes null and warns KRAFT-W009)")
    }

    @Test
    fun `no error - target property with default value does not trigger required-field error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String)

            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val score: Int = 0)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("score")
        assertThat(result.generatedMapperText()).contains("fun PersonSource.toPersonDto()")
    }

    @Test
    fun `no error - nullable target property with default does not trigger required-field error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String)

            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val nickname: String? = null)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("nickname")
        assertThat(result.generatedMapperText()).contains("fun PersonSource.toPersonDto()")
    }

    /** All Kotlin sources the processor generated, concatenated; empty when it generated none. */
    private fun JvmCompilationResult.generatedMapperText(): String =
        sourcesGeneratedBySymbolProcessor
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
}
