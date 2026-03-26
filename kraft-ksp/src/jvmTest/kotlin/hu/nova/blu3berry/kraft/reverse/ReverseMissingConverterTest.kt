package hu.nova.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ReverseMissingConverterTest {

    @Test
    fun `missing reverse converter emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val birthYear: Int, val name: String)
            data class UserDto(val age: String, val name: String)

            @hu.nova.blu3berry.kraft.config.MapReverse
            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = User::class,
                target = UserDto::class
            )
            object UserMapper {
                @hu.nova.blu3berry.kraft.config.MapUsing(source = "birthYear", target = "age")
                fun toAge(v: Int): String = (2026 - v).toString()
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("@MapReverse")
        assertThat(result.messages).contains("no reverse converter")
    }
}
