package com.blu3berry.kraft.model.scan

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.blu3berry.kraft.model.TypeInfo

/**
 * Identifies a converter by its source/target type pair.
 *
 * Both qualified names refer to the **non-generic, non-nullable** declaration name
 * (e.g. `kotlin.String`, `kotlin.uuid.Uuid`). [sourceNullable] / [targetNullable]
 * record the declared nullability so a `Uuid → String?` converter is distinct from
 * a `Uuid → String` converter.
 */
data class ConverterTypeKey(
    val sourceFqName: String,
    val sourceNullable: Boolean,
    val targetFqName: String,
    val targetNullable: Boolean
)

/**
 * A single entry in the [GlobalConverterRegistry].
 *
 * Two flavours exist because Kraft auto-resolves both hand-written
 * [@KraftConverter][com.blu3berry.kraft.config.KraftConverter] extensions and
 * [@MapEnum][com.blu3berry.kraft.config.MapEnum]-generated enum mappers as global
 * converters. Real entries carry the underlying KSP declaration (used for opt-in
 * collection, error reporting, and delegate emission). Synthetic entries
 * represent functions that *will* be generated in this same KSP round, so no
 * declaration exists yet — only the call coordinates.
 */
sealed class ConverterEntry {
    abstract val packageName: String
    abstract val simpleName: String

    /** A converter the user wrote (or that an upstream module published as a delegate). */
    data class Real(val function: KSFunctionDeclaration) : ConverterEntry() {
        override val packageName: String get() = function.packageName.asString()
        override val simpleName: String get() = function.simpleName.asString()
    }

    /**
     * A converter that this KSP run is generating itself (currently only used for
     * `@MapEnum`-derived enum mappers). The full source/target [TypeInfo] is kept
     * so [com.blu3berry.kraft.processor.codegen.generator.DelegateRegistryGenerator]
     * can emit a `@KraftConverterDelegate` wrapper for cross-module discovery
     * without needing the original `KSFunctionDeclaration`. [originatingFiles] is
     * forwarded to KSP's [com.google.devtools.ksp.processing.Dependencies].
     */
    data class Synthetic(
        override val packageName: String,
        override val simpleName: String,
        val sourceTypeInfo: TypeInfo,
        val targetTypeInfo: TypeInfo,
        val originatingFiles: List<KSFile>
    ) : ConverterEntry()
}

/**
 * Indexed lookup of converters discovered during the current KSP round —
 * hand-written `@KraftConverter` extensions, classpath `@KraftConverterDelegate`
 * trampolines, and `@MapEnum`-generated enum mappers.
 *
 * Built once per processing round and threaded through `MappingContext` for use
 * by the property-resolver chain.
 *
 * @param entries Map keyed by source/target type pair → entry describing where
 *                to find or how to call the converter.
 */
data class GlobalConverterRegistry(
    val entries: Map<ConverterTypeKey, ConverterEntry>
) {
    fun lookup(key: ConverterTypeKey): ConverterEntry? = entries[key]

    /**
     * Returns a new registry with [other] merged in at lower priority — entries
     * already present in `this` win. Used to fold classpath-discovered delegates
     * underneath same-module declarations so a local override silently shadows a
     * classpath default.
     */
    fun mergeAsFallback(other: GlobalConverterRegistry): GlobalConverterRegistry {
        if (other.entries.isEmpty()) return this
        if (entries.isEmpty()) return other
        val merged = LinkedHashMap<ConverterTypeKey, ConverterEntry>(entries.size + other.entries.size)
        merged.putAll(other.entries)
        merged.putAll(entries) // same-module overrides classpath
        return GlobalConverterRegistry(merged)
    }

    companion object {
        val EMPTY = GlobalConverterRegistry(emptyMap())
    }
}
