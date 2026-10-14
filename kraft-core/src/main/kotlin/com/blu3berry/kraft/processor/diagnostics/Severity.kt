package com.blu3berry.kraft.processor.diagnostics

/** How a [KraftDiagnostic] is reported: [ERROR] fails compilation, [WARNING] does not. */
enum class Severity {
    ERROR,
    WARNING,
}
