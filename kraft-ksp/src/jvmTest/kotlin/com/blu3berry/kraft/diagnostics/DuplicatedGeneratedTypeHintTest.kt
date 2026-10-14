package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.TestKspRunner
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Covers `diagnostics/type-mismatch-hints`: a type-mismatch between two same-named,
 * same-shaped types with different enclosing classes gets a generator hint in front of
 * the existing guidance.
 */
@OptIn(ExperimentalCompilerApi::class)
class DuplicatedGeneratedTypeHintTest {

    private companion object {
        const val HINT = "Types differ only by enclosing class"
    }

    /**
     * The source types live in an upstream module and the target types in the consumer:
     * same-module enums (and nested classes) with matching entries are auto-derived, so
     * only a cross-module pair reaches the type-mismatch error.
     */
    private fun compileEnum(sourceRole: String, sourceEntries: String, targetEntries: String) =
        TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(
                SourceFile.kotlin(
                    "Models.kt",
                    """
                    package models

                    class AuthResponse {
                        class User(val role: $sourceRole) {
                            enum class Role { $sourceEntries }
                        }
                    }
                    """
                )
            ),
            consumerSources = listOf(
                SourceFile.kotlin(
                    "Target.kt",
                    """
                    package app

                    class AuthMe200Response {
                        enum class Role { $targetEntries }
                    }

                    @com.blu3berry.kraft.mapping.MapFrom(models.AuthResponse.User::class)
                    data class UserDto(val role: AuthMe200Response.Role)
                    """
                )
            )
        ).consumer

    @Test
    fun `duplicated enum gets the kmpgen hint`() {
        val result = compileEnum("Role", "ADMIN, MEMBER", "ADMIN, MEMBER")

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "Types differ only by enclosing class: AuthResponse.User.Role vs AuthMe200Response.Role, " +
                "with identical entries."
        )
        assertThat(result.messages).contains(
            "This is usually a code generator inlining the same \$ref twice (e.g. kmpgen). Fix it in the " +
                "generator, or bridge the pair with @MapEnum(source = AuthResponse.User.Role::class, " +
                "target = AuthMe200Response.Role::class)."
        )
    }

    @Test
    fun `same simple name with a different shape gets no hint`() {
        val result = compileEnum("Role", "ADMIN, MEMBER", "OWNER, GUEST")

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Type mismatch for property 'role'")
        assertThat(result.messages).doesNotContain(HINT)
    }

    @Test
    fun `duplicated class suggests converter or MapConfig`() {
        // Abstract classes: concrete nested classes would be auto-mapped and never reach the error.
        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(
                SourceFile.kotlin(
                    "Models.kt",
                    """
                    package models

                    class A { abstract class User(val id: Int, val name: String) }
                    data class Src(val user: A.User)
                    """
                )
            ),
            consumerSources = listOf(
                SourceFile.kotlin(
                    "Target.kt",
                    """
                    package app

                    class B { abstract class User(val id: Int, val name: String) }

                    @com.blu3berry.kraft.mapping.MapFrom(models.Src::class)
                    data class Dst(val user: B.User)
                    """
                )
            )
        ).consumer

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "Types differ only by enclosing class: A.User vs B.User, with identical constructor parameters."
        )
        assertThat(result.messages).contains("@MapConfig(source = A.User::class, target = B.User::class)")
        assertThat(result.messages).contains("@KraftConverter")
        assertThat(result.messages).doesNotContain("@MapEnum(source = A.User")
    }

    @Test
    fun `nullable duplicated enum keeps the nullability guidance`() {
        val result = compileEnum("Role?", "ADMIN, MEMBER", "ADMIN, MEMBER")

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(HINT)
        assertThat(result.messages).contains("The source is nullable and the target is not.")
    }
}
