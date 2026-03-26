package hu.nova.blu3berry.kraft.basic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
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

            @hu.nova.blu3berry.kraft.config.MapConfig(
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
        val text = generated.first().readText()
        assertThat(text).contains("fun UserDto.mapUserDtoToUser(")
    }

    @Test
    fun `default function name is used when kraft_functionNameFormat is not set`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class OrderDto(val id: Int)
            data class Order(val id: Int)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = OrderDto::class,
                target = Order::class
            )
            object OrderMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)

        assertThat(generated).isNotEmpty()
        val text = generated.first().readText()
        assertThat(text).contains("fun OrderDto.toOrder(")
    }
}
