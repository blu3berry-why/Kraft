package com.blu3berry.kraft.missingsource

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/missing-source-properties`, requirement "The fallback applies to every mapper kind".
 */
@OptIn(ExperimentalCompilerApi::class)
class MapperKindsTest {

    @Test
    fun `@MapFrom target with a default`() {
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
        val mapper = result.sourcesGeneratedBySymbolProcessor
            .single { it.name == "PersonSourceToPersonDtoMapper.kt" }
            .readText()
        assertThat(mapper).contains("fun PersonSource.toPersonDto()")
        assertThat(mapper).doesNotContain("score")
    }

    @Test
    fun `Reverse mapper with a defaulted source-side property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String, val createdAt: Long = 0L)
            data class PersonDto(val name: String)

            @com.blu3berry.kraft.config.MapConfig(source = PersonSource::class, target = PersonDto::class)
            @com.blu3berry.kraft.config.MapReverse
            object PersonMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val generated = result.sourcesGeneratedBySymbolProcessor.associate { it.name to it.readText() }
        assertThat(generated["PersonSourceToPersonDtoMapper.kt"]).contains("fun PersonSource.toPersonDto()")
        val reverse = generated["PersonDtoToPersonSourceMapper.kt"]
        assertThat(reverse).contains("fun PersonDto.toPersonSource()")
        assertThat(reverse).doesNotContain("createdAt")
    }
}
