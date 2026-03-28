package com.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.toTypeInfo
import com.blu3berry.kraft.processor.util.constructorPropertyMismatch
import com.blu3berry.kraft.processor.util.missingConstructorProperty
import com.blu3berry.kraft.processor.util.unsupportedTypeInConstructor

/**
 * Extracts and validates [PropertyInfo] entries from a target class's primary constructor.
 *
 * Each constructor parameter is matched to its declared property and resolved to a
 * [KSClassDeclaration]-backed [PropertyInfo].
 *
 * - Parameters whose type is not a [KSClassDeclaration] (e.g. generic type parameters) are
 *   skipped. If any such parameter has no default value, the extractor returns `null` after
 *   logging a [constructorPropertyMismatch] error.
 * - Returns `null` immediately (logging [missingConstructorProperty]) if a constructor
 *   parameter has no matching declared property.
 * - Otherwise returns the collected list, which may be shorter than the full parameter list
 *   when parameters with defaults were skipped.
 */
internal class TargetPropertyExtractor(private val logger: KSPLogger) {

    fun extract(
        targetDecl: KSClassDeclaration,
        targetCtor: KSFunctionDeclaration,
        targetTypeName: String
    ): List<PropertyInfo>? {

        val declaredProps = targetDecl.getDeclaredProperties().toList()
        var hasUnsupportedNonDefault = false

        val props = targetCtor.parameters.mapNotNull { param ->
            val paramName = param.name ?: return@mapNotNull null

            val declProp = declaredProps.firstOrNull { it.simpleName == paramName }
                ?: run {
                    logger.missingConstructorProperty(
                        typeName = targetTypeName,
                        parameterName = paramName.asString(),
                        available = declaredProps.map { it.simpleName.asString() },
                        symbol = param
                    )
                    return null
                }

            val ksType = param.type.resolve()
            val decl = ksType.declaration as? KSClassDeclaration ?: run {
                logger.unsupportedTypeInConstructor(
                    typeName = targetTypeName,
                    parameterName = paramName.asString(),
                    actualType = ksType.toString(),
                    symbol = param
                )
                if (!param.hasDefault) hasUnsupportedNonDefault = true
                return@mapNotNull null
            }

            PropertyInfo(
                name = paramName.asString(),
                type = decl.toTypeInfo(ksType),
                declaration = declProp,
                hasDefault = param.hasDefault
            )
        }

        if (hasUnsupportedNonDefault) {
            logger.constructorPropertyMismatch(targetTypeName, targetDecl)
            return null
        }

        return props
    }
}
