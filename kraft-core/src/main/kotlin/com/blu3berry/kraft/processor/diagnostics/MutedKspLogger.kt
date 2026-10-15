package com.blu3berry.kraft.processor.diagnostics

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode

/**
 * A [KSPLogger] that discards everything. Used when Kraft evaluates resolution rules
 * speculatively (for example to detect a redundant `@MapUsing`) and any error or warning
 * those rules log must not reach the user.
 */
object MutedKspLogger : KSPLogger {
    override fun logging(message: String, symbol: KSNode?) = Unit
    override fun info(message: String, symbol: KSNode?) = Unit
    override fun warn(message: String, symbol: KSNode?) = Unit
    override fun error(message: String, symbol: KSNode?) = Unit
    override fun exception(e: Throwable) = Unit
}
