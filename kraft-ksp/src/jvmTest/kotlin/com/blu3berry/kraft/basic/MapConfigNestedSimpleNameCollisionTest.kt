package com.blu3berry.kraft.basic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Regression for K-N3 (extension-mapper variant): two `@MapConfig` declarations
 * whose source data classes share a leaf simple name (`User`) but live under
 * different parents must each produce a distinct generated file. Pre-fix this
 * threw FileAlreadyExistsException for `UserToUserDtoMapper.kt`.
 */
@OptIn(ExperimentalCompilerApi::class)
class MapConfigNestedSimpleNameCollisionTest {

    @Test
    fun `two @MapConfig with nested sources sharing a simple name emit distinct files`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            data class UserDto(val name: String)

            class AuthMe200Response {
                data class User(val name: String)
            }

            class AuthResponse {
                class Wrapper {
                    data class User(val name: String)
                }
            }

            @com.blu3berry.kraft.config.MapConfig(
                source = AuthMe200Response.User::class,
                target = UserDto::class
            )
            object MeUserMapping

            @com.blu3berry.kraft.config.MapConfig(
                source = AuthResponse.Wrapper.User::class,
                target = UserDto::class
            )
            object WrapperUserMapping
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        val files = TestKspRunner.compileAndReturnGenerated(source)
        val mappers = files.filter { it.name.contains("Mapper") && !it.name.contains("_EnumMapper") }

        assertThat(mappers).hasSize(2)

        val fileNames = mappers.map { it.name }
        assertThat(fileNames.any { it.contains("AuthMe200Response_UserToUserDtoMapper") }).isTrue()
        assertThat(fileNames.any { it.contains("AuthResponse_Wrapper_UserToUserDtoMapper") }).isTrue()
    }
}
