package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.ConverterSource
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.processor.descriptor.propertyresolver.LeafOperationResolver
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule

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
 * Converters at element position (`List<A>` → `List<B>`) are applied by [ContainerRule],
 * through [LeafOperationResolver.resolveElement].
 */
class GlobalConverterRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        if (ctx.globalConverters.isEmpty()) return null

        // Direct property-type converter (e.g. Status → StatusDto).
        val directMatch = findDirectMatch(target, ctx) ?: return null
        return buildConverterFunction(target, directMatch)
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
        val key = LeafOperationResolver.converterKey(source.type, target.type) ?: return null
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

    private data class GlobalMatch(
        val effectiveSourceName: String,
        val source: PropertyInfo,
        val converter: ConverterEntry,
        val useSafeCall: Boolean = false
    )
}
