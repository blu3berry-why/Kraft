package com.blu3berry.kraft.processor.codegen.generator

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName

/**
 * Adds an aliased import for every member in [members] whose simple name is shared with
 * a member from another package.
 *
 * KotlinPoet imports one member per simple name and falls back to the fully qualified
 * name for the rest. A qualified *extension* call (`this.x.com.example.toUser()`) is not
 * valid Kotlin, so a parent mapper calling `api.User.toUser()` and `auth.User.toUser()`
 * would not compile. With an aliased import KotlinPoet renders `%M` as the alias.
 *
 * Each alias is the simple name plus the shortest trailing run of package segments that
 * tells the colliding packages apart, e.g. `toUserApiGenerated` / `toUserAuthGenerated`.
 *
 * A member in the file's own package is never aliased: KotlinPoet renders it by simple
 * name without an import, and an aliased import would hide that simple name.
 */
internal fun FileSpec.Builder.aliasCollidingMemberImports(members: Collection<MemberName>): FileSpec.Builder {
    members.distinct()
        .groupBy { it.simpleName }
        .values
        .filter { group -> group.map { it.packageName }.distinct().size > 1 }
        .forEach { group ->
            val suffixes = distinguishingSuffixes(group.map { it.packageName })
            group.filter { it.packageName != packageName }.forEach { member ->
                addAliasedImport(member, member.simpleName + suffixes.getValue(member.packageName))
            }
        }
    return this
}

/**
 * Maps each package to the capitalized concatenation of its last `k` segments, for the
 * smallest `k` that makes all of them distinct. When concatenation alone cannot separate
 * them (`foo.bar` and `fooBar` both give `FooBar`), falls back to the full package with
 * underscores, which is distinct for distinct packages.
 */
private fun distinguishingSuffixes(packages: List<String>): Map<String, String> {
    val segments = packages.distinct().associateWith { pkg -> pkg.split('.').filter { it.isNotEmpty() } }
    val maxDepth = segments.values.maxOf { it.size }
    for (depth in 1..maxDepth) {
        val suffixes = segments.mapValues { (_, parts) ->
            parts.takeLast(depth).joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        }
        if (suffixes.values.distinct().size == suffixes.size) return suffixes
    }
    return segments.mapValues { (pkg, _) -> "_" + pkg.replace('.', '_') }
}
