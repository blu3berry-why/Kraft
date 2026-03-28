package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.detailedTypeMismatch

/** [MappingRule] for direct same-name, same-type property assignments. */
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
