package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Regression: a `@MapEnum` whose source is a nested type (e.g. an enum
 * declared inside `Outer.Inner`) used to be referenced by its leaf simple
 * name only, producing a wrong top-level import. When an unrelated
 * top-level type with the same simple name existed in the same package,
 * the generated mapper compiled but mapped the wrong type.
 */
@OptIn(ExperimentalCompilerApi::class)
class MapEnumNestedSourceTest {

    @Test
    fun `@MapEnum with nested source qualifies the nested path and imports the outer class`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            class AuthResponse {
                class User {
                    enum class Role { STAFF, MANAGER, OWNER }
                }
            }

            // Unrelated top-level enum sharing the simple name 'Role'
            // — its presence used to mask the bug at compile time.
            enum class Role { A, B, C }

            enum class UserRole { STAFF, MANAGER, OWNER }

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

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val mapperFile = generated.first { it.name.contains("Role") && it.name.contains("UserRole") }
        val text = mapperFile.readText()

        // The nested path must appear qualified — bare `Role.STAFF` would
        // resolve to the unrelated top-level Role.
        assertThat(text).contains("AuthResponse.User.Role.STAFF -> UserRole.STAFF")
        assertThat(text).contains("AuthResponse.User.Role.MANAGER -> UserRole.MANAGER")
        assertThat(text).contains("AuthResponse.User.Role.OWNER -> UserRole.OWNER")

        // The receiver must be the nested type — not the bare `Role`.
        assertThat(text).contains("fun AuthResponse.User.Role.")

        // KotlinPoet imports the outer-most class; the leaf simple name
        // must NOT be imported as if it were top-level.
        assertThat(text).doesNotContain("import models.Role\n")
    }
}
