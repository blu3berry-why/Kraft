package com.blu3berry.kraft.model.scan

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor

/**
 * Raw result of scanning a `@MapConfig`-annotated object.
 *
 * Produced by the config object scanner and later converted into a [MapperDescriptor].
 *
 * @param sourceType       The source class declared in the `@MapConfig` arguments.
 * @param targetType       The target class declared in the `@MapConfig` arguments.
 * @param configObject     The object declaration that carries `@MapConfig`.
 * @param fieldOverrides   Rename pairs from `@FieldMapping` declarations inside the object.
 * @param ignoredMappings  Ignore rules from `@MapIgnoreField` declarations inside the object.
 * @param converters       Converter functions from `@MapUsing`-annotated functions.
 * @param nestedMappings   Explicit nested mappings from `@NestedMapping` declarations.
 * @param hasReverse       Whether `@MapReverse` is also present on the config object.
 */
data class ConfigObjectScanResult(
    val sourceType: KSClassDeclaration,
    val targetType: KSClassDeclaration,
    val configObject: KSClassDeclaration,
    val fieldOverrides: List<FieldOverride>,
    val ignoredMappings: List<IgnoredMappingConfig> = emptyList(),
    val converters: List<ConverterDescriptor>,
    val nestedMappings: List<NestedMappingDescriptor> = emptyList(),
    val hasReverse: Boolean = false,
)
