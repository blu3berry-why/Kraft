package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.ContainerShape
import com.blu3berry.kraft.model.descriptor.ElementOp
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.processor.descriptor.propertyresolver.LeafOperationResolver
import com.blu3berry.kraft.processor.util.nonInjectiveMapKey
import com.blu3berry.kraft.processor.util.ContainerTypeInfo
import com.blu3berry.kraft.processor.util.isContainerSubtype
import com.blu3berry.kraft.processor.util.isLossyContainerConversion
import com.blu3berry.kraft.processor.util.lossyCollectionConversion
import com.blu3berry.kraft.processor.util.mappedContainerOf

/**
 * Builds the recursive [ContainerShape] for an auto-detected pair of mapped containers, one
 * [ContainerShape.Linear] level per nesting level, with the element operation at the leaf.
 * Used by [ContainerRule].
 */
internal object ContainerShapeBuilder {

    /** Outcome of building one container level of a shape. */
    sealed interface Built {
        data class Ok(val shape: ContainerShape) : Built
        data object Fail : Built
        data object Reported : Built
    }

    /**
     * Builds the [ContainerShape.Linear] for the mapped-container pair [srcType] -> [tgtType],
     * recursing into container elements. [Built.Fail] when some leaf has no operation (the rest of
     * the chain reports it); [Built.Reported] once a lossy conversion (KRAFT-E063) has been
     * reported at this or an inner level.
     */
    @Suppress("ReturnCount")
    fun buildLinear(srcType: TypeInfo, tgtType: TypeInfo, target: PropertyInfo, ctx: MappingContext): Built {
        val source = mappedContainerOf(srcType) ?: return Built.Fail
        val targetContainer = mappedContainerOf(tgtType) ?: return Built.Fail
        if (source.kind.isKeyed != targetContainer.kind.isKeyed) return Built.Fail
        if (source.kind.isKeyed) return buildKeyed(srcType, tgtType, source, targetContainer, target, ctx)
        val srcElement = source.element ?: return Built.Fail
        val tgtElement = targetContainer.element ?: return Built.Fail

        val element = if (srcElement.ksType == tgtElement.ksType) {
            leaf(ElementOp.Identity, srcElement, tgtElement)
        } else {
            when (val built = buildElement(srcType, tgtType, srcElement, tgtElement, target, ctx)) {
                is Built.Ok -> built.shape
                else -> return built
            }
        }

        if (isLossyContainerConversion(source.kind, targetContainer.kind)) {
            reportLossy(target, ctx, srcType, tgtType)
            return Built.Reported
        }
        return Built.Ok(
            ContainerShape.Linear(
                sourceKind = source.kind,
                targetKind = targetContainer.kind,
                element = element,
                sourceNullable = srcType.isNullable,
                targetNullable = tgtType.isNullable
            )
        )
    }

    /**
     * The [ContainerShape.Keyed] for a `Map` / `MutableMap` pair whose key or value types differ.
     * Keys map only through a one-to-one operation (see [buildKey]); values recurse through
     * [buildElement], so containers nest. [Built.Fail] for nullable values into non-null values: dropping a null value
     * would also drop its key, so KRAFT-E005 reports it. [Built.Reported] once KRAFT-E064 is
     * reported for the keys.
     */
    @Suppress("ReturnCount", "LongParameterList")
    private fun buildKeyed(
        srcType: TypeInfo,
        tgtType: TypeInfo,
        source: ContainerTypeInfo,
        targetContainer: ContainerTypeInfo,
        target: PropertyInfo,
        ctx: MappingContext
    ): Built {
        val srcKey = source.key ?: return Built.Fail
        val tgtKey = targetContainer.key ?: return Built.Fail
        val srcValue = source.element ?: return Built.Fail
        val tgtValue = targetContainer.element ?: return Built.Fail
        val key = if (srcKey.ksType == tgtKey.ksType) {
            leaf(ElementOp.Identity, srcKey, tgtKey)
        } else {
            when (val built = buildKey(srcType, tgtType, srcKey, tgtKey, target, ctx)) {
                is Built.Ok -> built.shape as ContainerShape.Leaf
                else -> return built
            }
        }

        val value: ContainerShape = if (srcValue.ksType == tgtValue.ksType) {
            leaf(ElementOp.Identity, srcValue, tgtValue)
        } else {
            if (srcValue.isNullable && !tgtValue.isNullable) return Built.Fail
            when (val built = buildElement(srcType, tgtType, srcValue, tgtValue, target, ctx)) {
                is Built.Ok -> built.shape
                else -> return built
            }
        }
        return Built.Ok(
            ContainerShape.Keyed(
                sourceKind = source.kind,
                targetKind = targetContainer.kind,
                key = key,
                value = value,
                sourceNullable = srcType.isNullable,
                targetNullable = tgtType.isNullable
            )
        )
    }

