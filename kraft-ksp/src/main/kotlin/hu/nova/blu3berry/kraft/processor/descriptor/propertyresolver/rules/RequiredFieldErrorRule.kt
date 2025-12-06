package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.google.devtools.ksp.symbol.Nullability
import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import hu.nova.blu3berry.kraft.model.FieldOverride
import hu.nova.blu3berry.kraft.processor.util.detailedMissingMapping

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
            classLevelOverrides = ctx.classOverrides,
            configOverrides = ctx.configOverrides.map { FieldOverride(it.key, it.value) }.toList(),
            symbol = target.declaration
        )
        return null
    }
}
