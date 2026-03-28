package com.blu3berry.kraft.basic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class WholeSourceConverterTest {

    @Test
    fun `whole-source regular function in object generates object call with this`() {
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
                fun combine(src: Src): String = "${'$'}{src.a}-${'$'}{src.b}"
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("combined = SrcMapper.combine(this)")
    }

    @Test
    fun `whole-source extension function in object generates with-block call`() {
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
                fun Src.combine(): String = "${'$'}{this.a}-${'$'}{this.b}"
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("combined = with(SrcMapper) { this@toDst.combine() }")
    }

    @Test
    fun `whole-source converter with wrong param type emits KSP error`() {
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
}
