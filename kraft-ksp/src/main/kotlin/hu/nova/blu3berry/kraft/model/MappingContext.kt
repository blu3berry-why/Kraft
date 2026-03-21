package hu.nova.blu3berry.kraft.model

import com.google.devtools.ksp.processing.KSPLogger

/**
 * Aggregated context passed to each [hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule]
 * during property resolution.
 *
 * @param sourceProps          All properties of the source class, keyed by name.
 * @param ignoredProperties    Properties to skip (merged from `@MapIgnore` and `@MapIgnoreField`).
 * @param classOverrides       Renames from `@MapField` on the annotated target class:
 *                             targetName → sourceName.
 * @param configOverrides      Renames from `@MapConfig.fieldOverrides`:
 *                             targetName → sourceName.
 * @param converters           `@MapUsing` converter descriptors from config objects.
 * @param nestedMappings       Explicit `@NestedMapping` declarations from `@MapConfig`.
 * @param classNestedOverrides Per-property `@MapNested` annotations, keyed by target
 *                             property name. Never contains [MapNestedAnnotation.NotAnnotated] entries.
 * @param sourceTypeName       Fully-qualified name of the source type (for error messages).
 * @param targetTypeName       Fully-qualified name of the target type (for error messages).
 */
data class MappingContext(
    val logger: KSPLogger,
    val sourceProps: Map<String, PropertyInfo>,
    val classOverrides: Map<String, String>,
    val configOverrides: Map<String, String>,
    val converters: List<ConverterDescriptor>,
    val nestedMappings: List<NestedMappingDescriptor> = emptyList(),
    val ignoredProperties: Set<String> = emptySet(),
    val classNestedOverrides: Map<String, MapNestedAnnotation> = emptyMap(),
    val sourceTypeName: String,
    val targetTypeName: String
)