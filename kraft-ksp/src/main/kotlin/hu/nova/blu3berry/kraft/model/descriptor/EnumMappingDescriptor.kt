package hu.nova.blu3berry.kraft.model.descriptor

import hu.nova.blu3berry.kraft.model.TypeInfo

/**
 * Describes an enum-to-enum mapping declared via `@MapEnum` or a `@FieldOverride`
 * inside `@MapConfig`.
 *
 * @param sourceType     The source enum type.
 * @param targetType     The target enum type.
 * @param entries        Per-entry name mappings (source constant → target constant).
 * @param allowDefault   Reserved — not yet set or consumed by the processor.
 * @param defaultTarget  Reserved — not yet set or consumed by the processor.
 */
data class EnumMappingDescriptor(
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val entries: List<EnumEntryMapping>,
    val allowDefault: Boolean = false,
    val defaultTarget: String? = null
)
