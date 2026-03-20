import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule

class NestedRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        // Find nested mapping whose TARGET TYPE matches the property
        val nested = ctx.nestedMappings.firstOrNull { nm ->
            nm.targetType.className == target.type.className
        } ?: return null

        // Find matching SOURCE PROPERTY in parent mapper
        val sourceProp = ctx.sourceProps.values.firstOrNull { prop ->
            prop.type.className == nested.sourceType.className
        } ?: return null

        return PropertyMappingStrategy.NestedMapper(
            targetProperty = target,
            sourceProperty = sourceProp,
            nestedMappingDescriptor = nested
        )
    }
}
