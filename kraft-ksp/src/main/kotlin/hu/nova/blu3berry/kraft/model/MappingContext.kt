package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.processing.KSPLogger

data class MappingContext(
    val logger: KSPLogger,
    val sourceProps: Map<String, PropertyInfo>,
    val classOverrides: Map<String, String>,
    val configOverrides: Map<String, String>,
    val converters: List<ConverterDescriptor>,
    val nestedMappings: List<NestedMappingDescriptor> = emptyList(),
    val sourceTypeName: String,
    val targetTypeName: String
)