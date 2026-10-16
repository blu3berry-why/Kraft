package com.blu3berry.kraft.processor.descriptor.propertyresolver

import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy

/**
 * One link of the [PropertyResolver] chain.
 *
 * A rule that only ever answers "this strategy" or "not mine" implements [tryResolve]. A rule
 * that can also fail a property it owns, after reporting the error itself, overrides [evaluate]
 * to return [RuleOutcome.Reported], so the chain stops instead of letting a later rule add a
 * second, follow-on error for the same cause.
 */
fun interface MappingRule {
    fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy?

    /** The rule's full outcome for [target]; by default a [tryResolve] result or [RuleOutcome.Pass]. */
    fun evaluate(target: PropertyInfo, ctx: MappingContext): RuleOutcome =
        tryResolve(target, ctx)?.let { RuleOutcome.Resolved(it) } ?: RuleOutcome.Pass
}

/** What one [MappingRule] decided for a target property. */
sealed interface RuleOutcome {
    /** The rule does not apply; the next rule in the chain is tried. */
    data object Pass : RuleOutcome

    /** The rule maps the property with [strategy]; the chain stops. */
    data class Resolved(val strategy: PropertyMappingStrategy) : RuleOutcome

    /**
     * The rule owns the property, has already reported the error for it, and generates nothing.
     * The chain stops, so no later rule adds a follow-on error (for example KRAFT-E005) for the
     * same cause, and [PropertyResolver.resolve] yields null, as for any unresolved property.
     */
    data object Reported : RuleOutcome
}
