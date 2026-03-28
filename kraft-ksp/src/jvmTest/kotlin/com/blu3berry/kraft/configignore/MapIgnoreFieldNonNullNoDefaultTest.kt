package com.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapIgnoreFieldNonNullNoDefaultTest {

    @Test
    fun `MapIgnoreField on non-null property with no default produces a compilation error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Order(val id: Int, val total: Double)

            @com.blu3berry.kraft.config.MapConfig(
                source = Order::class,
                target   = OrderDto::class,
                ignoredMappings = [
                    com.blu3berry.kraft.config.MapIgnoreField("total")
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
