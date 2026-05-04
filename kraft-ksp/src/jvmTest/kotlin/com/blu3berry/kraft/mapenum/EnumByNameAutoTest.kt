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

        val files = TestKspRunner.compileAndReturnGenerated(source)
        val parent = files.first { "ToDstMapper" in it.name }.readText()
        val enumMapper = files.first { "Status_To_StatusDto_EnumMapper" in it.name }.readText()

        assertThat(parent).contains("status = this.status.toStatusDto()")
        assertThat(enumMapper).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(enumMapper).contains("Status.INACTIVE -> StatusDto.INACTIVE")
    }

    @Test
    fun `same-module enums with a source entry missing in target produce the existing type-mismatch error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, BLOCKED }
            enum class StatusDto { ACTIVE, BANNED }

            data class Src(val status: Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Type mismatch for property 'status'")
    }

    @Test
    fun `extra target entries do not block auto-derivation`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE, PENDING }

            data class Src(val status: Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val mapper = TestKspRunner.compileAndReturnGenerated(source)
            .first { "Status_To_StatusDto_EnumMapper" in it.name }
            .readText()
        assertThat(mapper).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(mapper).contains("Status.INACTIVE -> StatusDto.INACTIVE")
    }

    @Test
    fun `user-declared @MapEnum for the pair suppresses auto-derivation`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            // User explicitly declares the same pair the deriver would otherwise
            // pick up. This must NOT result in a duplicate-pair compile error.
            @com.blu3berry.kraft.config.MapEnum(source = Status::class, target = StatusDto::class)
            object StatusMapping

            data class Src(val status: Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val files = TestKspRunner.compileAndReturnGenerated(source)
            .filter { "Status_To_StatusDto_EnumMapper" in it.name }
        assertThat(files).hasSize(1)
    }

    @Test
    fun `cross-module enum pair does not auto-derive`() {
        val upstream = SourceFile.kotlin(
            "Upstream.kt",
            """
            package upstream

            enum class Status { ACTIVE, INACTIVE }
            """
        )
        val consumer = SourceFile.kotlin(
            "Models.kt",
            """
            package consumer

            enum class StatusDto { ACTIVE, INACTIVE }

            data class Src(val status: upstream.Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstream),
            consumerSources = listOf(consumer),
            upstreamKspOptions = mapOf("kraft.moduleId" to "upstream"),
            consumerKspOptions = mapOf("kraft.moduleId" to "consumer"),
        )
        // Cross-module: deriver requires both enums to be in the current
        // module. The user must publish an upstream @MapEnum if they want
        // the cross-module path to work; absent that, this is a hard error.
        assertThat(result.consumer.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.consumer.messages).contains("Type mismatch for property 'status'")
    }

    @Test
    fun `parent @MapReverse derives both directions when entries align both ways`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE }

            data class Src(val status: Status)
            data class Dst(val status: StatusDto)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            @com.blu3berry.kraft.config.MapReverse
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }
        val files = TestKspRunner.compileAndReturnGenerated(source)
        assertThat(files.any { "Status_To_StatusDto_EnumMapper" in it.name }).isTrue()
        assertThat(files.any { "StatusDto_To_Status_EnumMapper" in it.name }).isTrue()
    }
}
