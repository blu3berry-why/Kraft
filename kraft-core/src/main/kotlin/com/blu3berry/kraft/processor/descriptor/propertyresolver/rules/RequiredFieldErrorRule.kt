package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.model.scan.FieldOverride
import com.blu3berry.kraft.processor.util.detailedMissingMapping

/** Terminal [MappingRule] that emits an error for required target properties that no prior rule could resolve. */
class RequiredFieldErrorRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        val required = 
            target.type.ksType.nullability == Nullability.NOT_NULL &&
            !target.hasDefault

        if (!required) return null // optional → handled elsewhere

        ctx.logger.detailedMissingMapping(
            sourceType = ctx.sourceTypeName,
            targetType = ctx.targetTypeName,
            targetProperty = target,
            sourceProperties = ctx.sourceProps,
            classLevelOverrides = ctx.classRenames,
            configOverrides = ctx.configRenames.map { FieldOverride(source = it.value, target = it.key) },
            symbol = target.declaration
        )
        return null
    }
}
