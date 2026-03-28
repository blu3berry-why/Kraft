package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class EnumToDataClassConverterTest {

    @Test
    fun `@MapUsing converter maps enum property to data class property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class Status { ACTIVE, INACTIVE }
            data class StatusInfo(val code: Int, val label: String)

            data class EventSource(val name: String, val status: Status)
            data class EventDto(val name: String, val statusInfo: StatusInfo)

            @com.blu3berry.kraft.config.MapConfig(
                source = EventSource::class,
                target = EventDto::class
            )
            object EventMapper {
                @com.blu3berry.kraft.config.MapUsing(source = "status", target = "statusInfo")
                fun convertStatus(status: Status): StatusInfo = when (status) {
                    Status.ACTIVE   -> StatusInfo(1, "Active")
                    Status.INACTIVE -> StatusInfo(0, "Inactive")
                }
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        assertThat(generated).isNotEmpty()
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun EventSource.toEventDto()")
        assertThat(content).contains("statusInfo = EventMapper.convertStatus(this.status)")
    }
}
