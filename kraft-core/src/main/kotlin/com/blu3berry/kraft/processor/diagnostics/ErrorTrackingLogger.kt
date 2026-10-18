package com.blu3berry.kraft.processor.diagnostics

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

/**
 * A [KSPLogger] that forwards every call to [delegate] and counts the [error] calls.
 *
 * `PropertyResolver` wraps the logger in one of these per target property, so that after the
 * rule chain has run it knows whether resolving *this* property reported an error. Many rules
 * log an error and then pass, so a missing strategy alone does not tell.
 */
class ErrorTrackingLogger(private val delegate: KSPLogger) : KSPLogger {

    /** How many [error] calls this logger has forwarded. */
    var errorCount: Int = 0
        private set

    override fun logging(message: String, symbol: KSNode?) = delegate.logging(message, symbol)
    override fun info(message: String, symbol: KSNode?) = delegate.info(message, symbol)
    override fun warn(message: String, symbol: KSNode?) = delegate.warn(message, symbol)
    override fun error(message: String, symbol: KSNode?) {
        errorCount++
        delegate.error(message, symbol)
    }
    override fun exception(e: Throwable) = delegate.exception(e)
}
