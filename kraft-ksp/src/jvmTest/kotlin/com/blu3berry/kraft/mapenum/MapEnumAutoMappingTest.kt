package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapEnumAutoMappingTest {

    @Test
    fun `@MapEnum auto-maps all entries when names match on both sides`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class Status { ACTIVE, INACTIVE, BANNED }
            enum class StatusDto { ACTIVE, INACTIVE, BANNED }

            @com.blu3berry.kraft.config.MapEnum(
                source = Status::class,
                target   = StatusDto::class
            )
            object StatusMapping
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first { "EnumMapper" in it.name }.readText()

        assertThat(content).contains("fun Status.toStatusDto()")
        assertThat(content).contains("Status.ACTIVE -> StatusDto.ACTIVE")
        assertThat(content).contains("Status.INACTIVE -> StatusDto.INACTIVE")
        assertThat(content).contains("Status.BANNED -> StatusDto.BANNED")
    }
}
