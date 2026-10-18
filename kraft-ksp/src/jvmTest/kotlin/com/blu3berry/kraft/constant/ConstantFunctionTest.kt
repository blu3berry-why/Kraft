package com.blu3berry.kraft.constant

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec `mapping/constant-values`, requirement "A @MapConstant function is called on every mapping". */
@OptIn(ExperimentalCompilerApi::class)
class ConstantFunctionTest {

    @Test
    fun `Function with an explicit target`() {
        val result = compileInAppMapping(
            """
            data class UserDto(val name: String)
            data class User(val name: String, val createdAt: Long)

            @MapConfig(source = UserDto::class, target = User::class)
            object UserMapper {
                var counter = 0L

                @MapConstant(target = "createdAt") fun now(): Long = counter++
            }
            """
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.mapperDeclaring("fun UserDto.toUser()")).contains("createdAt = UserMapper.now()")

        val loader = result.classLoader
        val dto = loader.loadClass("app.mapping.UserDto").getConstructor(String::class.java).newInstance("Ada")
        val facade = result.generatedFileDeclaring("fun UserDto.toUser()").nameWithoutExtension + "Kt"
        val toUser = loader.loadClass("app.mapping.generated.$facade").getMethod("toUser", dto.javaClass)
        val createdAt = { user: Any -> user.javaClass.getMethod("getCreatedAt").invoke(user) }

        assertThat(createdAt(toUser.invoke(null, dto))).isEqualTo(0L)
        assertThat(createdAt(toUser.invoke(null, dto))).isEqualTo(1L)
    }
}
