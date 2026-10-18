package com.blu3berry.kraft.missingsource

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/missing-source-properties`, requirement "The fallback never masks a reported error".
 * A property whose resolution already reported an error must not fall back to its default or to null.
 */
@OptIn(ExperimentalCompilerApi::class)
class FallbackDoesNotMaskErrorsTest {

    @Test
    fun `Same-named source with the wrong type`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String, val nickname: Int)
            data class PersonDto(val name: String, val nickname: String?)

            @com.blu3berry.kraft.config.MapConfig(source = PersonSource::class, target = PersonDto::class)
            object PersonMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005] Type mismatch for property 'nickname'.")
        assertThat(result.messages).doesNotContain("KRAFT-W009")
        val generated = result.sourcesGeneratedBySymbolProcessor.joinToString("\n") { it.readText() }
        assertThat(generated).doesNotContain("nickname = null")
    }

    @Test
    fun `Invalid @MapField override on a defaulted property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String)

            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(
                val name: String,
                @com.blu3berry.kraft.mapping.MapField("missing") val score: Int = 0
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E006] Invalid @MapField override for 'score'.")
        assertThat(result.sourcesGeneratedBySymbolProcessor.map { it.name }.toList())
            .doesNotContain("PersonSourceToPersonDtoMapper.kt")
    }
}
