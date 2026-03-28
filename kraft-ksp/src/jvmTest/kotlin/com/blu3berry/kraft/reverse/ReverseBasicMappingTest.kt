package com.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ReverseBasicMappingTest {

    @Test
    fun `@MapReverse @MapFrom generates both forward and reverse`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val id: Int, val name: String)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.mapping.MapFrom(User::class)
            data class UserDto(val id: Int, val name: String)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        assertThat(allContent).contains("fun User.toUserDto()")
        assertThat(allContent).contains("fun UserDto.toUser()")
    }

    @Test
    fun `@MapReverse @MapTo generates both forward and reverse`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.mapping.MapTo(UserDto::class)
            data class User(val id: Int, val name: String)

            data class UserDto(val id: Int, val name: String)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        assertThat(allContent).contains("fun User.toUserDto()")
        assertThat(allContent).contains("fun UserDto.toUser()")
    }

    @Test
    fun `@MapReverse @MapConfig generates both forward and reverse`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val id: Int, val name: String)
            data class UserDto(val id: Int, val name: String)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapConfig(
                source = User::class,
                target = UserDto::class
            )
            object UserMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        assertThat(allContent).contains("fun User.toUserDto()")
        assertThat(allContent).contains("fun UserDto.toUser()")
    }
}
