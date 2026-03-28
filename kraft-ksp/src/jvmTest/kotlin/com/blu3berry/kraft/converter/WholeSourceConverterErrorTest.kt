package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class WholeSourceConverterErrorTest {

    @Test
    fun `wrong parameter type emits error mentioning source class`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val value: Int)
            data class Dst(val text: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(target = "text")
                fun combine(wrong: String): String = wrong
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("source class")
    }

    @Test
    fun `whitespace-only source is treated as whole-source and wrong param type emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val value: Int)
            data class Dst(val text: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "   ", target = "text")
                fun combine(wrong: String): String = wrong
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("source class")
    }

    @Test
    fun `return type mismatch emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val value: Int)
            data class Dst(val text: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(target = "text")
                fun combine(src: Src): Int = src.value
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("mismatch")
    }

    @Test
    fun `duplicate whole-source converters for same target emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val a: Int, val b: Int)
            data class Dst(val combined: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(target = "combined")
                fun combineFirst(src: Src): String = "${'$'}{src.a}"

                @com.blu3berry.kraft.config.MapUsing(target = "combined")
                fun combineSecond(src: Src): String = "${'$'}{src.b}"
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Multiple")
    }

    @Test
    fun `unknown target property emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val value: Int)
            data class Dst(val text: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(target = "nonexistent")
                fun combine(src: Src): String = src.value.toString()
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("target")
        assertThat(result.messages).contains("nonexistent")
    }
}
