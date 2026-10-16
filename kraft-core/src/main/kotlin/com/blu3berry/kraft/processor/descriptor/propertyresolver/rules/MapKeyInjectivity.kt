package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.ElementOp
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.processor.descriptor.propertyresolver.LeafOperationResolver
import com.blu3berry.kraft.processor.util.KraftKspConstants
import com.blu3berry.kraft.processor.util.findAnnotation

/**
 * Decides whether a `Map` key operation is provably one-to-one (design D4). Accepted: identity,
 * a value-class wrap or unwrap, and an enum mapper whose entry table maps no two source entries
 * to one target entry ([ConverterEntry.Synthetic.isInjective]). Refused, with KRAFT-E064: a
 * nested mapper, a `@KraftConverter`, a many-to-one `@MapEnum`, and a converter or enum mapper
 * from another module, whose entry table is not visible.
 */
internal object MapKeyInjectivity {

    /** Why a key operation is refused, as the KRAFT-E064 message words it. */
    data class Refusal(val operation: String, val effect: String)

    private const val MERGES_KEYS = "can map two different keys to the same key and silently drop entries"

    /** Null when [op] maps [srcKey] keys to [tgtKey] keys one-to-one; otherwise why it is refused. */
    fun refusal(op: ElementOp, srcKey: TypeInfo, tgtKey: TypeInfo, ctx: MappingContext): Refusal? =
        when (op) {
            ElementOp.Identity, is ElementOp.Wrap, is ElementOp.Unwrap -> null
            // An enum mapper renders as a nested-style call; the registry tells the two apart.
            is ElementOp.Nested -> enumRefusal(srcKey, tgtKey, ctx)
            is ElementOp.Converter -> Refusal(converterLabel(op.converter, srcKey), MERGES_KEYS)
        }

    /** An enum mapper is accepted when its entry table is one-to-one; a nested mapper never is. */
    private fun enumRefusal(srcKey: TypeInfo, tgtKey: TypeInfo, ctx: MappingContext): Refusal? {
        val entry = LeafOperationResolver.converterKey(srcKey, tgtKey)
            ?.let { ctx.globalConverters.lookup(it) } as? ConverterEntry.Synthetic
            ?: return Refusal("a nested mapper", MERGES_KEYS)
        val collision = entry.keyCollision ?: return null
        return Refusal(
            operation = "@MapEnum ${srcKey.simpleName} → ${tgtKey.simpleName}",
            effect = "maps ${collision.sources.joinToString(", ")} to ${collision.target} " +
                "and would silently drop entries"
        )
    }

    /**
     * `@KraftConverter Long.toKey()` for a converter written in this module. A classpath
     * delegate (a converter or enum mapper compiled in another module) has no visible entry
     * table and is named as such.
     */
    private fun converterLabel(converter: ConverterDescriptor, srcKey: TypeInfo): String {
        if (converter.function?.findAnnotation(KraftKspConstants.FQ_KRAFT_CONVERTER_DELEGATE) != null) {
            return "a converter from another module"
        }
        val name = converter.callFunctionName
        val call = if (converter.isExtension) "${srcKey.simpleName}.$name()" else "$name(${srcKey.simpleName})"
        return "@KraftConverter $call"
    }
}
