package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule

/**
 * Returns [PropertyMappingStrategy.Ignored] if the target property should be skipped.
 *
 * Two sources are merged into [MappingContext.classIgnoredProperties] before the rule
 * is invoked:
 *  - `@MapIgnore` on the `@MapFrom`/`@MapTo` annotated class.
 *  - `@IgnoreField` entries in `@MapConfig.ignoredMappings`, filtered to the current
 *    mapping direction by [hu.nova.blu3berry.kraft.processor.descriptor.ClassDescriptorBuilder].
 */
class IgnoreRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        return if (target.name in ctx.classIgnoredProperties)
            PropertyMappingStrategy.Ignored(target)
        else
            null
    }
}
