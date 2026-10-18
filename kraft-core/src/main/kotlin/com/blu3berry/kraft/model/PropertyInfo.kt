package com.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter

/**
 * Metadata for a single property of a source or target class.
 *
 * @param name         Simple property name.
 * @param type         Resolved type info (for matching and code generation).
 * @param declaration  Raw KSP declaration (used for error messages and reflection).
 * @param hasDefault   Whether the property has a default value in its constructor.
 *                     Verified by [PropertyMappingStrategy.Ignored] to ensure it is safe to omit.
 * @param parameter    The primary-constructor parameter backing this property, for target
 *                     properties only. Kotlin puts an annotation such as `@Suppress` on a
 *                     constructor `val`'s parameter rather than its property unless a use-site
 *                     target is written, so suppression checks look here too.
 */
data class PropertyInfo(
    val name: String,
    val type: TypeInfo,
    val declaration: KSPropertyDeclaration,
    val hasDefault: Boolean,
    val parameter: KSValueParameter? = null
)
