package hu.nova.blu3berry.kraft.basic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapFromOnNonClassTest {

    @Test
    fun `@MapFrom on an object emits a KSP error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Source(val value: Int)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(Source::class)
            object NotAClass
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Incorrect use of @")
        assertThat(result.messages).contains("Expected annotation target: class")
    }

    @Test
    fun `@MapTo on an interface emits a KSP error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Target(val value: Int)

            @hu.nova.blu3berry.kraft.mapping.MapTo(Target::class)
            interface NotAClass
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Incorrect use of @")
        assertThat(result.messages).contains("Expected annotation target: class")
    }
}
