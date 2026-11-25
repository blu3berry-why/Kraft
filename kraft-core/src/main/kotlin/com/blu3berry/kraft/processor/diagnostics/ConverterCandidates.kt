package com.blu3berry.kraft.processor.diagnostics

import com.google.devtools.ksp.symbol.KSType
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.processor.util.unwrapTypeAliases

/** Most converter lines KRAFT-E005 prints before summarising the rest. */
private const val MAX_LISTED_CONVERTERS = 10

/** Orders converters the same way in the E005 listing and in the `kraft.report` file. */
val CONVERTER_ORDER: Comparator<Map.Entry<ConverterTypeKey, ConverterEntry>> =
    compareBy<Map.Entry<ConverterTypeKey, ConverterEntry>>(
        { it.key.sourceRendering },
        { it.key.targetRendering },
        { it.value.callFqName },
    )

/**
 * The KRAFT-E005 section that lists the converters touching a failing property pair: every
 * known converter whose source type appears in [source]'s type, or whose target type appears
 * in [target]'s type. Type arguments count, so `List<Money>` matches a `Money` converter, and
 * nullability is ignored. When none match, the section says so and points at `kraft.report`.
 */
fun converterCandidatesSection(
    source: PropertyInfo,
    target: PropertyInfo,
    converters: GlobalConverterRegistry
): String {
    val sourceNames = qualifiedNamesIn(source.type.ksType)
    val targetNames = qualifiedNamesIn(target.type.ksType)
    val matches = converters.knownEntries().entries
        .filter { it.key.sourceFqName in sourceNames || it.key.targetFqName in targetNames }
        .sortedWith(CONVERTER_ORDER)

    if (matches.isEmpty()) {
        val from = leafNames(source.type.ksType)
        val to = leafNames(target.type.ksType)
        return """
            No registered converter has $from as its source or $to as its target.
            Set the KSP option kraft.report=true to list every converter Kraft registered.
        """.trimIndent().prependIndent("    ")
    }

    val lines = matches.take(MAX_LISTED_CONVERTERS).map { (key, entry) ->
        "      • ${key.sourceRendering} → ${key.targetRendering} (${entry.callFqName}, ${entry.origin.label})"
    }
    val more = matches.size - MAX_LISTED_CONVERTERS
    val tail = if (more > 0) listOf("      … and $more more (set kraft.report=true to see them all)") else emptyList()
    return (listOf("    Registered converters touching these types:") + lines + tail).joinToString("\n")
}

/** The qualified names of [type] and, recursively, of its type arguments. */
private fun qualifiedNamesIn(type: KSType): Set<String> {
    val out = linkedSetOf<String>()
    fun visit(t: KSType) {
        val unwrapped = t.unwrapTypeAliases()
        unwrapped.declaration.qualifiedName?.asString()?.let(out::add)
        unwrapped.arguments.forEach { arg -> arg.type?.resolve()?.let(::visit) }
    }
    visit(type)
    return out
}

/**
 * The types a converter would be written for: [type] itself, or its innermost type arguments
 * for a parameterised type (`List<Money>` → `app.Money`), joined with " or ".
 */
private fun leafNames(type: KSType): String {
    val out = linkedSetOf<String>()
    fun visit(t: KSType) {
        val unwrapped = t.unwrapTypeAliases()
        val args = unwrapped.arguments.mapNotNull { it.type?.resolve() }
        if (args.isEmpty()) {
            unwrapped.declaration.qualifiedName?.asString()?.let(out::add)
        } else {
            args.forEach(::visit)
        }
    }
    visit(type)
    return out.joinToString(" or ")
}
