package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule

class IgnoreRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        if (!ctx.configOverrides.containsKey(target.name) &&
            ctx.configOverrides[target.name] == "__ignore") {
            return PropertyMappingStrategy.Ignored(target)
        }

        return null
    }
}
