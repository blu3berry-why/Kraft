package com.blu3berry.kraft.basic

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * KSP appends the `.kt` extension itself; passing a name that already ends in `.kt`
 * to `FileSpec.builder` produced `XToYMapper.kt.kt`.
 */
@OptIn(ExperimentalCompilerApi::class)
class GeneratedFileNameTest {

    @Test
    fun `generated mapper files have a single kt extension`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class StatusDto { OK }
            enum class Status { OK }

            data class Src(val status: StatusDto)
            data class Dst(val status: Status)

            @com.blu3berry.kraft.config.MapEnum(source = StatusDto::class, target = Status::class)
            object StatusMapping

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val names = TestKspRunner.compileAndReturnGenerated(source).map { it.name }

        assertThat(names).contains("SrcToDstMapper.kt")
        assertThat(names).contains("StatusDto_To_Status_EnumMapper.kt")
        assertThat(names.filter { it.endsWith(".kt.kt") }).isEmpty()
    }
}
