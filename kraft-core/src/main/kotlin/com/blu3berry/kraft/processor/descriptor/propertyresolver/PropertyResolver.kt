package com.blu3berry.kraft.processor.descriptor.propertyresolver

import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.ConverterSource
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.diagnostics.ErrorTrackingLogger
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.isKraftSuppressed
import com.blu3berry.kraft.processor.util.nullableTargetDefaultedToNull
import com.blu3berry.kraft.processor.diagnostics.MutedKspLogger
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ClassOverrideRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ConfigOverrideRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ContainerRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ConverterRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.DirectMatchRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.GlobalConverterRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.IgnoreRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.NestedRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.RequiredFieldErrorRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ValueClassRule

/**
 * Coordinates an ordered chain of [MappingRule] instances to resolve each target property to a [PropertyMappingStrategy].
 *
 * @param rules ordered list of rules to evaluate; the first rule that returns non-null wins.
 */
class PropertyResolver(private val rules: List<MappingRule> = default()) {

    /**
     * Resolves [target] by running the rule chain, then, when no rule produced a strategy and
     * none reported an error for this property, falls back to [missingSourceFallback].
     * Returns null only when the property cannot be mapped; the caller then drops the mapper.
     */
    fun resolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        val tracker = ErrorTrackingLogger(ctx.logger)
        val trackedCtx = ctx.copy(logger = tracker)

        for (rule in rules) {
            val result = when (val outcome = rule.evaluate(target, trackedCtx)) {
                RuleOutcome.Pass -> continue
                RuleOutcome.Reported -> return null
                is RuleOutcome.Resolved -> outcome.strategy
            }
            if (rule is ConverterRule &&
                result is PropertyMappingStrategy.ConverterFunction &&
                result.source is ConverterSource.Property
            ) {
                return result.copy(shadowedNative = probeShadowedNative(target, ctx, result.source))
            }
            return result
        }

