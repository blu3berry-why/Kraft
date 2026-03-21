package hu.nova.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class IgnoreFieldNonNullNoDefaultTest {

    @Test
    fun `IgnoreField on non-null property with no default produces a compilation error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Order(val id: Int, val total: Double)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = Order::class,
                to   = OrderDto::class,
                ignoredMappings = [
                    hu.nova.blu3berry.kraft.config.IgnoreField("total")
                ]
            )
            object OrderMapper

            data class OrderDto(val id: Int, val total: Double)  // no default on total
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("has no default value")
    }
}
