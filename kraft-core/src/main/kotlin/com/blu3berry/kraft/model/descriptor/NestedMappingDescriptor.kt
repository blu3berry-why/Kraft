package com.blu3berry.kraft.model.descriptor

import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.TypeInfo

/**
 * Identifies a child mapper that must be invoked to map a nested property.
 *
 * Produced during scanning and stored both on [MapperDescriptor.nestedMappings]
 * and inside [PropertyMappingStrategy.NestedMapper] or an [ElementOp.Nested] container leaf.
 * Always describes a single object; container levels are described by [ContainerShape].
 *
 * @param nestedMapperId  [MapperId] of the child mapper (source → target class pair).
 * @param sourceType      The nested source type.
 * @param targetType      The nested target type.
 */
data class NestedMappingDescriptor(
    val nestedMapperId: MapperId,
    val sourceType: TypeInfo,
    val targetType: TypeInfo
)
