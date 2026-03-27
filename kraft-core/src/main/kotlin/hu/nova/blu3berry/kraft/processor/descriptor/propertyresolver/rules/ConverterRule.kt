package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import hu.nova.blu3berry.kraft.model.descriptor.ConverterSource
import hu.nova.blu3berry.kraft.model.descriptor.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule

/** [MappingRule] that resolves properties targeted by `@MapUsing` converter functions. */
class ConverterRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        val matching = ctx.converters.filter { conv -> conv.targetPropertyName == target.name }
        if (matching.size > 1) {
            ctx.logger.error(
                "Multiple @MapUsing converters target property '${target.name}' — only one is allowed. " +
                "Found: ${matching.map { it.function.simpleName.asString() }}",
                target.declaration
            )
            return null
        }
        val converter = matching.firstOrNull() ?: return null

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
