package hu.nova.blu3berry.kraft.model

data class EnumMappingDescriptor(
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val entries: List<EnumEntryMapping>,
    val allowDefault: Boolean = false,
    val defaultTarget: String? = null
)