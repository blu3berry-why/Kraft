package com.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ReverseIgnoreSideTest {

    @Test
    fun `SOURCE direction activates in reverse and skips in forward`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val id: Int, val name: String, val internalNote: String = "")
            data class UserDto(val id: Int, val name: String, val extra: String = "")

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapConfig(
                source = User::class,
                target = UserDto::class,
                ignoredMappings = [
                    com.blu3berry.kraft.config.MapIgnoreField("extra", direction = com.blu3berry.kraft.config.IgnoreSide.TARGET),
                    com.blu3berry.kraft.config.MapIgnoreField("internalNote", direction = com.blu3berry.kraft.config.IgnoreSide.SOURCE)
                ]
            )
            object UserMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        // Forward: 'extra' is ignored (TARGET), 'internalNote' is NOT ignored (SOURCE skipped in forward)
        assertThat(allContent).contains("fun User.toUserDto()")
        // The forward mapper should NOT contain 'extra' assignment
        val forwardFile = files.first { it.contains("fun User.toUserDto()") }
        assertThat(forwardFile).doesNotContain("extra = ")

        // Reverse: 'internalNote' is ignored (SOURCE active), 'extra' is NOT ignored (TARGET skipped in reverse)
        assertThat(allContent).contains("fun UserDto.toUser()")
        val reverseFile = files.first { it.contains("fun UserDto.toUser()") }
        assertThat(reverseFile).doesNotContain("internalNote = ")
    }

    @Test
    fun `BOTH direction applies in both forward and reverse`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class User(val id: Int, val name: String, val temp: String = "")
            data class UserDto(val id: Int, val name: String, val temp: String = "")

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapConfig(
                source = User::class,
                target = UserDto::class,
                ignoredMappings = [
                    com.blu3berry.kraft.config.MapIgnoreField("temp", direction = com.blu3berry.kraft.config.IgnoreSide.BOTH)
                ]
            )
            object UserMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }

        // Both directions should skip 'temp'
        val forwardFile = files.first { it.contains("fun User.toUserDto()") }
        assertThat(forwardFile).doesNotContain("temp = ")

        val reverseFile = files.first { it.contains("fun UserDto.toUser()") }
        assertThat(reverseFile).doesNotContain("temp = ")
    }
}
