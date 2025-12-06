package hu.nova.blu3berry.kraft.processor.util

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*

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
            logger.missingAnnotationArgument(annotationFqName, name, symbol)
            return null
        }

    val type = arg.value as? KSType
    if (type == null) {
        logger.invalidKClassArgument(annotationFqName, name, arg.value, symbol)
        return null
    }

    return type
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
            logger.missingAnnotationArgument(annotationFqName, name, symbol)
            return null
        }

    val value = arg.value as? String
    if (value == null) {
        logger.error(
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
                logger.error(
                    "@$annotationFqName argument '$name' must be a List<T>. Found List<${value.firstOrNull()?.let { it::class.simpleName }}>",
                    symbol
                )
                null
            }
        }

        is Array<*> -> {
            value.toList() as? List<T> ?: run {
                logger.error(
                    "@$annotationFqName argument '$name' must be an Array<T>. Found Array<${value.firstOrNull()?.let { it::class.simpleName }}>",
                    symbol
                )
                null
            }
        }

        else -> {
            logger.error(
                "@$annotationFqName argument '$name' must be an array/List. Found: ${value!!::class.simpleName}",
                symbol
            )
            null
        }
    }
}


