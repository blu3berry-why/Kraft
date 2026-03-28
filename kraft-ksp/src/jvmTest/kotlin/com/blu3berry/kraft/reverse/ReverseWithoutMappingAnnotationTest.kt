package com.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ReverseWithoutMappingAnnotationTest {

    @Test
    fun `@MapReverse without @MapFrom or @MapTo on class emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            @com.blu3berry.kraft.config.MapReverse
            data class UserDto(val id: Int, val name: String)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("@MapReverse")
        assertThat(result.messages).contains("@MapFrom or @MapTo")
    }

    @Test
    fun `@MapReverse without @MapConfig on object emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            @com.blu3berry.kraft.config.MapReverse
            object OrphanedMapper
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("@MapReverse")
        assertThat(result.messages).contains("@MapConfig")
    }
}
