package com.blu3berry.kraft.processor.diagnostics

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

private const val BANNER_RULE = "----------------------------------------"

/**
 * Reports [message] as [diagnostic], routed to [KSPLogger.error] or [KSPLogger.warn]
 * by the diagnostic's [Severity].
 *
 * The message keeps the Kraft banner. Its first content line is prefixed with
 * `[KRAFT-X###] `, and the last line of the whole message is the `See: <docs link>`
 * line, placed after the closing rule so that it is the last non-blank line a
 * reader (or an agent scanning compiler output) sees.
 */
fun KSPLogger.report(diagnostic: KraftDiagnostic, message: String, symbol: KSNode?) {
    val text = formatDiagnostic(diagnostic, message)
    when (diagnostic.severity) {
        Severity.ERROR -> error(text, symbol)
        Severity.WARNING -> warn(text, symbol)
    }
}

/** Builds the full banner text [report] logs. Exposed for tests and for rendering previews. */
fun formatDiagnostic(diagnostic: KraftDiagnostic, message: String): String {
    val header = when (diagnostic.severity) {
        Severity.ERROR -> "Kraft KSP Error"
        Severity.WARNING -> "Kraft KSP Warning"
    }
    return buildString {
        appendLine(BANNER_RULE)
        appendLine(header)
        appendLine(BANNER_RULE)
        appendLine()
        appendLine("[${diagnostic.code}] ${message.trim()}")
        appendLine()
        appendLine(BANNER_RULE)
        append("See: ${diagnostic.docsUrl}")
    }
}
