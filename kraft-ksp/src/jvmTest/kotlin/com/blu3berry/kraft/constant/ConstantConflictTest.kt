package com.blu3berry.kraft.constant

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec `mapping/constant-values`, requirement "One rule per target" (KRAFT-E070). */
@OptIn(ExperimentalCompilerApi::class)
class ConstantConflictTest {

    private val conflictPrefix =
        "[KRAFT-E070] Target 'currency' in 'User' has more than one rule on 'UserMapper'"

    @Test
    fun `Constant and rename on one target`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String, val cur: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            @FieldMapping(source = "cur", target = "currency")
            object UserMapper {
                @MapConstant val currency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.singleLineWith("[KRAFT-E070]")).endsWith(
            "[KRAFT-E070] Target 'currency' in 'User' has more than one rule on 'UserMapper': " +
                "@MapConstant 'currency' and @FieldMapping(source = \"cur\", target = \"currency\"). Keep one."
        )
    }

    @Test
    fun `Constant and converter on one target`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant val currency = "HUF"

                @MapUsing(target = "currency") fun UserDto.cur() = "EUR"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        val line = result.singleLineWith("[KRAFT-E070]")
        assertThat(line).contains(conflictPrefix)
        assertThat(line).contains("@MapConstant 'currency' and @MapUsing 'cur'")
    }

    @Test
    fun `Two constants on one target`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant val currency = "HUF"

                @MapConstant(target = "currency") fun cur() = "EUR"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        val line = result.singleLineWith("[KRAFT-E070]")
        assertThat(line).contains(conflictPrefix)
        assertThat(line).contains("@MapConstant 'currency' and @MapConstant 'cur'")
    }

    @Test
    fun `Constant and ignore on one target`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val currency: String = "EUR")

            @MapConfig(source = UserDto::class, target = User::class)
            @MapIgnoreField("currency")
            object UserMapper {
                @MapConstant val currency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        val line = result.singleLineWith("[KRAFT-E070]")
        assertThat(line).contains(conflictPrefix)
        assertThat(line).contains("@MapConstant 'currency' and @MapIgnoreField(\"currency\")")
    }

    @Test
    fun `A constant and a rename on different targets do not conflict`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val cur: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            @FieldMapping(source = "cur", target = "currency")
            object UserMapper {
                @MapConstant val name = "anon"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-E070")
    }
}
