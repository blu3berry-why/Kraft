package com.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapIgnoreFieldSourceDirectionTest {

    @Test
    fun `MapIgnoreField with SOURCE direction has no effect on current forward-only generation`() {
        // SOURCE entries are reserved for future reverse-mapping support.
        // The forward mapper must still include the property.
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Report(val id: Int, val title: String, val notes: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Report::class,
                target   = ReportDto::class,
                ignoredMappings = [
                    com.blu3berry.kraft.config.MapIgnoreField(
                        "notes",
                        direction = com.blu3berry.kraft.config.IgnoreSide.SOURCE
                    )
                ]
            )
            object ReportMapper

            data class ReportDto(val id: Int, val title: String, val notes: String)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun Report.toReportDto()")
        // SOURCE entry must not suppress the forward mapping
        assertThat(content).contains("notes = this.notes")
    }
}
