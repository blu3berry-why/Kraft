package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import hu.nova.blu3berry.kraft.processor.util.detailedTypeMismatch

class ConfigOverrideRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        val sourceName = ctx.configOverrides[target.name] ?: return null
        val sourceProp = ctx.sourceProps[sourceName] ?: run {
            ctx.logger.error(
                "Config override refers to unknown property '$sourceName'. " +
                        "Available: ${ctx.sourceProps.keys}",
                target.declaration
            )
            return null
        }

        return if (sourceProp.type.ksType == target.type.ksType) {
            PropertyMappingStrategy.Renamed(
                targetProperty = target,
                sourceProperty = sourceProp
            )
        } else {
            ctx.logger.detailedTypeMismatch(
                ctx.sourceTypeName,
                ctx.targetTypeName,
                sourceProp,
                target,
                target.declaration
            )
            null
        }
    }
}
