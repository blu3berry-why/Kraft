package com.blu3berry.kraft.missingsource

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/missing-source-properties`, requirement "Unmapped nullable targets without a
 * default receive null and a warning".
 */
@OptIn(ExperimentalCompilerApi::class)
class NullableWithoutDefaultTest {

    private val models = SourceFile.kotlin(
        "Models.kt",
        """
        data class PersonSource(val name: String)

        data class PersonDto(val name: String, val nickname: String?)

        @com.blu3berry.kraft.config.MapConfig(source = PersonSource::class, target = PersonDto::class)
        object PersonMapper
        """
    )

    @Test
    fun `Nullable property without a default`() {
        val result = TestKspRunner.compile(models)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val mapper = result.sourcesGeneratedBySymbolProcessor
            .single { it.name == "PersonSourceToPersonDtoMapper.kt" }
            .readText()
        assertThat(mapper).contains(
            """
            public fun PersonSource.toPersonDto(): PersonDto = PersonDto(
              name = this.name,
              nickname = null
            )
            """.trimIndent()
        )

        val firstLine = "[KRAFT-W009] Nullable property 'nickname' in target type 'PersonDto' " +
            "has no mapping source, so Kraft passes null."
        val warnings = result.messages.lines().filter { it.contains("[KRAFT-W009]") }
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single()).endsWith(firstLine)
        assertThat(result.messages).contains("Give 'nickname' a default (= null) to make null the intended value.")
        assertThat(result.messages).contains("Or map it explicitly with @MapUsing(target = \"nickname\").")
        assertThat(result.messages).contains(
            "Or add @Suppress(\"KRAFT-W009\") to the property or to the mapping declaration."
        )
    }

    @Test
    fun `Warning fails a warnings-as-errors build`() {
        val result = TestKspRunner.compile(models, warningsAsErrors = true)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("KRAFT-W009")
    }
}
