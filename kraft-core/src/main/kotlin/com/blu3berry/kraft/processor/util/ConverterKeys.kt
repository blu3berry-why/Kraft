package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.scan.ConverterTypeKey

/**
 * Builds the [ConverterTypeKey] identifying a converter by its source/target
 * type pair, or `null` when either side cannot be represented as a key.
 *
 * Types are resolved through type aliases first (see [unwrapTypeAliases]) so a
 * converter declared against an alias registers under the underlying class.
 * A side is rejected (`null`) when it is not backed by a [KSClassDeclaration]
 * (anonymous declarations, unexpanded parameterized aliases), has no qualified
 * name, or is PLATFORM-typed — a platform type must not silently collapse
 * into a NOT_NULL key.
 */
fun buildConverterTypeKey(sourceType: KSType, targetType: KSType): ConverterTypeKey? {
    val (sFq, sNull) = sourceType.fqAndNullable() ?: return null
    val (tFq, tNull) = targetType.fqAndNullable() ?: return null
    return ConverterTypeKey(sFq, sNull, tFq, tNull)
}

private fun KSType.fqAndNullable(): Pair<String, Boolean>? {
    val unwrapped = unwrapTypeAliases()
    val decl = unwrapped.declaration as? KSClassDeclaration ?: return null
    val fq = decl.qualifiedName?.asString() ?: return null
    val nullable = when (unwrapped.nullability) {
        Nullability.NULLABLE -> true
        Nullability.NOT_NULL -> false
        Nullability.PLATFORM -> return null
    }
    return fq to nullable
}
