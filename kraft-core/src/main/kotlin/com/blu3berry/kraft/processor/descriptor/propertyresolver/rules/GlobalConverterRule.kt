package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.ConverterSource
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.model.scan.ConverterTypeKey
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
 */
class GlobalConverterRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        if (ctx.globalConverters.entries.isEmpty()) return null
        val match = findGlobalMatch(target, ctx) ?: return null
        return PropertyMappingStrategy.ConverterFunction(
            targetProperty = target,
            source = ConverterSource.Property(match.source),
            converter = ConverterDescriptor(
                enclosingObject = null,
                function = match.converter,
                sourcePropertyName = match.effectiveSourceName,
                targetPropertyName = target.name,
                sourceType = match.source.type,
                targetType = target.type
            )
        )
    }

    /**
     * Resolves the source property + registered converter for [target], honouring
     * `@MapField` / `FieldMapping` renames. Returns `null` when no source matches
     * the effective name, types already match, either side is PLATFORM-typed, or
     * no converter is registered for the pair — letting the rest of the resolver
     * chain handle those cases.
     */
    private fun findGlobalMatch(target: PropertyInfo, ctx: MappingContext): GlobalMatch? {
        val effectiveSourceName = ctx.classRenames[target.name]
            ?: ctx.configRenames[target.name]
            ?: target.name
        val source = ctx.sourceProps[effectiveSourceName] ?: return null
        if (source.type.ksType == target.type.ksType) return null
        val key = buildLookupKey(source, target) ?: return null
        val converterFn = ctx.globalConverters.lookup(key) ?: return null
        return GlobalMatch(effectiveSourceName, source, converterFn)
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
        val converter: com.google.devtools.ksp.symbol.KSFunctionDeclaration
    )

    private fun KSType.nullableFlag(): Boolean? = when (nullability) {
        Nullability.NULLABLE -> true
        Nullability.NOT_NULL -> false
        Nullability.PLATFORM -> null
    }
}
