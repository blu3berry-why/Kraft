package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class PropertySourceConverterErrorTest {

    @Test
    fun `unknown source property emits error`() {
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
                @com.blu3berry.kraft.config.MapUsing(source = "nonexistent", target = "text")
                fun convert(v: Int): String = v.toString()
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("source")
        assertThat(result.messages).contains("nonexistent")
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
                @com.blu3berry.kraft.config.MapUsing(source = "value", target = "nonexistent")
                fun convert(v: Int): String = v.toString()
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("target")
        assertThat(result.messages).contains("nonexistent")
    }

    @Test
    fun `parameter type mismatch emits error`() {
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
                @com.blu3berry.kraft.config.MapUsing(source = "value", target = "text")
                fun convert(v: String): String = v
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("mismatch")
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
                @com.blu3berry.kraft.config.MapUsing(source = "value", target = "text")
                fun convert(v: Int): Int = v
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("mismatch")
    }

    @Test
    fun `blank target emits error`() {
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
                @com.blu3berry.kraft.config.MapUsing(source = "value", target = "")
                fun convert(v: Int): String = v.toString()
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("target")
    }

    @Test
    fun `converter with wrong type argument emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val tags: List<String>)
            data class Dst(val tagStr: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "tags", target = "tagStr")
                fun convert(tags: List<Int>): String = tags.joinToString()
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("mismatch")
    }

    @Test
    fun `converter with nullable parameter when source is non-nullable emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val name: String)
            data class Dst(val label: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "name", target = "label")
                fun convert(v: String?): String = v ?: ""
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("mismatch")
    }

    @Test
    fun `duplicate converters for same source to target pair emits error`() {
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
                @com.blu3berry.kraft.config.MapUsing(source = "value", target = "text")
                fun convertFirst(v: Int): String = v.toString()

                @com.blu3berry.kraft.config.MapUsing(source = "value", target = "text")
                fun convertSecond(v: Int): String = "duplicate"
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Multiple")
    }
}
