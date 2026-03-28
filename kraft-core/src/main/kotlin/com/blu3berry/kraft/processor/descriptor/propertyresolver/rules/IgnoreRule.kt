package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.ignoredRequiredProperty

/**
 * Returns [PropertyMappingStrategy.Ignored] if the target property should be skipped.
 *
 * Two sources are merged into [MappingContext.ignoredProperties] before the rule
 * is invoked:
 *  - `@MapIgnore` on the `@MapFrom`/`@MapTo` annotated class.
 *  - `@MapIgnoreField` entries in `@MapConfig.ignoredMappings`, filtered to the current
 *    mapping direction by [com.blu3berry.kraft.processor.descriptor.ClassDescriptorBuilder].
 */
class IgnoreRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        if (target.name !in ctx.ignoredProperties) return null

        if (!target.hasDefault) {
            ctx.logger.ignoredRequiredProperty(
                targetType = ctx.targetTypeName,
                propertyName = target.name,
                symbol = target.declaration
            )
        }

        return PropertyMappingStrategy.Ignored(target)
    }
}
