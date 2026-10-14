package com.blu3berry.kraft.diagnostics

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

/** A [KSPLogger] that records every call as (severity, message) for assertions. */
class RecordingKspLogger : KSPLogger {
    data class Entry(val severity: String, val message: String)

    val entries = mutableListOf<Entry>()

    override fun logging(message: String, symbol: KSNode?) {
        entries += Entry("logging", message)
    }

    override fun info(message: String, symbol: KSNode?) {
        entries += Entry("info", message)
    }

    override fun warn(message: String, symbol: KSNode?) {
        entries += Entry("warn", message)
    }

    override fun error(message: String, symbol: KSNode?) {
        entries += Entry("error", message)
    }

    override fun exception(e: Throwable) {
        entries += Entry("exception", e.toString())
    }
}
