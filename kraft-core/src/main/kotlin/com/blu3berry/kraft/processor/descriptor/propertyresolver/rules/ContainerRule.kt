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
import com.blu3berry.kraft.processor.descriptor.propertyresolver.RuleOutcome
import com.blu3berry.kraft.processor.util.ContainerTypeInfo
import com.blu3berry.kraft.processor.util.ambiguousNestedDescriptors
import com.blu3berry.kraft.processor.util.ambiguousNestedSourceProperty
import com.blu3berry.kraft.processor.util.isContainerSubtype
import com.blu3berry.kraft.processor.util.isLegacyContainerPair
import com.blu3berry.kraft.processor.util.isLossyContainerConversion
import com.blu3berry.kraft.processor.util.isMappableClass
import com.blu3berry.kraft.processor.util.isMappedContainerPair
import com.blu3berry.kraft.processor.util.legacyContainerOf
import com.blu3berry.kraft.processor.util.lossyCollectionConversion
import com.blu3berry.kraft.processor.util.mappedContainerOf
import com.blu3berry.kraft.processor.util.nestedMappingSourceNotFound
import com.blu3berry.kraft.processor.util.nestedTypeNotMappable

/**
 * Maps container-typed properties element-wise, producing a
 * [PropertyMappingStrategy.ContainerMapping] (or a plain `Direct` when the target type is
 * already a supertype of the source): `items = this.items.map { it.toItemDto() }`.
 *
 * Handles the [com.blu3berry.kraft.processor.util.MAPPED_CONTAINER_KINDS] in the same order
 * the scalar rules run for a single property:
 * 1. `@MapNested` and 2. an explicit `@NestedMapping` are deprecated and frozen at their 0.13
 *    behaviour: they apply only to `List` / `Set` (see
 *    [com.blu3berry.kraft.processor.util.LEGACY_NESTED_KINDS]), with exactly the old output,
 *    including the `List` -> `Set` conversion through a `FieldMapping` rename. They never reach
 *    the other kinds, which go through 3.
 * 2. An explicit `@NestedMapping` whose target is the target's element type. With a
 *    `FieldMapping` override the source may be any `List` / `Set`, and the target's kind decides the
 *    result; without one, the single source property of the same kind whose element is the mapping's
 *    source is used.
 * 3. Auto-detection, for every pair of mapped containers: the element operation comes from
 *    [LeafOperationResolver.resolveElement] (or is the identity when the elements are equal), the
 *    conversion between the two kinds must keep every element (otherwise KRAFT-E063), and a target
 *    that is already a supertype of the source is assigned as is. A value-class element operation
 *    also needs a nullable source container to have a nullable target.
 *
 * Returns `null` when no element operation applies, so the rest of the chain reports the
 * mismatch as it would for a scalar.
 */
class ContainerRule : MappingRule {

    override fun tryResolve(target: PropertyInfo, ctx: MappingContext): PropertyMappingStrategy? =
        (evaluate(target, ctx) as? RuleOutcome.Resolved)?.strategy

    /** [RuleOutcome.Reported] once a lossy conversion (KRAFT-E063) is reported, so no KRAFT-E005 follows. */
    override fun evaluate(target: PropertyInfo, ctx: MappingContext): RuleOutcome {
        ctx.classNestedOverrides[target.name]?.let { mapNested ->
            val sourceProp = ctx.sourceProps[mapNested.sourceNameFor(target)] ?: return RuleOutcome.Pass
            return when {
                isLegacyContainerPair(sourceProp.type, target.type) ->
                    resolveMapNested(target, ctx, mapNested).asOutcome()
                // Frozen: @MapNested does not reach the other kinds; auto-detection maps them,
                // reading the source property @MapNested(sourceName = ...) names.
                isMappedContainerPair(sourceProp.type, target.type) -> {
                    reportMapNestedWarnings(target, ctx, mapNested)
                    resolveAuto(target, ctx, sourceProp.name)
                }
                else -> RuleOutcome.Pass
            }
        }

        val targetContainer = legacyContainerOf(target.type)
        val targetElement = targetContainer?.element
        if (targetContainer != null && targetElement != null) {
            val candidates = ctx.nestedMappings.filter { it.targetType.qualifiedName == targetElement.qualifiedName }
            if (candidates.isNotEmpty() && !hasNewKindSource(target, ctx)) {
                return resolveExplicit(target, ctx, targetContainer, candidates).asOutcome()
            }
        }

        return resolveAuto(target, ctx, sourceName = null)
    }

    private fun PropertyMappingStrategy?.asOutcome(): RuleOutcome =
        if (this == null) RuleOutcome.Pass else RuleOutcome.Resolved(this)

    /**
     * True when the source property that [target] would read is a mapped container the frozen
     * explicit `NestedMapping` path does not handle (an `Array`, `Collection`, ...), so
     * auto-detection takes the property instead.
     */
    private fun hasNewKindSource(target: PropertyInfo, ctx: MappingContext): Boolean {
        val sourceName = ctx.configRenames[target.name] ?: ctx.classRenames[target.name] ?: target.name
        val sourceType = ctx.sourceProps[sourceName]?.type ?: return false
        return mappedContainerOf(sourceType) != null && legacyContainerOf(sourceType) == null
    }

    // ---------- 1. @MapNested (frozen: List / Set only) ----------

