package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Nullability

/**
 * Resolves through (possibly chained) type aliases to the underlying class
 * type, preserving the use-site nullability of the outermost reference.
 *
 * KSP never auto-expands aliases: for `typealias AliasedStamp = Stamp` the
 * resolved [KSType.declaration] of a `AliasedStamp` property is the
 * [KSTypeAlias] node, not `Stamp`'s class declaration. Kotlin aliases are
 * transparent at call sites, so mapping (and generated code) must treat the
 * property as its underlying type. Nullability lives on the outer reference —
 * `AliasedStamp?` aliases non-null `Stamp` — hence the [Nullability] carry-over
 * at each hop.
 *
 * Parameterized aliases (`typealias StringMap<V> = Map<String, V>`) are left
 * untouched: `alias.type.resolve()` returns the right-hand side without
 * substituting use-site type arguments, so unwrapping one would produce a
 * wrong type. Those fall through to the existing non-class handling
 * (skip/error) exactly as before.
 */
fun KSType.unwrapTypeAliases(): KSType {
    var current = this
    while (true) {
        val alias = current.declaration as? KSTypeAlias ?: return current
        if (alias.typeParameters.isNotEmpty()) return current
        val underlying = alias.type.resolve()
        current = if (current.nullability == Nullability.NULLABLE) {
            underlying.makeNullable()
        } else {
            underlying
        }
    }
}
