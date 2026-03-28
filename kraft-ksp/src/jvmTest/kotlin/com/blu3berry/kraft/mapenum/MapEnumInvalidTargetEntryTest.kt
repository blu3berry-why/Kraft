package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapEnumInvalidTargetEntryTest {

    @Test
    fun `@MapEnum emits a KSP error when fieldMappings references a non-existent target entry`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class Color    { RED, GREEN, BLUE }
            enum class ColorDto { RED, GREEN, BLUE }

            @com.blu3berry.kraft.config.MapEnum(
                source = Color::class,
                target   = ColorDto::class,
                fieldMappings = [
                    com.blu3berry.kraft.config.FieldMapping(source = "RED", target = "YELLOW")
                ]
            )
            object ColorMapping
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("is not a value of target enum")
    }
}
