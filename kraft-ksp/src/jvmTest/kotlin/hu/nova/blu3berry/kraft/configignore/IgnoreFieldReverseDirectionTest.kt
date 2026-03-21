package hu.nova.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class IgnoreFieldReverseDirectionTest {

    @Test
    fun `IgnoreField with REVERSE direction has no effect on current forward-only generation`() {
        // REVERSE entries are reserved for future reverse-mapping support.
        // The forward mapper must still include the property.
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Report(val id: Int, val title: String, val notes: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = Report::class,
                to   = ReportDto::class,
                ignoredMappings = [
                    hu.nova.blu3berry.kraft.config.IgnoreField(
                        "notes",
                        direction = hu.nova.blu3berry.kraft.config.IgnoreDirection.REVERSE
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
        // REVERSE entry must not suppress the forward mapping
        assertThat(content).contains("notes = this.notes")
    }
}
