package com.blu3berry.kraft.mapignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapIgnoreMapToDirectionTest {

    @Test
    fun `@MapIgnore works on @MapTo-annotated class`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ReportDto(val title: String, val summary: String? = null)

            @com.blu3berry.kraft.mapping.MapTo(ReportDto::class)
            data class Report(
                val title: String,
                // @MapIgnore is on the source class (Report); the same-named target property
                // (ReportDto.summary) is skipped. Requires the target property to have a default.
                @com.blu3berry.kraft.mapping.MapIgnore
                val summary: String
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun Report.toReportDto()")
        assertThat(content).contains("title = this.title")
        assertThat(content).doesNotContain("summary")
    }
}
