package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.ContainerShape
import com.blu3berry.kraft.model.descriptor.ElementOp
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.processor.descriptor.propertyresolver.LeafOperationResolver
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
        if (container != null && element != null) return "${container.kind.simpleName}<${typeName(element)}>"
        return type.simpleName + if (type.isNullable) "?" else ""
    }
}
