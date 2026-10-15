package com.blu3berry.kraft.processor.diagnostics

import com.google.devtools.ksp.symbol.KSAnnotated

private const val SUPPRESS_FQ_NAME = "kotlin.Suppress"
private const val SUPPRESS_SIMPLE_NAME = "Suppress"
private const val SUPPRESS_NAMES_ARG = "names"

/**
 * True when this declaration carries `@Suppress("<code>")` naming [diagnostic]'s code.
 *
 * `kotlin.Suppress` has SOURCE retention, but KSP sees it on same-module declarations,
 * which is the only place a suppressible Kraft warning fires. Matching is exact and
 * case-sensitive, and only this declaration is checked: suppressions on enclosing
 * classes or files do not apply.
 */
fun KSAnnotated.isKraftSuppressed(diagnostic: KraftDiagnostic): Boolean =
    annotations
        .filter { it.shortName.asString() == SUPPRESS_SIMPLE_NAME }
        .filter {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SUPPRESS_FQ_NAME
        }
        .flatMap { annotation ->
            annotation.arguments
                .filter { it.name?.asString() == SUPPRESS_NAMES_ARG }
                .flatMap { suppressedNames(it.value) }
        }
        .any { it == diagnostic.code }

private fun suppressedNames(value: Any?): Sequence<String> = when (value) {
    is String -> sequenceOf(value)
    is Array<*> -> value.asSequence().filterIsInstance<String>()
    is Iterable<*> -> value.asSequence().filterIsInstance<String>()
    else -> emptySequence()
}
