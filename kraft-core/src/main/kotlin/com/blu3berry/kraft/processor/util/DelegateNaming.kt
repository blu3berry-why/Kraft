package com.blu3berry.kraft.processor.util

import com.blu3berry.kraft.model.scan.ConverterTypeKey

/**
 * Derives the name of a generated `@KraftConverterDelegate` wrapper from the
 * converter's [ConverterTypeKey].
 *
 * The name must be a pure function of the type pair — identical across modules and
 * builds — because KMP consumers cannot enumerate the delegate package on the klib
 * classpath (`Resolver.getDeclarationsFromPackage` returns nothing for klib
 * dependencies). Instead they *construct* the expected delegate name for each
 * unresolved type pair and resolve it via `Resolver.getFunctionDeclarationsByName`,
 * which does work across klibs.
 *
 * Shape: `kraftDelegate_<SourceSimple>_to_<TargetSimple>_<hash>` where `<hash>` is a
 * stable 8-hex-digit hash of the full key (qualified names + nullability). The simple
 * names are cosmetic; the hash carries the identity. Two modules declaring a converter
 * for the same pair therefore emit the same delegate FQN, which is how consumers
 * detect upstream-vs-upstream ambiguity (the by-name lookup returns both).
 *
 * Compatibility contract: the name format is Kraft-internal wiring, not public API,
 * and may change between Kraft releases. Producer and consumer modules must build
 * with the same Kraft version for KMP cross-module discovery; a mismatch degrades to
 * "no converter found" (the consumer computes a name the producer never emitted) —
 * never to wrong generated code.
 */
object DelegateNaming {

    private val UNSAFE_IDENTIFIER_CHARS = Regex("[^A-Za-z0-9_]")

    fun delegateNameFor(key: ConverterTypeKey): String {
        val sourceSimple = identifierSafe(key.sourceFqName.substringAfterLast('.'))
        val targetSimple = identifierSafe(key.targetFqName.substringAfterLast('.'))
        return "kraftDelegate_${sourceSimple}_to_${targetSimple}_${keyHash(key)}"
    }

    private fun identifierSafe(raw: String): String =
        raw.replace(UNSAFE_IDENTIFIER_CHARS, "_")

    /**
     * Stable across JVM runs: `String.hashCode` is specified by contract, and the
     * canonical string fixes field order. Collisions between *different* pairs are
     * tolerated — consumers validate the resolved function's actual receiver/return
     * types against the requested key before using it.
     */
    private fun keyHash(key: ConverterTypeKey): String {
        val canonical =
            "${key.sourceFqName}|${key.sourceNullable}>${key.targetFqName}|${key.targetNullable}"
        return "%08x".format(canonical.hashCode())
    }
}
