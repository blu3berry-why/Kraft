package hu.nova.blu3berry.kraft.model

data class MapperDescriptor(
    val id: MapperId,
    val fromType: TypeInfo,
    val toType: TypeInfo,
    val source: MappingSource,
    val propertyMappings: List<PropertyMappingStrategy>,
    val nestedMappings: List<NestedMappingDescriptor> = emptyList(),
    val enumMappings: List<EnumMappingDescriptor> = emptyList(),
    val converters: List<ConverterDescriptor> = emptyList(),
    val generateReverse: Boolean = false,      // for future
    val generateListExtensions: Boolean = true // future: List<From>.toListOfTo()
) {

    /**
     * Convenience for codegen / graph building.
     */
    val nestedDependencies: Set<MapperId> =
        propertyMappings
            .filterIsInstance<PropertyMappingStrategy.NestedMapper>()
            .map { it.nestedMapperId }
            .toSet()
}
