package com.blu3berry.kraft.processor.descriptor.propertyresolver

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.ElementOp
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.processor.util.ValueClassInfo
import com.blu3berry.kraft.processor.util.isMappableClass
import com.blu3berry.kraft.processor.util.sameTypeIgnoringNullability
import com.blu3berry.kraft.processor.util.valueClassInfo

/**
 * Resolves how one value of a source type maps to a target type, independent of where the
 * value sits. The scalar rules (`NestedRule`, `GlobalConverterRule`, `ValueClassRule`) use
 * its type-pair checks for a property's own type, and `ContainerRule` uses [resolveElement]
 * for each container element, so the same pair resolves the same way in both positions.
 *
 * The checks here only answer "which operation applies"; nullability and diagnostics stay
 * with the caller, because a property and a container element handle them differently.
 */
object LeafOperationResolver {

    /**
     * The operation for one container element of [source] → [target], tried in the order of
     * the scalar rule chain: auto-detected nested mapper, then a global converter, then a
     * value-class unwrap or wrap. Returns null when none applies.
     *
     * A `@MapEnum`-derived (synthetic) converter applies as a nested-style `to<Target>()` call.
     * A hand-written or classpath (real) `@KraftConverter` applies under its own callable name
     * (`it.intoBar()`); a converter registered for the non-null pair also serves nullable
     * elements, through a safe call or by dropping nulls, as for a nested mapper. Value-class
     * operations apply only between non-null elements, and an unwrap only to a non-null
     * underlying type.
     */
    fun resolveElement(source: TypeInfo, target: TypeInfo, ctx: MappingContext): ElementOp? {
        autoNested(source, target)?.let { return ElementOp.Nested(it) }
        val key = converterKey(source, target)
        val converter = key?.let { ctx.globalConverters.lookup(it) }
        // Enum mappers are registered for the non-null pair only; like a real converter, that
        // entry also serves nullable elements, rendered as `it?.toB()` / `mapNotNull`.
        val synthetic = converter as? ConverterEntry.Synthetic
            ?: key?.takeIf { converter == null }
                ?.let { ctx.globalConverters.lookup(it.copy(sourceNullable = false, targetNullable = false)) }
                as? ConverterEntry.Synthetic
        if (synthetic != null) return ElementOp.Nested(nestedDescriptor(source, target))
        realConverterOp(source, target, key, converter, ctx)?.let { return it }
        return valueClassElementOp(source, target)
    }

    private fun realConverterOp(
        source: TypeInfo,
        target: TypeInfo,
        key: ConverterTypeKey?,
        exact: ConverterEntry?,
        ctx: MappingContext
    ): ElementOp.Converter? {
        if (key == null) return null
        val entry = exact as? ConverterEntry.Real
            ?: ctx.globalConverters.lookup(key.copy(sourceNullable = false, targetNullable = false))
                as? ConverterEntry.Real
            ?: return null
        val function = entry.function
        val isExtension = function.extensionReceiver != null
        // A free-function converter cannot be safe-called, so it needs the exact non-null pair.
        if (!isExtension && (source.isNullable || target.isNullable)) return null
        return ElementOp.Converter(
            ConverterDescriptor(
                enclosingObject = null,
                function = function,
                callPackageName = entry.packageName,
                callFunctionName = entry.simpleName,
                isExtension = isExtension,
                sourcePropertyName = null,
                targetPropertyName = "",
                sourceType = source,
                targetType = target
            )
        )
    }

    private fun valueClassElementOp(source: TypeInfo, target: TypeInfo): ElementOp? {
        if (source.isNullable || target.isNullable) return null
        unwrapOf(source, target)
            ?.takeUnless { it.type.isMarkedNullable }
            ?.let { return ElementOp.Unwrap(it.propertyName) }
        return if (wrapOf(source, target) != null) ElementOp.Wrap(target) else null
    }

    /**
     * A synthesised nested-mapper descriptor for [source] → [target] when both are classes
     * Kraft can generate a mapper for (see [isMappableClass]), or null otherwise.
     */
    fun autoNested(source: TypeInfo, target: TypeInfo): NestedMappingDescriptor? =
        if (isMappableClass(source) && isMappableClass(target)) nestedDescriptor(source, target) else null

    /** The nested-mapper descriptor for [source] → [target], keyed by their declarations. */
    fun nestedDescriptor(source: TypeInfo, target: TypeInfo): NestedMappingDescriptor =
        NestedMappingDescriptor(
            nestedMapperId = MapperId(
                sourceQualifiedName = source.declaration.qualifiedName?.asString()
                    ?: source.declaration.simpleName.asString(),
                targetQualifiedName = target.declaration.qualifiedName?.asString()
                    ?: target.declaration.simpleName.asString()
            ),
            sourceType = source,
            targetType = target
        )

    /**
     * The global-converter lookup key for [source] → [target]. Platform types (Java
     * declarations without nullness metadata) yield null so they don't silently alias into
     * NOT_NULL keys.
     */
    fun converterKey(source: TypeInfo, target: TypeInfo): ConverterTypeKey? {
        val sourceNullable = source.ksType.nullableFlag() ?: return null
        val targetNullable = target.ksType.nullableFlag() ?: return null
        return ConverterTypeKey(
            sourceFqName = source.qualifiedName,
            sourceNullable = sourceNullable,
            targetFqName = target.qualifiedName,
            targetNullable = targetNullable
        )
    }

    /** The underlying property of [source] when it is a value class wrapping [target] (nullability ignored). */
    fun unwrapOf(source: TypeInfo, target: TypeInfo): ValueClassInfo? =
        valueClassInfo(source)?.takeIf { sameTypeIgnoringNullability(it.type, target.ksType) }

    /** The underlying property of [target] when it is a value class wrapping [source] (nullability ignored). */
    fun wrapOf(source: TypeInfo, target: TypeInfo): ValueClassInfo? =
        valueClassInfo(target)?.takeIf { sameTypeIgnoringNullability(it.type, source.ksType) }

    private fun KSType.nullableFlag(): Boolean? = when (nullability) {
        Nullability.NULLABLE -> true
        Nullability.NOT_NULL -> false
        Nullability.PLATFORM -> null
    }
}
