package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.CollectionKind
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.collectionKindOf
import com.blu3berry.kraft.processor.util.elementTypeInfo
import com.blu3berry.kraft.processor.util.sameTypeIgnoringNullability
import com.blu3berry.kraft.processor.util.valueClassInfo

/**
 * Auto-wraps and unwraps value classes against their underlying type:
 * `UserId → String` emits `this.id.value`, `String → UserId` emits `UserId(this.id)`.
 * `List` / `Set` properties are handled element-wise when both sides use the same
 * collection kind and non-null elements.
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

        val srcKind = collectionKindOf(source.type)
        val tgtKind = collectionKindOf(target.type)
        if (srcKind != null || tgtKind != null) {
            return resolveCollection(target, source, srcKind, tgtKind)
        }
        return resolveScalar(target, source)
    }

    private fun resolveScalar(target: PropertyInfo, source: PropertyInfo): PropertyMappingStrategy? {
        val srcNullable = source.type.isNullable
        val tgtNullable = target.type.isNullable

        valueClassInfo(source.type)?.let { underlying ->
            if (!sameTypeIgnoringNullability(underlying.type, target.type.ksType)) return@let
            val resultNullable = srcNullable || underlying.type.isMarkedNullable
            if (resultNullable && !tgtNullable) return null
            return PropertyMappingStrategy.ValueClassUnwrap(
                targetProperty = target,
                sourceProperty = source,
                underlyingPropertyName = underlying.propertyName,
                safeCall = srcNullable
            )
        }

        valueClassInfo(target.type)?.let { underlying ->
            if (!sameTypeIgnoringNullability(underlying.type, source.type.ksType)) return@let
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

    @Suppress("ReturnCount")
    private fun resolveCollection(
        target: PropertyInfo,
        source: PropertyInfo,
        srcKind: CollectionKind?,
        tgtKind: CollectionKind?
    ): PropertyMappingStrategy? {
        if (srcKind == null || srcKind != tgtKind) return null
        if (source.type.isNullable && !target.type.isNullable) return null
        val srcElement = elementTypeInfo(source.type)?.takeUnless { it.isNullable } ?: return null
        val tgtElement = elementTypeInfo(target.type)?.takeUnless { it.isNullable } ?: return null
        val safeCall = source.type.isNullable

        unwrappable(srcElement, tgtElement)?.let { propertyName ->
            return PropertyMappingStrategy.ValueClassUnwrap(
                targetProperty = target,
                sourceProperty = source,
                underlyingPropertyName = propertyName,
                safeCall = safeCall,
                collectionKind = srcKind
            )
        }
        if (wrappable(srcElement, tgtElement)) {
            return PropertyMappingStrategy.ValueClassWrap(
                targetProperty = target,
                sourceProperty = source,
                valueClass = tgtElement,
                safeCall = safeCall,
                collectionKind = srcKind
            )
        }
        return null
    }

    private fun unwrappable(srcElement: TypeInfo, tgtElement: TypeInfo): String? {
        val underlying = valueClassInfo(srcElement) ?: return null
        if (underlying.type.isMarkedNullable) return null
        if (!sameTypeIgnoringNullability(underlying.type, tgtElement.ksType)) return null
        return underlying.propertyName
    }

    private fun wrappable(srcElement: TypeInfo, tgtElement: TypeInfo): Boolean {
        val underlying = valueClassInfo(tgtElement) ?: return false
        return sameTypeIgnoringNullability(underlying.type, srcElement.ksType)
    }
}