    /**
     * The key leaf for differing key types. The key operation is resolved as for an element, and
     * accepted only when it is one-to-one: a value-class wrap or unwrap, or an enum mapping whose
     * entry table maps no two entries to one (see [MapKeyInjectivity]). Any other
     * operation (a nested mapper, a `@KraftConverter`, a many-to-one `@MapEnum`, a converter from
     * another module) could merge two keys and drop an entry, so KRAFT-E064 is reported.
     * [Built.Fail] when no operation applies, and for nullable keys, which are not mapped: the
     * rest of the chain reports KRAFT-E005.
     */
    @Suppress("ReturnCount", "LongParameterList")
    private fun buildKey(
        parentSource: TypeInfo,
        parentTarget: TypeInfo,
        srcKey: TypeInfo,
        tgtKey: TypeInfo,
        target: PropertyInfo,
        ctx: MappingContext
    ): Built {
        if (srcKey.isNullable || tgtKey.isNullable) return Built.Fail
        if (mappedContainerOf(srcKey) != null || mappedContainerOf(tgtKey) != null) return Built.Fail
        val op = autoElementOp(parentSource, parentTarget, srcKey, tgtKey, ctx) ?: return Built.Fail
        val refusal = MapKeyInjectivity.refusal(op, srcKey, tgtKey, ctx) ?: return Built.Ok(leaf(op, srcKey, tgtKey))
        ctx.logger.nonInjectiveMapKey(
            propertyName = target.name,
            sourceKey = typeName(srcKey),
            targetKey = typeName(tgtKey),
            operation = refusal.operation,
            effect = refusal.effect,
            symbol = target.declaration
        )
        return Built.Reported
    }

    /** The element shape for differing element types: a nested container level, or a leaf operation. */
    @Suppress("ReturnCount")
    private fun buildElement(
        parentSource: TypeInfo,
        parentTarget: TypeInfo,
        srcElement: TypeInfo,
        tgtElement: TypeInfo,
        target: PropertyInfo,
        ctx: MappingContext
    ): Built {
        val srcInner = mappedContainerOf(srcElement)
        val tgtInner = mappedContainerOf(tgtElement)
        if (srcInner != null && tgtInner != null) {
            if (keepsInnerAsIs(srcInner, tgtInner, srcElement, tgtElement)) {
                return Built.Ok(leaf(ElementOp.Identity, srcElement, tgtElement))
            }
            return buildLinear(srcElement, tgtElement, target, ctx)
        }
        val op = autoElementOp(parentSource, parentTarget, srcElement, tgtElement, ctx) ?: return Built.Fail
        return Built.Ok(leaf(op, srcElement, tgtElement))
    }

    /** Equal inner elements and the inner target is the same kind or a supertype that keeps nullability: no copy. */
    private fun keepsInnerAsIs(
        src: ContainerTypeInfo,
        tgt: ContainerTypeInfo,
        srcType: TypeInfo,
        tgtType: TypeInfo
    ): Boolean =
        src.key?.ksType == tgt.key?.ksType &&
            src.element?.ksType == tgt.element?.ksType &&
            (src.kind == tgt.kind || isContainerSubtype(src.kind, tgt.kind)) &&
            (!srcType.isNullable || tgtType.isNullable)

    private fun leaf(op: ElementOp, src: TypeInfo, tgt: TypeInfo) =
        ContainerShape.Leaf(op = op, sourceNullable = src.isNullable, targetNullable = tgt.isNullable)

    fun reportLossy(target: PropertyInfo, ctx: MappingContext, sourceType: TypeInfo, targetType: TypeInfo) =
        ctx.logger.lossyCollectionConversion(
            propertyName = target.name,
            sourceType = typeName(sourceType),
            targetType = typeName(targetType),
            listType = "List<${typeName(mappedContainerOf(targetType)?.element ?: targetType)}>",
            symbol = target.declaration
        )

    /** The operation for differing element types, or null when none applies (the rest of the chain reports it). */
    private fun autoElementOp(
        parentSource: TypeInfo,
        parentTarget: TypeInfo,
        srcElement: TypeInfo,
        tgtElement: TypeInfo,
        ctx: MappingContext
    ): ElementOp? {
        if (srcElement.qualifiedName == tgtElement.qualifiedName) return null
        val op = LeafOperationResolver.resolveElement(srcElement, tgtElement, ctx) ?: return null
        val isValueClassOp = op is ElementOp.Wrap || op is ElementOp.Unwrap
        if (isValueClassOp && parentSource.isNullable && !parentTarget.isNullable) return null
        return op
    }

    /** A type as the diagnostics print it: simple names with type arguments, containers recursively. */
    private fun typeName(type: TypeInfo): String {
        val container = mappedContainerOf(type)
        val element = container?.element
        // A container's own nullability is not printed; an element's is.
        val key = container?.key
        if (container != null && key != null && element != null) {
            return "${container.kind.simpleName}<${typeName(key)}, ${typeName(element)}>"
        }
        if (container != null && element != null) return "${container.kind.simpleName}<${typeName(element)}>"
        return type.simpleName + if (type.isNullable) "?" else ""
    }
}
