package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import hu.nova.blu3berry.kraft.model.ConverterDescriptor
import hu.nova.blu3berry.kraft.model.MappingDirection


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
    val fieldOverrides: List<FieldOverride>, // from -> to
    val ignoredFields: List<String>,
    val converters: List<ConverterDescriptor>
)

data class FieldOverride(
    val from: String,
    val to: String
)
