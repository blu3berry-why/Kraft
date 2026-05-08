package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Regression for K-N3: two `@MapEnum` declarations whose source enums share
 * a simple name (`Role`) but live under different parents must each produce
 * a distinct generated file. Pre-fix this threw FileAlreadyExistsException
 * because the filename was derived from leaf simple names only.
 */
@OptIn(ExperimentalCompilerApi::class)
class MapEnumNestedSimpleNameCollisionTest {

    @Test
    fun `two @MapEnum with nested sources sharing a simple name emit distinct files`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class AuthMe200Response {
                enum class Role { STAFF, MANAGER, OWNER }
            }

            class AuthResponse {
                class User {
                    enum class Role { STAFF, MANAGER, OWNER }
                }
            }

            enum class UserRole { STAFF, MANAGER, OWNER }

            @com.blu3berry.kraft.config.MapEnum(
                source = AuthMe200Response.Role::class,
                target = UserRole::class
            )
            object MeRoleMapping

            @com.blu3berry.kraft.config.MapEnum(
                source = AuthResponse.User.Role::class,
                target = UserRole::class
            )
            object UserRoleMapping
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        val files = TestKspRunner.compileAndReturnGenerated(source)
        val enumMappers = files.filter { it.name.contains("_EnumMapper") }

        // Two source enums → two distinct mapper files.
        assertThat(enumMappers).hasSize(2)

        val fileNames = enumMappers.map { it.name }
        assertThat(fileNames.any { it.contains("AuthMe200Response_Role_To_UserRole_EnumMapper") }).isTrue()
        assertThat(fileNames.any { it.contains("AuthResponse_User_Role_To_UserRole_EnumMapper") }).isTrue()
    }
}
