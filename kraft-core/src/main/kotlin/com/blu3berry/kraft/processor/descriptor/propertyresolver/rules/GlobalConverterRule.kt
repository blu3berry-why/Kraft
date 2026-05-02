package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

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

        val effectiveSourceName = ctx.classRenames[target.name]
            ?: ctx.configRenames[target.name]
            ?: target.name
        val source = ctx.sourceProps[effectiveSourceName] ?: return null

        if (source.type.ksType == target.type.ksType) return null

        val key = ConverterTypeKey(
            sourceFqName = source.type.qualifiedName,
            sourceNullable = source.type.ksType.nullability == Nullability.NULLABLE,
            targetFqName = target.type.qualifiedName,
            targetNullable = target.type.ksType.nullability == Nullability.NULLABLE
        )
        val converterFn = ctx.globalConverters.lookup(key) ?: return null

        val descriptor = ConverterDescriptor(
            enclosingObject = null,
            function = converterFn,
            sourcePropertyName = effectiveSourceName,
            targetPropertyName = target.name,
            sourceType = source.type,
            targetType = target.type
        )

        return PropertyMappingStrategy.ConverterFunction(
            targetProperty = target,
            source = ConverterSource.Property(source),
            converter = descriptor
        )
    }
}
