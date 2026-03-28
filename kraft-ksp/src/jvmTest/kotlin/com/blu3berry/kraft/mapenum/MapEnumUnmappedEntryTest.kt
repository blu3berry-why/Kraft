package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapEnumUnmappedEntryTest {

    @Test
    fun `@MapEnum emits a KSP error when a source entry has no target mapping`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class Flag    { A, B, C }
            enum class FlagDto { A, B }   // C has no match in target and no fieldMappings entry

            @com.blu3berry.kraft.config.MapEnum(
                source = Flag::class,
                target   = FlagDto::class
            )
            object FlagMapping
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("unmapped source entries")
    }
}
