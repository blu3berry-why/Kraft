package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.MutedKspLogger
import com.blu3berry.kraft.processor.diagnostics.Severity
import com.blu3berry.kraft.processor.diagnostics.report
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DiagnosticReportTest {

    private val rule = Regex("-{10,}")

    private fun contentLines(message: String): List<String> =
        message.lines().filter { it.isNotBlank() && !rule.matches(it) }

    @Test
    fun `a warning goes to warn with the code on the first content line and the link as the last line`() {
        val logger = RecordingKspLogger()

        logger.report(
            KraftDiagnostic.REDUNDANT_MAP_USING,
            "Converter 'toRows' duplicates native mapping.\n\nDetails line.",
            null
        )

        val entry = logger.entries.single()
        assertThat(entry.severity).isEqualTo("warn")

        val lines = contentLines(entry.message)
        assertThat(lines[0]).isEqualTo("Kraft KSP Warning")
        assertThat(lines[1]).isEqualTo("[KRAFT-W001] Converter 'toRows' duplicates native mapping.")
        assertThat(lines).contains("Details line.")
        assertThat(entry.message.lines().last()).isEqualTo(
            "See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#kraft-w001"
        )
    }

    @Test
    fun `the existing banner shape is kept around the message`() {
        val logger = RecordingKspLogger()

        logger.report(KraftDiagnostic.NULLABLE_TWIN_CONVERTER, "Twin found.", null)

        val lines = logger.entries.single().message.lines()
        assertThat(lines[0]).matches("-{10,}")
        assertThat(lines[1]).isEqualTo("Kraft KSP Warning")
        assertThat(lines[2]).matches("-{10,}")
        assertThat(lines[3]).isEmpty()
        assertThat(lines[4]).isEqualTo("[KRAFT-W002] Twin found.")
        assertThat(lines[lines.size - 2]).matches("-{10,}")
        assertThat(lines.last()).isEqualTo(
            "See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#kraft-w002"
        )
    }

    @Test
    fun `codes follow the KRAFT-X### shape, are unique and match their severity`() {
        KraftDiagnostic.entries.forEach { diagnostic ->
            assertThat(diagnostic.code).matches("KRAFT-[EW]\\d{3}")
            val letter = if (diagnostic.severity == Severity.ERROR) 'E' else 'W'
            assertThat(diagnostic.code[6]).isEqualTo(letter)
        }
        assertThat(KraftDiagnostic.entries.map { it.code }.toSet()).hasSize(KraftDiagnostic.entries.size)
    }

    @Test
    fun `W001 and W002 are reserved for the redundancy warnings`() {
        assertThat(KraftDiagnostic.REDUNDANT_MAP_USING.code).isEqualTo("KRAFT-W001")
        assertThat(KraftDiagnostic.NULLABLE_TWIN_CONVERTER.code).isEqualTo("KRAFT-W002")
    }

    @Test
    fun `the muted logger discards everything without throwing`() {
        MutedKspLogger.report(KraftDiagnostic.REDUNDANT_MAP_USING, "ignored", null)
        MutedKspLogger.error("ignored", null)
        MutedKspLogger.exception(IllegalStateException("ignored"))
    }
}
