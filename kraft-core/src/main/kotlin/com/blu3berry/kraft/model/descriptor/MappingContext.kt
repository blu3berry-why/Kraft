package com.blu3berry.kraft.model.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.model.scan.MapNestedAnnotation

/**
 * Aggregated context passed to each [com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule]
 * during property resolution.
 *
 * @param sourceProps          All properties of the source class, keyed by name.
 * @param ignoredProperties    Properties to skip (merged from `@MapIgnore` and `@MapIgnoreField`).
 * @param classRenames         Renames from `@MapField` on the annotated target class:
 *                             targetName → sourceName.
 * @param configRenames        Renames from `@MapConfig.fieldOverrides`:
 *                             targetName → sourceName.
 * @param converters           `@MapUsing` converter descriptors from config objects.
 * @param globalConverters     Registry of `@KraftConverter` extension functions
 *                             discovered across the project — same-module sources
 *                             merged with `@KraftConverterDelegate` wrappers found
 *                             on the compile classpath. Consulted by `MappingContext`
 *                             when a property pair has mismatched types and no
 *                             `@MapUsing` claim.
 * @param nestedMappings       Explicit `@NestedMapping` declarations from `@MapConfig`.
 * @param classNestedOverrides Per-property `@MapNested` annotations, keyed by target
 *                             property name. Never contains [MapNestedAnnotation.NotAnnotated] entries.
 * @param sourceTypeName       Fully-qualified name of the source type (for error messages).
 * @param targetTypeName       Fully-qualified name of the target type (for error messages).
 * @param mappingDeclaration   The declaration that asked for this mapper: the `@MapConfig`
 *                             object, or the class annotated with `@MapFrom` / `@MapTo`.
 *                             A `@Suppress` on it silences suppressible per-property warnings
 *                             such as `KRAFT-W009`. Null when the context is built by hand.
 * @param constants            `@MapConstant` members that apply to this direction.
 */
data class MappingContext(
    val logger: KSPLogger,
    val sourceProps: Map<String, PropertyInfo>,
    val classRenames: Map<String, String>,
    val configRenames: Map<String, String>,
    val converters: List<ConverterDescriptor>,
    val globalConverters: GlobalConverterRegistry = GlobalConverterRegistry.EMPTY,
    val nestedMappings: List<NestedMappingDescriptor> = emptyList(),
    val ignoredProperties: Set<String> = emptySet(),
    val classNestedOverrides: Map<String, MapNestedAnnotation> = emptyMap(),
    val sourceTypeName: String,
    val targetTypeName: String,
    val mappingDeclaration: KSAnnotated? = null,
    val constants: List<ConstantDescriptor> = emptyList()
)
