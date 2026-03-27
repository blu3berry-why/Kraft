package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSPropertyDeclaration

/**
 * Metadata for a single property of a source or target class.
 *
 * @param name         Simple property name.
 * @param type         Resolved type info (for matching and code generation).
 * @param declaration  Raw KSP declaration (used for error messages and reflection).
 * @param hasDefault   Whether the property has a default value in its constructor.
 *                     Verified by [PropertyMappingStrategy.Ignored] to ensure it is safe to omit.
 */
data class PropertyInfo(
    val name: String,
    val type: TypeInfo,
    val declaration: KSPropertyDeclaration,
    val hasDefault: Boolean
)
