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

        ctx.logger.warn("""
    ****************************************************
    *                  ⚠  WARNING                      *
    ****************************************************
    
    The current version of the AutoMapper does NOT
    generate nested mappings.
    
    Please ensure that a mapper between the following
    types exists:
    
        * ${sourceProp.type.className} -> ${target.type.className}
    
    If no mapping exists, you can add the appropriate
    annotations to the target object.
    The mapping will then be generated automatically.
    
    Without this mapping, nested objects will NOT
    be mapped correctly.
    
    ****************************************************
        """)

        return PropertyMappingStrategy.NestedMapper(
            targetProperty = target,
            sourceProperty = sourceProp,
            nestedMappingDescriptor = nested
        )
    }
}
