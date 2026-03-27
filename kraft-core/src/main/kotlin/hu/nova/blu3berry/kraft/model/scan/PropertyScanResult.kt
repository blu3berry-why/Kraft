package hu.nova.blu3berry.kraft.model.scan

import com.google.devtools.ksp.symbol.KSPropertyDeclaration

/**
 * Describes how a single property is annotated on a `@MapFrom`/`@MapTo` class.
 *
 * @param property           The KSP property declaration.
 * @param mapFieldSourceName Rename override from `@MapField`; null if not annotated.
 * @param isIgnored          Whether the property is marked with `@MapIgnore`.
 * @param mapNested          The `@MapNested` annotation state for this property.
 */
data class PropertyScanResult(
    val property: KSPropertyDeclaration,
    val mapFieldSourceName: String?,
    val isIgnored: Boolean,
    val mapNested: MapNestedAnnotation = MapNestedAnnotation.NotAnnotated
)
