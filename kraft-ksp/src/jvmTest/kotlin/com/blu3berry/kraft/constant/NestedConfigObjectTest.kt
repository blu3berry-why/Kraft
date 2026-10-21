package com.blu3berry.kraft.constant

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * A `@MapConfig` object nested in another declaration: generated code must reference it by its
 * full name (`Mappers.UserMapper`), not by its simple name alone.
 */
@OptIn(ExperimentalCompilerApi::class)
class NestedConfigObjectTest {

    @Test
    fun `Constant on a nested config object`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val currency: String)

            object Mappers {
                @MapConfig(source = UserDto::class, target = User::class)
                object UserMapper {
                    @MapConstant val currency = "HUF"
                }
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring("fun UserDto.toUser()")).contains("Mappers.UserMapper.currency")
    }

    @Test
    fun `MapUsing on a nested config object`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String)

            object Mappers {
                @MapConfig(source = UserDto::class, target = User::class)
                object UserMapper {
                    @MapUsing(target = "name")
                    fun UserDto.upperName(): String = name.uppercase()
                }
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring("fun UserDto.toUser()")).contains("Mappers.UserMapper")
    }
}
