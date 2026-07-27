package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.CollectionKind
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.ConverterSource
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.collectionKindOf
import com.blu3berry.kraft.processor.util.elementTypeInfo

/**
 * Resolves a target property to a `@KraftConverter` extension function when the
 * source/target property types differ and a registered converter matches the pair.
 *
 * Runs **before** the rename rules ([ClassOverrideRule], [ConfigOverrideRule]) and
 * [DirectMatchRule] so it can claim mismatched-type pairs before those rules emit
 * a type-mismatch error.
 *
 * Returns `null` (passing the decision down the chain) when:
 * - No source property is found under the effective name (rename or same-name).
 * - The source/target types already match — let direct/rename rules win.
 * - No converter is registered for the pair — let direct/rename rules emit the
 *   type-mismatch error so the message points users at the original code.
 *
 * Also handles element-position converter pairs for `List<A>`/`Set<A>` ↔ `List<B>`/`Set<B>` when
 * the element-level converter `A → B` is registered (e.g. auto-derived enum mappers). Produces
 * a [PropertyMappingStrategy.NestedMapper] with the appropriate [CollectionKind] so the code
 * generator emits `.map { it.toB() }` / `.map { it.toB() }.toSet()` without requiring a full
 * nested [com.blu3berry.kraft.model.descriptor.MapperDescriptor] for the element type.
 */
class GlobalConverterRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        if (ctx.globalConverters.isEmpty()) return null

        // 1. Direct property-type converter (e.g. Status → StatusDto).
        val directMatch = findDirectMatch(target, ctx)
        if (directMatch != null) {
            return buildConverterFunction(target, directMatch)
        }

        // 2. Collection-element converter (e.g. List<Status> → List<StatusDto>).
        return findCollectionElementMatch(target, ctx)
    }

    // ---------- Direct match ----------

    /**
     * Resolves the source property + registered converter for [target], honouring
     * `@MapField` / `FieldMapping` renames. Returns `null` when no source matches
     * the effective name, types already match, either side is PLATFORM-typed, or
     * no converter is registered for the pair — letting the rest of the resolver
     * chain handle those cases.
     */
    private fun findDirectMatch(target: PropertyInfo, ctx: MappingContext): GlobalMatch? {
        val effectiveSourceName = ctx.classRenames[target.name]
            ?: ctx.configRenames[target.name]
            ?: target.name
        val source = ctx.sourceProps[effectiveSourceName] ?: return null
        if (source.type.ksType == target.type.ksType) return null
        val key = buildLookupKey(source, target) ?: return null
        val entry = ctx.globalConverters.lookup(key)
            ?: return findNullableLift(effectiveSourceName, source, key, ctx)
        return GlobalMatch(effectiveSourceName, source, entry)
    }

    /**
     * Nullable-scalar lift (#105): for `X? → Y?` with only the non-null bridge
     * `X → Y` registered, thread the bridge through a safe call — the scalar
     * analogue of the `?.map { it.toY() }` collections already get. A nullable
     * source with a NON-null target is deliberately not lifted: a safe call
     * yields `Y?`, which has no scalar equivalent of `?: emptyList()`.
     */
    private fun findNullableLift(
        effectiveSourceName: String,
        source: PropertyInfo,
        key: ConverterTypeKey,
        ctx: MappingContext
    ): GlobalMatch? {
        if (!key.sourceNullable || !key.targetNullable) return null
        val nonNullKey = key.copy(sourceNullable = false, targetNullable = false)
        val entry = ctx.globalConverters.lookup(nonNullKey) ?: return null
        return GlobalMatch(effectiveSourceName, source, entry, useSafeCall = true)
    }

    private fun buildConverterFunction(
        target: PropertyInfo,
        match: GlobalMatch
    ): PropertyMappingStrategy.ConverterFunction {
        val (function, isExtension) = when (val entry = match.converter) {
            // Real entries (hand-written @KraftConverter / classpath delegate)
            // expose their KSP declaration; the call is an extension iff that
            // declaration is one.
            is ConverterEntry.Real -> entry.function to (entry.function.extensionReceiver != null)
            // Synthetic entries (currently @MapEnum-derived enum mappers) are
            // always emitted as extension functions on the source type, so we
            // can fix isExtension = true and leave function = null since the
            // declaration doesn't exist yet.
            is ConverterEntry.Synthetic -> null to true
        }
        return PropertyMappingStrategy.ConverterFunction(
            targetProperty = target,
            source = ConverterSource.Property(match.source),
            converter = ConverterDescriptor(
                enclosingObject = null,
                function = function,
                callPackageName = match.converter.packageName,
                callFunctionName = match.converter.simpleName,
                isExtension = isExtension,
                sourcePropertyName = match.effectiveSourceName,
                targetPropertyName = target.name,
                sourceType = match.source.type,
                targetType = target.type,
                useSafeCall = match.useSafeCall
            )
        )
    }

    // ---------- Collection-element match ----------

    /**
     * Handles `List<SourceElem>` / `Set<SourceElem>` ↔ `List<TargetElem>` / `Set<TargetElem>`
     * when a converter for the element pair `(SourceElem → TargetElem)` is registered
     * (e.g. an auto-derived enum mapper). Produces a [PropertyMappingStrategy.NestedMapper]
     * with [CollectionKind] so [com.blu3berry.kraft.processor.codegen.generator.CtorCallBuilder]
     * emits `.map { it.toTargetElem() }` / `.map { it.toTargetElem() }.toSet()`.
     *
     * The [NestedMappingDescriptor.nestedMapperId] references the element pair; callers that
     * validate nested dependency IDs (e.g. [com.blu3berry.kraft.processor.descriptor.DescriptorBuilder])
     * must exclude IDs whose source/target are covered by an enum mapping (no `MapperDescriptor`
     * exists for enum types — only an `EnumMappingDescriptor`).
     */
    @Suppress("ReturnCount")
    private fun findCollectionElementMatch(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        val effectiveSourceName = ctx.classRenames[target.name]
            ?: ctx.configRenames[target.name]
            ?: target.name
        val source = ctx.sourceProps[effectiveSourceName] ?: return null

        val srcCollKind = collectionKindOf(source.type) ?: return null
        val tgtCollKind = collectionKindOf(target.type) ?: return null
        if (srcCollKind != tgtCollKind) return null

        val srcElement = elementTypeInfo(source.type) ?: return null
        val tgtElement = elementTypeInfo(target.type) ?: return null
        if (srcElement.qualifiedName == tgtElement.qualifiedName) return null

        val elementKey = buildElementKey(srcElement, tgtElement) ?: return null
        val elementEntry = ctx.globalConverters.lookup(elementKey) ?: return null
        // Only synthetic entries (auto-derived enum mappers) follow the
        // `to${target}` naming template that NestedMapper rendering assumes.
        // A Real @KraftConverter extension can be named anything (e.g.
        // `Foo.intoBar()`); routing it through NestedMapper would drop the
        // user's callable identity and emit a call to a non-existent
        // `it.toBar()` function. Fall through and let the rest of the
        // resolver chain (or a future Real-aware collection rule) handle it.
        if (elementEntry !is ConverterEntry.Synthetic) return null

        return PropertyMappingStrategy.NestedMapper(
            targetProperty = target,
            sourceProperty = source,
            nestedMappingDescriptor = NestedMappingDescriptor(
                nestedMapperId = MapperId(
                    sourceQualifiedName = srcElement.qualifiedName,
                    targetQualifiedName = tgtElement.qualifiedName
                ),
                sourceType = srcElement,
                targetType = tgtElement,
                collectionKind = srcCollKind
            )
        )
    }

    private fun buildElementKey(srcElement: TypeInfo, tgtElement: TypeInfo): ConverterTypeKey? {
        val srcNullable = srcElement.ksType.nullableFlag() ?: return null
        val tgtNullable = tgtElement.ksType.nullableFlag() ?: return null
        return ConverterTypeKey(
            sourceFqName = srcElement.qualifiedName,
            sourceNullable = srcNullable,
            targetFqName = tgtElement.qualifiedName,
            targetNullable = tgtNullable
        )
    }

    /**
     * Builds the lookup key for the (source, target) property pair. Platform types
     * (Java declarations without nullness metadata) are rejected so they don't
     * silently alias into NOT_NULL keys; defer to other rules in that case.
     */
    private fun buildLookupKey(source: PropertyInfo, target: PropertyInfo): ConverterTypeKey? {
        val sourceNullable = source.type.ksType.nullableFlag() ?: return null
        val targetNullable = target.type.ksType.nullableFlag() ?: return null
        return ConverterTypeKey(
            sourceFqName = source.type.qualifiedName,
            sourceNullable = sourceNullable,
            targetFqName = target.type.qualifiedName,
            targetNullable = targetNullable
        )
    }

    private data class GlobalMatch(
        val effectiveSourceName: String,
        val source: PropertyInfo,
        val converter: ConverterEntry,
        val useSafeCall: Boolean = false
    )

    private fun KSType.nullableFlag(): Boolean? = when (nullability) {
        Nullability.NULLABLE -> true
        Nullability.NOT_NULL -> false
        Nullability.PLATFORM -> null
    }
}
