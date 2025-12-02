package hu.nova.blu3berry.kraft.model

sealed interface PropertyMappingStrategy {
    val targetProperty: PropertyInfo

    /**
     * Direct “same name, same type” assignment:
     * target.x = source.x
     */
    data class Direct(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo
    ) : PropertyMappingStrategy

    /**
     * Renamed property, same type:
     * target.newName = source.oldName
     */
    data class Renamed(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo
    ) : PropertyMappingStrategy

    /**
     * Use a converter function:
     * - extension: source.prop.converter()
     * - object: ConfigObject.converter(source.prop)
     */
    data class ConverterFunction(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val converter: ConverterDescriptor
    ) : PropertyMappingStrategy

    /**
     * Uses another generated mapper for nested types:
     * target.child = source.child.toChildDto()
     */
    data class NestedMapper(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val nestedMapperId: MapperId
    ) : PropertyMappingStrategy

    /**
     * Use a literal / expression:
     * target.isActive = true
     * target.id = "prefix-${source.id}"
     *
     * You can keep this for future, or skip initially.
     */
    data class Constant(
        override val targetProperty: PropertyInfo,
        val expression: String
    ) : PropertyMappingStrategy

    /**
     * Target property is ignored (left to default value):
     * No argument will be passed for this property in constructor call.
     */
    data class Ignored(
        override val targetProperty: PropertyInfo
    ) : PropertyMappingStrategy
}