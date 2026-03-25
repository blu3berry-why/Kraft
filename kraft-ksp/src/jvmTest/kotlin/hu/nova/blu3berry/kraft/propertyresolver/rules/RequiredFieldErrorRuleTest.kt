package hu.nova.blu3berry.kraft.propertyresolver.rules

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
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

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val age: Int)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("age")
    }

    @Test
    fun `no error - target property with default value does not trigger required-field error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val score: Int = 0)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("score")
    }

    @Test
    fun `no error - nullable target property with default does not trigger required-field error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val nickname: String? = null)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("nickname")
    }
}
