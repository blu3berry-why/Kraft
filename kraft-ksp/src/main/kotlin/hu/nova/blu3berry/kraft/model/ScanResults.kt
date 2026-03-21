package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import hu.nova.blu3berry.kraft.config.IgnoreSide


data class ClassMappingScanResult(
    val direction: MappingDirection,
    val sourceType: KSClassDeclaration,
    val targetType: KSClassDeclaration,
    val annotatedClass: KSClassDeclaration,
    val propertyScanResults: List<PropertyScanResult> = emptyList(),
)

sealed interface MapNestedAnnotation {
    data object NotAnnotated : MapNestedAnnotation
    data object SameName : MapNestedAnnotation
    data class Renamed(val sourceName: String) : MapNestedAnnotation
}

data class PropertyScanResult(
    val property: KSPropertyDeclaration,
    val mapFieldSourceName: String?,
    val isIgnored: Boolean,
    val mapNested: MapNestedAnnotation = MapNestedAnnotation.NotAnnotated
)

/** A single ignore declaration extracted from [@MapIgnoreField][hu.nova.blu3berry.kraft.config.MapIgnoreField]. */
data class IgnoredMappingConfig(
    val name: String,
    val direction: IgnoreSide
)

data class ConfigObjectScanResult(
    val fromType: KSClassDeclaration,
    val toType: KSClassDeclaration,
    val configObject: KSClassDeclaration,
    val fieldOverrides: List<FieldOverride>,
    val ignoredMappings: List<IgnoredMappingConfig> = emptyList(),
    val converters: List<ConverterDescriptor>,
    val nestedMappings: List<NestedMappingDescriptor> = emptyList(),
)

data class FieldOverride(
    val from: String,
    val to: String
)
