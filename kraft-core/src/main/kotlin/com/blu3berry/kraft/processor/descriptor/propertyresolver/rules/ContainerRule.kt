package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.ContainerShape
import com.blu3berry.kraft.model.descriptor.ElementOp
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.model.scan.MapNestedAnnotation
import com.blu3berry.kraft.processor.descriptor.propertyresolver.LeafOperationResolver
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.ContainerTypeInfo
import com.blu3berry.kraft.processor.util.ambiguousNestedDescriptors
import com.blu3berry.kraft.processor.util.ambiguousNestedSourceProperty
import com.blu3berry.kraft.processor.util.isMappableClass
import com.blu3berry.kraft.processor.util.isMappedContainerPair
import com.blu3berry.kraft.processor.util.mappedContainerOf
import com.blu3berry.kraft.processor.util.nestedMappingSourceNotFound
import com.blu3berry.kraft.processor.util.nestedTypeNotMappable

/**
 * Maps container-typed properties element-wise, producing a
 * [PropertyMappingStrategy.ContainerMapping]: `items = this.items.map { it.toItemDto() }`.
 *
 * Handles the [com.blu3berry.kraft.processor.util.MAPPED_CONTAINER_KINDS] in the same order
 * the scalar rules run for a single property:
 * 1. `@MapNested` on the target, when both sides are containers of the same kind.
 * 2. An explicit `@NestedMapping` whose target is the target's element type. With a
 *    `FieldMapping` override the source may be any mapped container, and the target's kind
 *    decides the result; without one, the single source property of the same kind whose
 *    element is the mapping's source is used.
 * 3. Otherwise, same-kind containers whose elements differ, with the element operation from
 *    [LeafOperationResolver.resolveElement]. A value-class element operation also needs a
 *    nullable source container to have a nullable target.
 *
 * Returns `null` when no element operation applies, so the rest of the chain reports the
 * mismatch as it would for a scalar.
 */
class ContainerRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        ctx.classNestedOverrides[target.name]?.let { return resolveMapNested(target, ctx, it) }

        val targetContainer = mappedContainerOf(target.type)
        val targetElement = targetContainer?.element
        if (targetContainer != null && targetElement != null) {
            val candidates = ctx.nestedMappings.filter { it.targetType.qualifiedName == targetElement.qualifiedName }
            if (candidates.isNotEmpty()) return resolveExplicit(target, ctx, targetContainer, candidates)
        }

        return resolveAuto(target, ctx)
    }

    // ---------- 1. @MapNested ----------

    /**
     * Claims a `@MapNested` container pair. Returns null without reporting anything when the
     * pair is not a same-kind container pair (or the source is missing), leaving the
     * property to [NestedRule], which skips the pairs claimed here.
     */
    private fun resolveMapNested(
        target: PropertyInfo,
        ctx: MappingContext,
        mapNested: MapNestedAnnotation
    ): PropertyMappingStrategy? {
        val sourceProp = ctx.sourceProps[mapNested.sourceNameFor(target)] ?: return null
        val (source, targetContainer) = containerPair(sourceProp, target) ?: return null

        reportMapNestedWarnings(target, ctx, mapNested)

        val srcElement = source.element?.takeIf { isMappableClass(it) }
        val tgtElement = targetContainer.element?.takeIf { isMappableClass(it) }
        if (srcElement == null || tgtElement == null) {
            val typeName = if (srcElement == null) sourceProp.type.ksType.toString() else target.type.ksType.toString()
            ctx.logger.nestedTypeNotMappable(
                propertyName = target.name,
                typeName = typeName,
                symbol = target.declaration
            )
            return null
        }
        val op = ElementOp.Nested(LeafOperationResolver.nestedDescriptor(srcElement, tgtElement))
        return linearMapping(target, sourceProp, source, targetContainer, op)
    }

    // ---------- 2. Explicit @NestedMapping ----------

    private fun resolveExplicit(
        target: PropertyInfo,
        ctx: MappingContext,
        targetContainer: ContainerTypeInfo,
        candidates: List<NestedMappingDescriptor>
    ): PropertyMappingStrategy? {
        val explicitSourceName = ctx.configRenames[target.name]
            ?: return resolveExplicitWithoutOverride(target, ctx, targetContainer, candidates)

        val overrideProp = ctx.sourceProps[explicitSourceName] ?: run {
            ctx.logger.nestedMappingSourceNotFound(
                sourceTypeName = ctx.sourceTypeName,
                nestedSourceType = candidates.first().sourceType.simpleName,
                nestedTargetType = target.type.simpleName,
                symbol = target.declaration
            )
            return null
        }
        val overrideContainer = mappedContainerOf(overrideProp.type)
        val overrideType = overrideContainer?.element ?: overrideProp.type
        val nestedCandidates = candidates.filter { it.sourceType.qualifiedName == overrideType.qualifiedName }

        return when {
            nestedCandidates.isEmpty() -> {
                ctx.logger.nestedMappingSourceNotFound(
                    sourceTypeName = ctx.sourceTypeName,
                    nestedSourceType = overrideProp.type.simpleName,
                    nestedTargetType = target.type.simpleName,
                    symbol = target.declaration
                )
                null
            }
            nestedCandidates.size > 1 -> {
                ctx.logger.ambiguousNestedDescriptors(
                    targetTypeName = target.type.simpleName,
                    matchCount = nestedCandidates.size,
                    symbol = target.declaration
                )
                null
            }
            // A scalar source has no elements to map into the target container.
            overrideContainer == null -> null
            else -> linearMapping(
                target, overrideProp, overrideContainer, targetContainer,
                ElementOp.Nested(nestedCandidates.single())
            )
        }
    }

    private fun resolveExplicitWithoutOverride(
        target: PropertyInfo,
        ctx: MappingContext,
        targetContainer: ContainerTypeInfo,
        candidates: List<NestedMappingDescriptor>
    ): PropertyMappingStrategy? {
        if (candidates.size > 1) {
            ctx.logger.ambiguousNestedDescriptors(
                targetTypeName = target.type.simpleName,
                matchCount = candidates.size,
                symbol = target.declaration
            )
            return null
        }
        val nested = candidates.single()
        val sourceCandidates = ctx.sourceProps.values.filter { prop ->
            val container = mappedContainerOf(prop.type)
            container?.kind == targetContainer.kind &&
                container.element?.qualifiedName == nested.sourceType.qualifiedName
        }

        return when (sourceCandidates.size) {
            0 -> {
                ctx.logger.nestedMappingSourceNotFound(
                    sourceTypeName = ctx.sourceTypeName,
                    nestedSourceType = nested.sourceType.simpleName,
                    nestedTargetType = nested.targetType.simpleName,
                    symbol = target.declaration
                )
                null
            }
            1 -> {
                val sourceProp = sourceCandidates.single()
                val source = mappedContainerOf(sourceProp.type) ?: return null
                linearMapping(target, sourceProp, source, targetContainer, ElementOp.Nested(nested))
            }
            else -> {
                ctx.logger.ambiguousNestedSourceProperty(
                    sourceTypeName = ctx.sourceTypeName,
                    nestedSourceType = nested.sourceType.simpleName,
                    matchingProps = sourceCandidates.map { it.name }.sorted(),
                    symbol = target.declaration
                )
                null
            }
        }
    }

    // ---------- 3. Auto-detected element operation ----------

    @Suppress("ReturnCount")
    private fun resolveAuto(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        val sourceName = ctx.classRenames[target.name]
            ?: ctx.configRenames[target.name]
            ?: target.name
        val sourceProp = ctx.sourceProps[sourceName] ?: return null
        val (source, targetContainer) = containerPair(sourceProp, target) ?: return null
        val srcElement = source.element ?: return null
        val tgtElement = targetContainer.element ?: return null
        if (srcElement.qualifiedName == tgtElement.qualifiedName) return null

        val op = LeafOperationResolver.resolveElement(srcElement, tgtElement, ctx) ?: return null
        val isValueClassOp = op is ElementOp.Wrap || op is ElementOp.Unwrap
        if (isValueClassOp && sourceProp.type.isNullable && !target.type.isNullable) return null
        return linearMapping(target, sourceProp, source, targetContainer, op)
    }

    // ---------- Shared helpers ----------

    /** The two sides' containers when they are a same-kind mapped container pair. */
    private fun containerPair(
        sourceProp: PropertyInfo,
        target: PropertyInfo
    ): Pair<ContainerTypeInfo, ContainerTypeInfo>? {
        if (!isMappedContainerPair(sourceProp.type, target.type)) return null
        val source = mappedContainerOf(sourceProp.type) ?: return null
        val targetContainer = mappedContainerOf(target.type) ?: return null
        return source to targetContainer
    }

    private fun linearMapping(
        target: PropertyInfo,
        sourceProp: PropertyInfo,
        source: ContainerTypeInfo,
        targetContainer: ContainerTypeInfo,
        op: ElementOp
    ): PropertyMappingStrategy.ContainerMapping? {
        val srcElement: TypeInfo = source.element ?: return null
        val tgtElement: TypeInfo = targetContainer.element ?: return null
        return PropertyMappingStrategy.ContainerMapping(
            targetProperty = target,
            sourceProperty = sourceProp,
            shape = ContainerShape.Linear(
                sourceKind = source.kind,
                targetKind = targetContainer.kind,
                element = ContainerShape.Leaf(
                    op = op,
                    sourceNullable = srcElement.isNullable,
                    targetNullable = tgtElement.isNullable
                ),
                sourceNullable = sourceProp.type.isNullable,
                targetNullable = target.type.isNullable
            )
        )
    }
}
