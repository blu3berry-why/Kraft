package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration


data class ClassMappingScanResult(
    val direction: MappingDirection,
    val sourceType: KSClassDeclaration,
    val targetType: KSClassDeclaration,
    val annotatedClass: KSClassDeclaration,
    val propertyScanResults: List<PropertyScanResult> = emptyList(),
)

data class PropertyScanResult(
    val property: KSPropertyDeclaration,
    val mapFieldOther: String?,
    val isIgnored: Boolean
)

data class ConfigObjectScanResult(
    val fromType: KSClassDeclaration,
    val toType: KSClassDeclaration,
    val configObject: KSClassDeclaration,
    val fieldOverrides: List<FieldOverride>,
    val ignoredFields: List<String>,
    val converters: List<ConverterDescriptor>,
    val nestedMappings: List<NestedMappingDescriptor> = emptyList(),
)

data class FieldOverride(
    val from: String,
    val to: String
)
