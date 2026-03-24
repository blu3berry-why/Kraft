package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import hu.nova.blu3berry.kraft.model.ConverterSource
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
            conv.targetPropertyName == target.name
        } ?: return null

        val converterSource: ConverterSource = if (converter.sourcePropertyName == null) {
            ConverterSource.WholeObject(converter.sourceType)
        } else {
            val name = converter.sourcePropertyName
            val sourceProp = ctx.sourceProps[name] ?: run {
                ctx.logger.error(
                    "Unknown source property '$name' in @MapUsing. " +
                    "Available: ${ctx.sourceProps.keys}",
                    converter.function
                )
                return null
            }
            ConverterSource.Property(sourceProp)
        }

        return PropertyMappingStrategy.ConverterFunction(
            targetProperty = target,
            source = converterSource,
            converter = converter
        )
    }
}