        if (tracker.errorCount > 0) return null
        return missingSourceFallback(target, trackedCtx)
    }

    /**
     * What to generate for a target property no rule resolved and no rule reported an error for:
     * a defaulted property is left out of the constructor call so Kotlin applies its default,
     * and a nullable property without a default receives `null` plus `KRAFT-W009`, unless
     * suppressed. Anything else cannot be mapped.
     */
    private fun missingSourceFallback(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? = when {
        target.hasDefault -> PropertyMappingStrategy.Ignored(target)
        target.type.ksType.nullability != Nullability.NOT_NULL -> {
            if (!target.isW009Suppressed(ctx)) {
                ctx.logger.nullableTargetDefaultedToNull(ctx.targetTypeName, target, target.declaration)
            }
            PropertyMappingStrategy.Constant(target, "null")
        }
        else -> null
    }

    /**
     * `KRAFT-W009` is suppressed by `@Suppress("KRAFT-W009")` on the target property, on its
     * constructor parameter (where Kotlin puts an annotation on a constructor `val` without a
     * use-site target), or on the mapping declaration. Three single-declaration checks, so
     * [isKraftSuppressed] keeps its "only this declaration" contract.
     */
    private fun PropertyInfo.isW009Suppressed(ctx: MappingContext): Boolean {
        val w009 = KraftDiagnostic.NULLABLE_TARGET_DEFAULTED_TO_NULL
        return declaration.isKraftSuppressed(w009) ||
            parameter?.isKraftSuppressed(w009) == true ||
            ctx.mappingDeclaration?.isKraftSuppressed(w009) == true
    }

    /**
     * Shadow probe for `KRAFT-W001`: what would Kraft have generated for [target] if the
     * property-source `@MapUsing` had not claimed it? Runs every rule except [ConverterRule]
     * and [RequiredFieldErrorRule], in order, and returns the first non-null strategy, or
     * null when there is none, when the property would be ignored, or when the native
     * strategy reads a different source property than the converter does (then the
     * converter is not a duplicate). A global `@KraftConverter` applied directly to the same
     * scalar pair is also not a duplicate: a `@MapUsing` there is a deliberate per-mapper
     * override. Element-wise (`ContainerMapping`) and null-lifted (`useSafeCall`) global-converter
     * shadows are still reported.
     *
     * The probe must be inert. Audit of the rules it runs (agent-readable-diagnostics 4.1):
     * - [ContainerRule], [NestedRule], [ValueClassRule], [GlobalConverterRule] (and the other
     *   rules) never mutate [MappingContext]: it is an immutable data class holding read-only
     *   maps and lists, and the rules only read it. [ContainerRule] and [NestedRule] synthesise
     *   `NestedMappingDescriptor`s inside the strategy they return; nested mappers are only
     *   registered by `DescriptorBuilder` from the *top-level* strategies, never from
     *   [PropertyMappingStrategy.ConverterFunction.shadowedNative].
     * - The rules log through `ctx.logger`, so the probe runs on a context copy whose logger
     *   is [MutedKspLogger].
     * - Shared state found: [GlobalConverterRule] and [ContainerRule] (for element converters)
     *   can reach the classpath fallback of
     *   `GlobalConverterRegistry`, which memoizes results and reports classpath ambiguity
     *   through the scanner's own logger. The copy therefore also carries
     *   `GlobalConverterRegistry.silent()`, whose fallback neither memoizes nor reports.
     */
    private fun probeShadowedNative(
        target: PropertyInfo,
        ctx: MappingContext,
        converterSource: ConverterSource.Property
    ): PropertyMappingStrategy? {
        val probeCtx = ctx.copy(
            logger = MutedKspLogger,
            globalConverters = ctx.globalConverters.silent()
        )
        val native = rules
            .asSequence()
            .filterNot { it is ConverterRule || it is RequiredFieldErrorRule }
            .map { it.evaluate(target, probeCtx) }
            .firstOrNull { it !is RuleOutcome.Pass }
            .let { it as? RuleOutcome.Resolved }
            ?.strategy
            ?: return null
        if (native is PropertyMappingStrategy.Ignored) return null
        if (native.isDirectGlobalConverter()) return null
        return native.takeIf { it.nativeSourceName() == converterSource.info.name }
    }

    /**
     * True for a global `@KraftConverter` call on the scalar pair itself. [ConverterRule] is
     * excluded from the probe, so any [PropertyMappingStrategy.ConverterFunction] here comes
     * from [GlobalConverterRule]'s direct match; an element-wise converter is a
     * `ContainerMapping` from [ContainerRule] instead. `useSafeCall` marks the null-lift through `?.`.
     */
    private fun PropertyMappingStrategy.isDirectGlobalConverter(): Boolean =
        this is PropertyMappingStrategy.ConverterFunction && !converter.useSafeCall

    /** The source property a native strategy reads, or null when it reads none. */
    private fun PropertyMappingStrategy.nativeSourceName(): String? = when (this) {
        is PropertyMappingStrategy.Direct -> sourceProperty.name
        is PropertyMappingStrategy.Renamed -> sourceProperty.name
        is PropertyMappingStrategy.NestedMapper -> sourceProperty.name
        is PropertyMappingStrategy.ValueClassWrap -> sourceProperty.name
        is PropertyMappingStrategy.ValueClassUnwrap -> sourceProperty.name
        is PropertyMappingStrategy.ContainerMapping -> sourceProperty.name
        is PropertyMappingStrategy.ConverterFunction -> (source as? ConverterSource.Property)?.info?.name
        is PropertyMappingStrategy.Constant -> null
        is PropertyMappingStrategy.Ignored -> null
    }

    companion object {
        /**
         * Rule evaluation order — the first rule that returns non-null wins.
         * DO NOT reorder without understanding the constraints below.
         *
         * 1. [ConverterRule]         — explicit @MapUsing overrides must be checked before any
         *                              name-based rule; otherwise DirectMatchRule could silently
         *                              win on a same-named property and skip the converter.
         *
         * 2. [IgnoreRule]            — checks ignoredProperties (populated from @MapIgnore
         *                              and @MapIgnoreField); must run before ConfigOverrideRule so
         *                              an ignored property is never mistakenly treated as a
         *                              source-name override.
         *
         * 3. [ContainerRule]         — element-wise mapping of List/Set properties (nested
         *                              mappers, enum mappers, value-class wrap/unwrap per element),
         *                              including @MapNested and explicit @NestedMapping on
         *                              collections. Before NestedRule so container properties are
         *                              resolved in one place; NestedRule, GlobalConverterRule and
         *                              ValueClassRule only see scalar properties after it.
         *
         * 3b. [NestedRule]           — must claim a property whose type matches a nested mapping
         *                              before DirectMatchRule tries to copy the object directly
         *                              by name, which would produce a type-mismatch error.
         *
         * 4. [GlobalConverterRule]   — same-module @KraftConverter lookup; runs before the rename
         *                              and direct-match rules so a registered (src, tgt) converter
         *                              can claim a mismatched-type pair before those rules emit a
         *                              type-mismatch error. Returns null when types already match
         *                              (rename/direct rules win) or no converter is registered
         *                              (existing rules emit the error).
         *
         * 4b. [ValueClassRule]       — auto wrap/unwrap of value classes against their underlying
         *                              type. After
         *                              GlobalConverterRule so a registered converter for the pair
         *                              wins; before the rename/direct rules for the same reason
         *                              GlobalConverterRule is — they would report a type mismatch.
         *
         * 5. [ClassOverrideRule]     — annotation-level renames (@MapField); evaluated before
         *                              ConfigOverrideRule so annotation-level intent wins when
         *                              both sources declare an override for the same property.
         *
         * 6. [ConfigOverrideRule]    — config-object-level renames; must precede DirectMatchRule
         *                              so explicitly remapped fields are not also matched by name.
         *
         * 7. [DirectMatchRule]       — automatic name + type match; runs after all explicit rules
         *                              have had the opportunity to claim the property.
         *
         * 8. [RequiredFieldErrorRule] — catch-all sentinel; MUST be last. Emits a KSP error for
         *                              any required (non-null, no default) property that no earlier
         *                              rule could resolve. Moving it earlier would silence valid
         *                              matches that appear after it in the list.
         */
        fun default() = listOf(
                ConverterRule(),
                IgnoreRule(),
                ContainerRule(),
                NestedRule(),
                GlobalConverterRule(),
                ValueClassRule(),
                ClassOverrideRule(),
                ConfigOverrideRule(),
                DirectMatchRule(),
                RequiredFieldErrorRule()
            )
    }
}
