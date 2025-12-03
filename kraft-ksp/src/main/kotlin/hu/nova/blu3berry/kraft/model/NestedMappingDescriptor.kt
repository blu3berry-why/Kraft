package hu.nova.blu3berry.kraft.model

data class NestedMappingDescriptor(
    val nestedMapperId: MapperId,
    val sourceType: TypeInfo,
    val targetType: TypeInfo,
    val isCollection: Boolean = false
)