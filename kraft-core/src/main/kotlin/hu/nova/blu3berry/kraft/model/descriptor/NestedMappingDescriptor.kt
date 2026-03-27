package hu.nova.blu3berry.kraft.model.descriptor

import hu.nova.blu3berry.kraft.model.MapperId
import hu.nova.blu3berry.kraft.model.TypeInfo

/**
 * Identifies a child mapper that must be invoked to map a nested property.
 *
 * Produced during scanning and stored both on [MapperDescriptor.nestedMappings]
 * and inside [PropertyMappingStrategy.NestedMapper].
 *
 * @param nestedMapperId  [MapperId] of the child mapper (source → target class pair).
 * @param sourceType      The nested source type.
 * @param targetType      The nested target type.
 * @param collectionKind  Non-null when the property is a collection (`List<Source>` → `List<Target>`, etc.).
 */
data class NestedMappingDescriptor(
    val nestedMapperId: MapperId,
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val collectionKind: CollectionKind? = null
) {
    val isCollection: Boolean get() = collectionKind != null
}
