package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import hu.nova.blu3berry.kraft.processor.util.detailedTypeMismatch

class DirectMatchRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        val source = ctx.sourceProps[target.name] ?: return null

        if (source.type.ksType != target.type.ksType) {
            ctx.logger.detailedTypeMismatch(
                sourceType = ctx.sourceTypeName,
                targetType = ctx.targetTypeName,
                sourceProperty = source,
                targetProperty = target,
                symbol = target.declaration
            )
            return null
        }

        return PropertyMappingStrategy.Direct(
            sourceProperty = source,
            targetProperty = target
        )
    }
}
