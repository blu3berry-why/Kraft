package com.blu3berry.kraft.model.scan

import com.google.devtools.ksp.symbol.KSFunctionDeclaration

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
 * Indexed lookup of [@KraftConverter][com.blu3berry.kraft.config.KraftConverter]
 * extension functions discovered during the current KSP round.
 *
 * Built once per processing round by `GlobalConverterScanner` and threaded through
 * `MappingContext` for use by the property-resolver chain.
 *
 * @param entries Map keyed by source/target type pair → KSP function declaration of
 *                the registered converter.
 */
data class GlobalConverterRegistry(
    val entries: Map<ConverterTypeKey, KSFunctionDeclaration>
) {
    fun lookup(key: ConverterTypeKey): KSFunctionDeclaration? = entries[key]

    /**
     * Returns a new registry with [other] merged in at lower priority — entries
     * already present in `this` win. Used to fold classpath-discovered delegates
     * underneath same-module `@KraftConverter` declarations so a local override
     * silently shadows a classpath default.
     */
    fun mergeAsFallback(other: GlobalConverterRegistry): GlobalConverterRegistry {
        if (other.entries.isEmpty()) return this
        if (entries.isEmpty()) return other
        val merged = LinkedHashMap<ConverterTypeKey, KSFunctionDeclaration>(entries.size + other.entries.size)
        merged.putAll(other.entries)
        merged.putAll(entries) // same-module overrides classpath
        return GlobalConverterRegistry(merged)
    }

    companion object {
        val EMPTY = GlobalConverterRegistry(emptyMap())
    }
}
