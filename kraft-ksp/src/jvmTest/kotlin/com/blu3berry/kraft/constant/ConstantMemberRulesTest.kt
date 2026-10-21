package com.blu3berry.kraft.constant

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/constant-values`, requirement "Only readable, argument-free members qualify"
 * (KRAFT-E069). Each case reports exactly one error: `currency` has a default, so the
 * rejected member leaves no unmapped property behind.
 */
@OptIn(ExperimentalCompilerApi::class)
class ConstantMemberRulesTest {

    private fun compileWithMember(member: String) = compileInAppMapping(
        """
        data class UserDto(val name: String)
        data class User(val name: String, val currency: String = "EUR")

        @MapConfig(source = UserDto::class, target = User::class)
        object UserMapper {
            $member
        }
        """
    )

    @Test
    fun `Private member`() {
        val result = compileWithMember("@MapConstant private val currency = \"HUF\"")

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.singleLineWith("[KRAFT-E069]")).endsWith(
            "[KRAFT-E069] @MapConstant 'UserMapper.currency' must not be private: " +
                "the generated mapper cannot read it."
        )
        assertThat(result.countOf("[KRAFT-E")).isEqualTo(1)
    }

    @Test
    fun `Function with parameters`() {
        val result = compileWithMember("@MapConstant fun currency(country: String) = \"HUF\"")

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.singleLineWith("[KRAFT-E069]")).endsWith(
            "[KRAFT-E069] @MapConstant function 'UserMapper.currency' must take no parameters. " +
                "To compute a value from the source, use @MapUsing."
        )
        assertThat(result.countOf("[KRAFT-E")).isEqualTo(1)
    }

    @Test
    fun `Extension function`() {
        val result = compileWithMember("@MapConstant fun UserDto.currency() = \"HUF\"")

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.singleLineWith("[KRAFT-E069]")).endsWith(
            "[KRAFT-E069] @MapConstant function 'UserMapper.currency' must not be an extension function. " +
                "To compute a value from the source, use @MapUsing."
        )
        assertThat(result.countOf("[KRAFT-E")).isEqualTo(1)
    }

    @Test
    fun `Not inside a MapConfig object`() {
        val result = compileInAppMapping(
            """
            object Defaults {
                @MapConstant val currency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.singleLineWith("[KRAFT-E069]")).endsWith(
            "[KRAFT-E069] @MapConstant 'Defaults.currency' must be declared in a @MapConfig object."
        )
        assertThat(result.countOf("[KRAFT-E")).isEqualTo(1)
    }
}
