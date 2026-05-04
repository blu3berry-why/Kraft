package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * `@MapEnum` is NOT required when both enums live in the same module and every
 * source entry has a target entry of the same name. The deriver synthesizes an
 * EnumMappingDescriptor up front; the rest of the pipeline (synthetic registry
 * + EnumMapperGenerator) is unchanged.
 */
@OptIn(ExperimentalCompilerApi::class)
class EnumByNameAutoTest {

    @Test
    fun `same-module enums with identical entries auto-generate without @MapEnum`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class Src(val status: Status, val name: String)
            data class Dst(val status: StatusDto, val name: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        val files = TestKspRunner.compileAndReturnGenerated(source)
        val parent = files.first { "ToDstMapper" in it.name }.readText()
        val enumMapper = files.first { "Status_To_StatusDto_EnumMapper" in it.name }.readText()

        assertThat(parent).contains("status = this.status.toStatusDto()")
        assertThat(enumMapper).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(enumMapper).contains("Status.INACTIVE -> StatusDto.INACTIVE")
    }
}
