package com.blu3berry.kraft.constant

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/constant-values`, requirements "The target must exist" (KRAFT-E067) and
 * "The member type must fit the target type" (KRAFT-E068).
 */
@OptIn(ExperimentalCompilerApi::class)
class ConstantTypeAndTargetTest {

    @Test
    fun `Misspelled target`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant val curency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.singleLineWith("[KRAFT-E067]")).endsWith(
            "[KRAFT-E067] @MapConstant 'UserMapper.curency' targets 'curency', " +
                "which is not a constructor parameter of 'User'."
        )
        assertThat(result.countOf("[KRAFT-E")).isEqualTo(1)
    }

    @Test
    fun `Wrong type`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val isActive: Boolean)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant val isActive = "yes"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.singleLineWith("[KRAFT-E068]")).endsWith(
            "[KRAFT-E068] @MapConstant 'UserMapper.isActive' has type String, " +
                "but target 'isActive' in 'User' has type Boolean."
        )
        assertThat(result.countOf("[KRAFT-E")).isEqualTo(1)
    }

    @Test
    fun `Nullable member into a non-null target`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val nickname: String)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant val nickname: String? = null
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.singleLineWith("[KRAFT-E068]")).endsWith(
            "[KRAFT-E068] @MapConstant 'UserMapper.nickname' has type String?, " +
                "but target 'nickname' in 'User' has type String."
        )
        assertThat(result.countOf("[KRAFT-E")).isEqualTo(1)
    }

    @Test
    fun `Non-null member into a nullable target`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val nickname: String?)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant val nickname = "anon"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring("fun UserDto.toUser()")).contains("nickname = UserMapper.nickname")
        assertThat(result.messages).doesNotContain("KRAFT-W009")
    }
}
