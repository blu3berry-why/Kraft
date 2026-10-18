package com.blu3berry.kraft.constant

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/constant-values`, requirements "A @MapConstant property supplies a target value"
 * and "The target name defaults to the member name".
 */
@OptIn(ExperimentalCompilerApi::class)
class ConstantPropertyTest {

    @Test
    fun `Literal property, target from the member name`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant val currency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val mapper = result.mapperDeclaring("fun UserDto.toUser()")
        assertThat(mapper).contains("package app.mapping.generated")
        assertThat(mapper).contains("import app.mapping.UserMapper")
        assertThat(mapper).contains("currency = UserMapper.currency")

        // The generated file compiled: call it and read the value back.
        val loader = result.classLoader
        val dto = loader.loadClass("app.mapping.UserDto").getConstructor(String::class.java).newInstance("Ada")
        val facade = result.generatedFileDeclaring("fun UserDto.toUser()").nameWithoutExtension + "Kt"
        val user = loader.loadClass("app.mapping.generated.$facade")
            .getMethod("toUser", dto.javaClass)
            .invoke(null, dto)
        assertThat(user.javaClass.getMethod("getCurrency").invoke(user)).isEqualTo("HUF")
    }

    @Test
    fun `const val`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val isActive: Boolean)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant const val isActive = true
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring("fun UserDto.toUser()")).contains("isActive = UserMapper.isActive")
    }

    @Test
    fun `Constant over a same-named source property`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String, val currency: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant val currency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val mapper = result.mapperDeclaring("fun UserDto.toUser()")
        assertThat(mapper).contains("currency = UserMapper.currency")
        assertThat(mapper).doesNotContain("currency = this.currency")
        assertThat(result.messages).doesNotContain("KRAFT-")
    }

    @Test
    fun `Overridden target on a property`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val currency: String)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                @MapConstant(target = "currency") val defaultCurrency = "HUF"
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring("fun UserDto.toUser()"))
            .contains("currency = UserMapper.defaultCurrency")
    }
}
