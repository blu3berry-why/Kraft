package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.report

/**
 * Find an annotation by its FQ name.
 */
fun KSAnnotated.findAnnotation(fqName: String): KSAnnotation? =
    annotations.firstOrNull {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == fqName
    }

/**
 * Extract a KClass<T> argument safely with detailed errors.
 */
fun KSAnnotation.getKClassArgOrNull(
    name: String,
    logger: KSPLogger,
    symbol: KSNode,
    annotationFqName: String
): KSType? {
    val arg = arguments.firstOrNull { it.name?.asString() == name }
        ?: run {
            logger.missingAnnotationArgument(
                annotationFqName.substringAfterLast('.'), name, ArgumentKind.CLASS, symbol
            )
            return null
        }

    val type = arg.value as? KSType
    if (type == null) {
        logger.invalidKClassArgument(annotationFqName, name, arg.value, symbol)
        return null
    }

    // KSP2 preserves type aliases in class-literal arguments
    // (`AliasedDto::class`); callers expect the underlying class type.
    return type.unwrapTypeAliases()
}

/**
 * Extract a String from an annotation argument.
 */
fun KSAnnotation.getStringArgOrNull(
    name: String,
    logger: KSPLogger,
    symbol: KSNode,
    annotationFqName: String
): String? {
    val arg = arguments.firstOrNull { it.name?.asString() == name }
        ?: run {
            logger.missingAnnotationArgument(
                annotationFqName.substringAfterLast('.'), name, ArgumentKind.STRING, symbol
            )
            return null
        }

    val value = arg.value as? String
    if (value == null) {
        logger.report(
            KraftDiagnostic.ANNOTATION_ARGUMENT_NOT_STRING,
            """
            @$annotationFqName argument '$name' must be a String.
            Found: ${arg.value?.let { it::class.simpleName }}
            """.trimIndent(),
            symbol
        )
        return null
    }

    return value
}

/**
 * Extract a String argument that has a declared default. An absent argument yields
 * [default] without logging; a present but mistyped one is still reported.
 */
fun KSAnnotation.getStringArgOrDefault(
    name: String,
    default: String,
    logger: KSPLogger,
    symbol: KSNode,
    annotationFqName: String
): String {
    if (arguments.none { it.name?.asString() == name }) return default
    return getStringArgOrNull(name, logger, symbol, annotationFqName) ?: default
}

/**
 * Extract an enum argument that has a declared default, as the entry's simple name.
 * An absent argument yields [default] without logging; a present but mistyped one
 * is still reported (and also yields [default]).
 */
fun KSAnnotation.getEnumArgOrDefault(
    name: String,
    default: String,
    logger: KSPLogger,
    symbol: KSNode,
    annotationFqName: String
): String = getEnumArgOrNull(name, logger, symbol, annotationFqName) ?: default

/**
 * Extract an enum annotation argument as its constant name string.
 *
 * In KSP, enum annotation values are surfaced as [KSType] instances; this helper
 * returns the simple name of the enum entry (e.g. `"FORWARD"`) so the caller can
 * map it to the actual enum via `valueOf`.  Returns `null` if the argument is absent
 * (caller should apply the default) or the value is not a [KSType].
 */
fun KSAnnotation.getEnumArgOrNull(
    name: String,
    logger: KSPLogger,
    symbol: KSNode,
    annotationFqName: String
): String? {
    val arg = arguments.firstOrNull { it.name?.asString() == name }
        ?: return null // absent → annotation default applies

    // KSP surfaces enum annotation arguments as KSClassDeclaration (enum entry nodes),
    // not as KSType — cast accordingly.
    val enumEntry = arg.value as? KSClassDeclaration ?: run {
        logger.report(
            KraftDiagnostic.ANNOTATION_ARGUMENT_NOT_ENUM,
            "@$annotationFqName argument '$name' must be an enum value. " +
                "Found: ${arg.value?.let { it::class.simpleName }}",
            symbol
        )
        return null
    }
    return enumEntry.simpleName.asString()
}

/**
 * Extract an array argument (used for fieldMapping).
 */
@Suppress("UNCHECKED_CAST")
fun <T> KSAnnotation.getArrayArgOrNull(
    name: String,
    logger: KSPLogger,
    symbol: KSNode,
    annotationFqName: String
): List<T>? {
    val arg = arguments.firstOrNull { it.name?.asString() == name }

    // Missing argument → annotation default applies → treat as empty list
    if (arg == null || arg.value == null) {
        return emptyList()
    }

    val value = arg.value

    return when (value) {
        is List<*> -> {
            value as? List<T> ?: run {
                logger.report(
                    KraftDiagnostic.ANNOTATION_ARGUMENT_NOT_ARRAY,
                    "@$annotationFqName argument '$name' must be a List<T>. " +
                        "Found List<${value.firstOrNull()?.let { it::class.simpleName }}>",
                    symbol
                )
                null
            }
        }

        is Array<*> -> {
            value.toList() as? List<T> ?: run {
                logger.report(
                    KraftDiagnostic.ANNOTATION_ARGUMENT_NOT_ARRAY,
                    "@$annotationFqName argument '$name' must be an Array<T>. " +
                        "Found Array<${value.firstOrNull()?.let { it::class.simpleName }}>",
                    symbol
                )
                null
            }
        }

        else -> {
            logger.report(
                KraftDiagnostic.ANNOTATION_ARGUMENT_NOT_ARRAY,
                "@$annotationFqName argument '$name' must be an array/List. Found: ${value!!::class.simpleName}",
                symbol
            )
            null
        }
    }
}


