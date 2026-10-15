package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.LeafOperationResolver
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.isMappedContainerPair

/**
 * Auto-wraps and unwraps value classes against their underlying type:
 * `UserId → String` emits `this.id.value`, `String → UserId` emits `UserId(this.id)`.
 * Value-class elements of `List` / `Set` properties are handled by [ContainerRule].
 *
 * Runs after [GlobalConverterRule] so a registered `@KraftConverter` for the pair wins.
 * Honours `@MapField` / `FieldMapping` renames like the other auto rules.
 *
 * Returns `null` (deferring to the rest of the chain, which reports the type mismatch) when
 * the result would be nullable but the target is not — Kraft never invents a value.
 */
class ValueClassRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        val sourceName = ctx.classRenames[target.name]
            ?: ctx.configRenames[target.name]
            ?: target.name
        val source = ctx.sourceProps[sourceName] ?: return null
        if (source.type.ksType == target.type.ksType) return null

        // Container-to-container pairs are ContainerRule's. A value class may still wrap a whole
        // container (`value class Attrs(val value: Map<String, String>)`), as in 0.13.
        if (isMappedContainerPair(source.type, target.type)) return null
        return resolveScalar(target, source)
    }

    private fun resolveScalar(target: PropertyInfo, source: PropertyInfo): PropertyMappingStrategy? {
        val srcNullable = source.type.isNullable
        val tgtNullable = target.type.isNullable

        LeafOperationResolver.unwrapOf(source.type, target.type)?.let { underlying ->
            val resultNullable = srcNullable || underlying.type.isMarkedNullable
            if (resultNullable && !tgtNullable) return null
            return PropertyMappingStrategy.ValueClassUnwrap(
                targetProperty = target,
                sourceProperty = source,
                underlyingPropertyName = underlying.propertyName,
                safeCall = srcNullable
            )
        }

        LeafOperationResolver.wrapOf(source.type, target.type)?.let { underlying ->
            val acceptsNull = underlying.type.isMarkedNullable
            // A nullable source into a non-null underlying needs a safe call, which yields `V?`.
            val safeCall = srcNullable && !acceptsNull
            if (safeCall && !tgtNullable) return null
            return PropertyMappingStrategy.ValueClassWrap(
                targetProperty = target,
                sourceProperty = source,
                valueClass = target.type,
                safeCall = safeCall
            )
        }
        return null
    }
}
