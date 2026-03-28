package com.blu3berry.kraft.model.descriptor

import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.TypeInfo

/**
 * The central intermediate representation (IR) for a single source→target mapper.
 *
 * Built by the descriptor builder after scanning and consumed by the code generators
 * to emit the final extension function.
 *
 * @param id                     Unique identity key (used for deduplication and cycle detection).
 * @param sourceType             The type being mapped from.
 * @param targetType             The type being mapped to.
 * @param source                 How this mapper was declared (`@MapFrom`/`@MapTo` or `@MapConfig`).
 * @param propertyMappings       Resolved mapping strategy for every target property.
 * @param nestedMappings         Child mapper descriptors referenced by [PropertyMappingStrategy.NestedMapper] entries.
 * @param enumMappings           Enum-to-enum mapping descriptors for this mapper.
 * @param converters             `@MapUsing` converter descriptors scanned from the config object.
 */
data class MapperDescriptor(
    val id: MapperId,
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val source: MappingSource,
    val propertyMappings: List<PropertyMappingStrategy>,
    val nestedMappings: List<NestedMappingDescriptor> = emptyList(),
    val enumMappings: List<EnumMappingDescriptor> = emptyList(),
    val converters: List<ConverterDescriptor> = emptyList(),
) {

    /**
     * Set of child [MapperId]s this mapper depends on.
     * Derived from [PropertyMappingStrategy.NestedMapper] entries in [propertyMappings].
     * Used for dependency ordering and cycle detection during codegen.
     */
    val nestedDependencies: Set<MapperId> =
        propertyMappings
            .filterIsInstance<PropertyMappingStrategy.NestedMapper>()
            .map { it.nestedMappingDescriptor.nestedMapperId }
            .toSet()
}
