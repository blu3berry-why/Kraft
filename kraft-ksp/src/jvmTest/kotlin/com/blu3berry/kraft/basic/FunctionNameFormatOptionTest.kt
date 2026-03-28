package com.blu3berry.kraft.basic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class FunctionNameFormatOptionTest {

    @Test
    fun `kraft_functionNameFormat option produces a custom function name`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class UserDto(val name: String)
            data class User(val name: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = UserDto::class,
                target = User::class
            )
            object UserMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            source,
            kspOptions = mapOf("kraft.functionNameFormat" to "map\${source}To\${target}")
        )

        assertThat(generated).isNotEmpty()
        assertThat(generated.any { it.readText().contains("fun UserDto.mapUserDtoToUser(") }).isTrue()
    }

    @Test
    fun `default function name is used when kraft_functionNameFormat is not set`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class OrderDto(val id: Int)
            data class Order(val id: Int)

            @com.blu3berry.kraft.config.MapConfig(
                source = OrderDto::class,
                target = Order::class
            )
            object OrderMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)

        assertThat(generated).isNotEmpty()
        assertThat(generated.any { it.readText().contains("fun OrderDto.toOrder(") }).isTrue()
    }

    @Test
    fun `kraft_functionNameFormat applies to enum mappers with custom format`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class SourceStatus { ACTIVE, INACTIVE }
            enum class TargetStatus { ACTIVE, INACTIVE }

            @com.blu3berry.kraft.config.MapEnum(
                source = SourceStatus::class,
                target = TargetStatus::class
            )
            object StatusMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(
            source,
            kspOptions = mapOf("kraft.functionNameFormat" to "map\${source}To\${target}")
        )

        assertThat(generated).isNotEmpty()
        assertThat(generated.any { it.readText().contains("fun SourceStatus.mapSourceStatusToTargetStatus(") }).isTrue()
    }

    @Test
    fun `enum mapper uses default function name when kraft_functionNameFormat is not set`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class SourceStatus { ACTIVE, INACTIVE }
            enum class TargetStatus { ACTIVE, INACTIVE }

            @com.blu3berry.kraft.config.MapEnum(
                source = SourceStatus::class,
                target = TargetStatus::class
            )
            object StatusMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)

        assertThat(generated).isNotEmpty()
        assertThat(generated.any { it.readText().contains("fun SourceStatus.toTargetStatus(") }).isTrue()
    }
}
