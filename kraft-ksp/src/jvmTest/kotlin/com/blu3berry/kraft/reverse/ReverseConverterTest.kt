package com.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ReverseConverterTest {

    @Test
    fun `forward and reverse converters both work`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val birthYear: Int, val name: String)
            data class UserDto(val age: String, val name: String)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapConfig(
                source = User::class,
                target = UserDto::class
            )
            object UserMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "birthYear", target = "age")
                fun toAge(v: Int): String = (2026 - v).toString()

                @com.blu3berry.kraft.config.MapUsing(source = "age", target = "birthYear")
                fun toBirthYear(v: String): Int = 2026 - v.toInt()
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        // Forward
        assertThat(allContent).contains("fun User.toUserDto()")
        assertThat(allContent).contains("UserMapper.toAge(this.birthYear)")

        // Reverse
        assertThat(allContent).contains("fun UserDto.toUser()")
        assertThat(allContent).contains("UserMapper.toBirthYear(this.age)")
    }
}
