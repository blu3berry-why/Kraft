package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.RuleOutcome
import com.blu3berry.kraft.processor.util.mapConstantTypeMismatch
import com.blu3berry.kraft.processor.util.simpleRendering

/**
 * [MappingRule] for target properties filled by a `@MapConstant` member of the `@MapConfig`
 * object. The member's type must be assignable to the target's type (a non-null member may
 * fill a nullable target); otherwise it reports `KRAFT-E068` and the chain stops.
 */
class ConstantRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? = (evaluate(target, ctx) as? RuleOutcome.Resolved)?.strategy

    /** [RuleOutcome.Reported] once a type mismatch (KRAFT-E068) is reported, so no other rule claims the property. */
    override fun evaluate(target: PropertyInfo, ctx: MappingContext): RuleOutcome {
        val constant = ctx.constants.firstOrNull { it.targetPropertyName == target.name }
            ?: return RuleOutcome.Pass

        val valueType = constant.valueType.ksType
        val targetType = target.type.ksType
        if (valueType.isError || !targetType.isAssignableFrom(valueType)) {
            ctx.logger.mapConstantTypeMismatch(
                constantName = constant.displayName,
                memberType = valueType.simpleRendering(),
                target = target.name,
                targetClass = target.declaration.parentDeclaration?.simpleName?.asString()
                    ?: ctx.targetTypeName.substringAfterLast('.'),
                targetType = targetType.simpleRendering(),
                symbol = constant.declaration
            )
            return RuleOutcome.Reported
        }

        return RuleOutcome.Resolved(PropertyMappingStrategy.ConstantMember(target, constant))
    }
}