    /**
     * Claims a `@MapNested` `List` / `Set` pair of the same kind. Returns null without reporting
     * anything when the pair is not one (or the source is missing), leaving the property to
     * [NestedRule], which skips the pairs claimed here.
     */
    private fun resolveMapNested(
        target: PropertyInfo,
        ctx: MappingContext,
        mapNested: MapNestedAnnotation
    ): PropertyMappingStrategy? {
        val sourceProp = ctx.sourceProps[mapNested.sourceNameFor(target)] ?: return null
        val source = legacyContainerOf(sourceProp.type) ?: return null
        val targetContainer = legacyContainerOf(target.type) ?: return null

        reportMapNestedWarnings(target, ctx, mapNested)

        val srcElement = source.element?.takeIf { isMappableClass(it) }
        val tgtElement = targetContainer.element?.takeIf { isMappableClass(it) }
        if (srcElement == null || tgtElement == null) {
            val unmappable = if (srcElement == null) source.element else targetContainer.element
            ctx.logger.nestedTypeNotMappable(
                propertyName = target.name,
                typeName = unmappable?.simpleName ?: sourceProp.type.simpleName,
                symbol = target.declaration
            )
            return null
        }
        val op = ElementOp.Nested(LeafOperationResolver.nestedDescriptor(srcElement, tgtElement))
        return linearMapping(target, sourceProp, source, targetContainer, op)
    }

    // ---------- 2. Explicit @NestedMapping (frozen: List / Set only) ----------

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
        val overrideContainer = legacyContainerOf(overrideProp.type)
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
            val container = legacyContainerOf(prop.type)
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
                val source = legacyContainerOf(sourceProp.type) ?: return null
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
        ctx: MappingContext,
        sourceName: String?
    ): RuleOutcome {
        val effectiveName = sourceName
            ?: ctx.classRenames[target.name]
            ?: ctx.configRenames[target.name]
            ?: target.name
        val sourceProp = ctx.sourceProps[effectiveName] ?: return RuleOutcome.Pass
        val source = mappedContainerOf(sourceProp.type) ?: return RuleOutcome.Pass
        val targetContainer = mappedContainerOf(target.type) ?: return RuleOutcome.Pass
        // Identical types are a direct copy, which DirectMatchRule handles.
        if (sourceProp.type.ksType == target.type.ksType) return RuleOutcome.Pass
        val srcElement = source.element ?: return RuleOutcome.Pass
        val tgtElement = targetContainer.element ?: return RuleOutcome.Pass

        val sameElements = srcElement.ksType == tgtElement.ksType
        val op = if (sameElements) {
            ElementOp.Identity
        } else {
            autoElementOp(sourceProp, target, srcElement, tgtElement, ctx) ?: return RuleOutcome.Pass
        }

        if (isLossyContainerConversion(source.kind, targetContainer.kind)) {
            reportLossy(target, ctx, source to srcElement, targetContainer to tgtElement)
            return RuleOutcome.Reported
        }
        if (sameElements) return resolveSameElements(sourceProp, target, source, targetContainer, op).asOutcome()
        return linearMapping(target, sourceProp, source, targetContainer, op).asOutcome()
    }

    /** Equal element types: a plain assignment when the target is a supertype, else a collection-type conversion. */
    private fun resolveSameElements(
        sourceProp: PropertyInfo,
        target: PropertyInfo,
        source: ContainerTypeInfo,
        targetContainer: ContainerTypeInfo,
        op: ElementOp
    ): PropertyMappingStrategy? {
        val nullabilityKept = !sourceProp.type.isNullable || target.type.isNullable
        if (isContainerSubtype(source.kind, targetContainer.kind) && nullabilityKept) {
            return PropertyMappingStrategy.Direct(sourceProperty = sourceProp, targetProperty = target)
        }
        // Only the container's nullability differs: not a collection-type conversion.
        if (source.kind == targetContainer.kind) return null
        return linearMapping(target, sourceProp, source, targetContainer, op)
    }

    private fun reportLossy(
        target: PropertyInfo,
        ctx: MappingContext,
        source: Pair<ContainerTypeInfo, TypeInfo>,
        targetSide: Pair<ContainerTypeInfo, TypeInfo>
    ) = ctx.logger.lossyCollectionConversion(
        propertyName = target.name,
        sourceType = containerTypeName(source.first, source.second),
        targetType = containerTypeName(targetSide.first, targetSide.second),
        listType = "List<${elementName(targetSide.second)}>",
        symbol = target.declaration
    )

    /** The operation for differing element types, or null when none applies (the rest of the chain reports it). */
    private fun autoElementOp(
        sourceProp: PropertyInfo,
        target: PropertyInfo,
        srcElement: TypeInfo,
        tgtElement: TypeInfo,
        ctx: MappingContext
    ): ElementOp? {
        if (srcElement.qualifiedName == tgtElement.qualifiedName) return null
        val op = LeafOperationResolver.resolveElement(srcElement, tgtElement, ctx) ?: return null
        val isValueClassOp = op is ElementOp.Wrap || op is ElementOp.Unwrap
        if (isValueClassOp && sourceProp.type.isNullable && !target.type.isNullable) return null
        return op
    }

    private fun elementName(element: TypeInfo): String = element.simpleName + if (element.isNullable) "?" else ""

    private fun containerTypeName(container: ContainerTypeInfo, element: TypeInfo): String =
        "${container.kind.simpleName}<${elementName(element)}>"

    // ---------- Shared helpers ----------

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
