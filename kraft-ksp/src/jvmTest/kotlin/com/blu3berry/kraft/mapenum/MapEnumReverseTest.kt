package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapEnumReverseTest {

    @Test
    fun `@MapReverse on @MapEnum with auto-by-same-name generates both directions`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE, BANNED }
            enum class StatusDto { ACTIVE, INACTIVE, BANNED }

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapEnum(
                source = Status::class,
                target = StatusDto::class
            )
            object StatusMapping
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        // Forward and reverse land in separate files
        // (Status_StatusDto_EnumMapper.kt and StatusDto_Status_EnumMapper.kt).
        val joined = generated.joinToString("\n") { it.readText() }

        assertThat(joined).contains("fun Status.toStatusDto()")
        assertThat(joined).contains("fun StatusDto.toStatus()")
        assertThat(joined).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(joined).contains("StatusDto.ACTIVE -> Status.ACTIVE")
    }

    @Test
    fun `reverse inverts explicit FieldMapping entries`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE, BLOCKED }
            enum class StatusDto { ACTIVE, INACTIVE, BANNED }

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapEnum(
                source = Status::class,
                target = StatusDto::class,
                fieldMappings = [
                    com.blu3berry.kraft.config.FieldMapping(source = "BLOCKED", target = "BANNED")
                ]
            )
            object StatusMapping
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val joined = generated.joinToString("\n") { it.readText() }

        // Forward
        assertThat(joined).contains("Status.BLOCKED -> StatusDto.BANNED")
        assertThat(joined).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        // Reverse: BANNED → BLOCKED is the inverted explicit mapping;
        // ACTIVE → ACTIVE is the auto-by-same-name fallback applied in
        // the reverse direction.
        assertThat(joined).contains("StatusDto.BANNED -> Status.BLOCKED")
        assertThat(joined).contains("StatusDto.ACTIVE -> Status.ACTIVE")
    }

    @Test
    fun `reverse errors when forward has duplicate target collapsing two sources`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { X, Y, Z }
            enum class StatusDto { A, B }

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapEnum(
                source = Status::class,
                target = StatusDto::class,
                fieldMappings = [
                    com.blu3berry.kraft.config.FieldMapping(source = "X", target = "A"),
                    com.blu3berry.kraft.config.FieldMapping(source = "Y", target = "A"),
                    com.blu3berry.kraft.config.FieldMapping(source = "Z", target = "B")
                ]
            )
            object StatusMapping
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        // The reverse inverts both A→X and A→Y, producing a duplicate
        // source 'A' on the reverse side — the existing duplicate-source
        // check fires.
        assertThat(result.messages).contains("duplicate source entries")
    }

    @Test
    fun `reverse errors when target enum has entries with no inverse`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            enum class Status { ACTIVE, INACTIVE }
            enum class StatusDto { ACTIVE, INACTIVE, EXTRA }

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapEnum(
                source = Status::class,
                target = StatusDto::class
            )
            object StatusMapping
            """
        )

        val result = TestKspRunner.compile(source)

        // Forward succeeds (every Status entry maps to a StatusDto entry of
        // the same name). Reverse fails because StatusDto.EXTRA has no
        // inverse on Status.
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("EXTRA")
    }

    @Test
    fun `@MapReverse alone on an object still errors`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            @com.blu3berry.kraft.config.MapReverse
            object Orphan
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("@MapReverse on 'Orphan'")
    }
}
