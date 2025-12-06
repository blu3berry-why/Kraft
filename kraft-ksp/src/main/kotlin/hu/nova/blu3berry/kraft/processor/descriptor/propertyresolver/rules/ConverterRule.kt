package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule

class ConverterRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        val converter = ctx.converters.firstOrNull { conv ->
            conv.mapUsingTo == target.name
        } ?: return null


        val sourceName = converter.mapUsingFrom
        val sourceProp = ctx.sourceProps[sourceName] ?: run {
            ctx.logger.error(
                "Unknown source property '$sourceName' in @MapUsing. " +
                        "Available: ${ctx.sourceProps.keys}",
                converter.function
            )
            return null
        }

        return PropertyMappingStrategy.ConverterFunction(
            targetProperty = target,
            sourceProperty = sourceProp,
            converter = converter
        )
    }
}
