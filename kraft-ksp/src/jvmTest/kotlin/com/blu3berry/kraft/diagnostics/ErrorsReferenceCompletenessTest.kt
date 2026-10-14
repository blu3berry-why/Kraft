package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Enforces that `docs/user-guide/errors.md` documents every [KraftDiagnostic] and
 * nothing else. Each entry is a heading ending in an explicit `{#kraft-x###}` anchor.
 * A heading whose text contains "(retired)" may keep an anchor with no live enum entry.
 */
class ErrorsReferenceCompletenessTest {

    @Test
    fun `every diagnostic has a heading and every heading has a diagnostic`() {
        val page = locateErrorsPage()

        val problems = completenessProblems(page.readText())

        assertWithMessage("${page.path} is out of sync with KraftDiagnostic:\n${problems.joinToString("\n")}")
            .that(problems)
            .isEmpty()
    }

    // The checks below run on a synthetic page built from the enum, so they test the
    // checker itself and only the test above fails when the real page drifts.

    @Test
    fun `a deleted heading is reported by its code`() {
        val withoutW001 = syntheticPage()
            .lines()
            .filterNot { it.contains("{#kraft-w001}") }
            .joinToString("\n")

        assertThat(completenessProblems(withoutW001)).containsExactly(
            "KRAFT-W001 (Redundant @MapUsing) has no heading with anchor {#kraft-w001}"
        )
    }

    @Test
    fun `an anchor with no enum entry is reported by its code`() {
        val withGhost = syntheticPage() + "\n### KRAFT-W999 — Ghost {#kraft-w999}\n"

        assertThat(completenessProblems(withGhost)).containsExactly(
            "KRAFT-W999 is documented but has no KraftDiagnostic entry"
        )
    }

    @Test
    fun `a retired heading may keep its anchor`() {
        val withRetired = syntheticPage() + "\n### KRAFT-W998 — Old (retired) {#kraft-w998}\n"

        assertThat(completenessProblems(withRetired)).isEmpty()
    }

    private fun syntheticPage(): String =
        KraftDiagnostic.entries.joinToString("\n\n") { "### ${it.code} — ${it.title} {#${it.anchor}}\n\nCause." }

    private fun completenessProblems(markdown: String): List<String> {
        val headings = markdown.lines().mapNotNull { HEADING_ANCHOR.find(it.trim()) }
        val documented = headings.map { it.groupValues[2] }.toSet()
        val retired = headings
            .filter { it.groupValues[1].contains("(retired)", ignoreCase = true) }
            .map { it.groupValues[2] }
            .toSet()
        val catalogued = KraftDiagnostic.entries.map { it.anchor }.toSet()

        val undocumented = KraftDiagnostic.entries
            .filter { it.anchor !in documented }
            .map { "${it.code} (${it.title}) has no heading with anchor {#${it.anchor}}" }
        val orphaned = (documented - catalogued - retired)
            .sorted()
            .map { "${it.uppercase()} is documented but has no KraftDiagnostic entry" }
        return undocumented + orphaned
    }

    /** Walks up from the working directory to the repo root: `settings.gradle.kts` next to `docs/`. */
    private fun locateErrorsPage(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "docs").isDirectory) {
                val page = File(dir, "docs/user-guide/errors.md")
                check(page.isFile) { "Expected the errors reference at ${page.path}" }
                return page
            }
            dir = dir.parentFile
        }
        error("Could not locate the repository root from ${File("").absolutePath}")
    }

    private companion object {
        val HEADING_ANCHOR = Regex("""^#{1,6}\s+(.*?)\s*\{#(kraft-[ew]\d{3})\}$""")
    }
}
