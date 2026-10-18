package com.blu3berry.kraft.constant

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec `mapping/constant-values`, requirement "Direction on a reverse mapper". */
@OptIn(ExperimentalCompilerApi::class)
class ConstantDirectionTest {

    private val forward = "fun UserDto.toUser()"
    private val reverse = "fun User.toUserDto()"

    @Test
    fun `AUTO picks forward`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String, val currency: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            @MapReverse
            object UserMapper {
                @MapConstant val currency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring(forward)).contains("currency = UserMapper.currency")
        assertThat(result.mapperDeclaring(reverse)).contains("currency = this.currency")
    }

    @Test
    fun `AUTO falls back to reverse`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String, val origin: String)
            data class User(val name: String)

            @MapConfig(source = UserDto::class, target = User::class)
            @MapReverse
            object UserMapper {
                @MapConstant val origin = "api"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring(reverse)).contains("origin = UserMapper.origin")
        assertThat(result.mapperDeclaring(forward)).doesNotContain("origin")
    }

    @Test
    fun `Explicit REVERSE`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String, val currency: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            @MapReverse
            object UserMapper {
                @MapConstant(direction = ConverterDirection.REVERSE) val currency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring(reverse)).contains("currency = UserMapper.currency")
        assertThat(result.mapperDeclaring(forward)).contains("currency = this.currency")
    }
}
