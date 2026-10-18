package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability

/** Shown in place of a type KSP could not resolve. */
const val UNRESOLVED_TYPE_RENDERING = "<unresolved>"

/**
 * Renders a type with simple names, as a user writes it in a declaration: `String`,
 * `String?`, `List<Int>`, `Map<String, *>`. An error type renders as [UNRESOLVED_TYPE_RENDERING].
 */
fun KSType.simpleRendering(): String {
    if (isError) return UNRESOLVED_TYPE_RENDERING
    val name = declaration.simpleName.asString()
    val args = if (arguments.isEmpty()) {
        ""
    } else {
        arguments.joinToString(", ", prefix = "<", postfix = ">") { arg ->
            arg.type?.resolve()?.simpleRendering() ?: "*"
        }
    }
    val mark = if (nullability == Nullability.NULLABLE) "?" else ""
    return "$name$args$mark"
}
