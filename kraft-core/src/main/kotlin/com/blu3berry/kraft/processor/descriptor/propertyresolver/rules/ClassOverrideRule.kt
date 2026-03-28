package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.detailedTypeMismatch
import com.blu3berry.kraft.processor.util.invalidMapFieldOverride

class ClassOverrideRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        val sourceName = ctx.classRenames[target.name] ?: return null
        val sourceProp = ctx.sourceProps[sourceName] ?: run {
            ctx.logger.invalidMapFieldOverride(
                ctx.sourceTypeName,
                target.name,
                sourceName,
                ctx.sourceProps,
                target.declaration
            )
            return null
        }

        return directIfTypesMatch(sourceProp, target, ctx)
    }

    private fun directIfTypesMatch(
        source: PropertyInfo,
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        if (source.type.ksType != target.type.ksType) {
            ctx.logger.detailedTypeMismatch(
                ctx.sourceTypeName,
                ctx.targetTypeName,
                source,
                target,
                target.declaration
            )
            return null
        }

        return PropertyMappingStrategy.Renamed(
            targetProperty = target,
            sourceProperty = source
        )
    }
}
