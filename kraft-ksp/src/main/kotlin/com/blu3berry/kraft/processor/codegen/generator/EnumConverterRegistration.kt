package com.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.processor.codegen.EnumMapperGeneratorSpi

/**
 * Builds the synthetic-converter index for `@MapEnum`-derived mappers — one
 * entry per [EnumMappingDescriptor], keyed by `(source enum, target enum)` so
 * the existing [com.blu3berry.kraft.processor.descriptor.propertyresolver.rules.GlobalConverterRule]
 * can resolve enum-typed properties without per-`@MapConfig` `@MapUsing`
 * boilerplate, and so [DelegateRegistryGenerator] can re-export each enum
 * mapper as a `@KraftConverterDelegate` for cross-module discovery.
 *
 * Naming is delegated to [enumGenerator] so the registered call coordinates
 * always match the actual generated extension — passing the same SPI instance
 * that performs the codegen guarantees the two sides can't drift, even when
 * a custom [EnumMapperGeneratorSpi] is loaded via `ServiceLoader`.
 *
 * Two `@MapEnum` declarations that register the same `(source, target)` pair
 * are reported via [logger] as a compile-time ambiguity (anchored at the
 * second declaration, with the first declaration's location surfaced in the
 * message) and the duplicate is dropped from the result so processing can
 * continue with at most one synthetic entry per pair.
 *
 * Pairs that can't be represented as a [ConverterTypeKey] (e.g. PLATFORM-typed
 * declarations without nullness metadata) are silently skipped — the caller's
 * existing rules already reject those at the `@MapEnum` site.
 */
fun enumMappingsToConverterEntries(
    descriptors: List<EnumMappingDescriptor>,
    enumGenerator: EnumMapperGeneratorSpi,
    logger: KSPLogger,
): Map<ConverterTypeKey, ConverterEntry.Synthetic> {
    if (descriptors.isEmpty()) return emptyMap()
    val out = LinkedHashMap<ConverterTypeKey, ConverterEntry.Synthetic>(descriptors.size)
    val seen = HashMap<ConverterTypeKey, EnumMappingDescriptor>(descriptors.size)
    for (desc in descriptors) {
        val key = buildKey(desc) ?: continue
        val previous = seen[key]
        if (previous != null) {
            reportDuplicateEnumMapping(key, previous, desc, logger)
            continue
        }
        seen[key] = desc
        out[key] = ConverterEntry.Synthetic(
            packageName = enumGenerator.generatedPackage(desc),
            simpleName = enumGenerator.generatedFunctionName(desc),
            sourceTypeInfo = desc.sourceType,
            targetTypeInfo = desc.targetType,
            // The @MapEnum declaration file is included alongside the source
            // and target enum files so editing the annotation arguments
            // (fieldMappings, source/target swaps) invalidates the synthetic
            // registry consumer even when neither enum was touched.
            originatingFiles = listOfNotNull(
                desc.sourceType.declaration.containingFile,
                desc.targetType.declaration.containingFile,
                desc.declaration?.containingFile,
            ).distinct()
        )
    }
    return out
}

private fun reportDuplicateEnumMapping(
    key: ConverterTypeKey,
    first: EnumMappingDescriptor,
    duplicate: EnumMappingDescriptor,
    logger: KSPLogger,
) {
    val source = "${key.sourceFqName}${if (key.sourceNullable) "?" else ""}"
    val target = "${key.targetFqName}${if (key.targetNullable) "?" else ""}"
    val firstLocation = first.declaration?.qualifiedName?.asString()
        ?: first.declaration?.simpleName?.asString()
        ?: "<unknown>"
    logger.error(
        "Ambiguous @MapEnum: ($source → $target) is registered by more than one " +
            "@MapEnum declaration. First seen at '$firstLocation'. " +
            "Remove one of the @MapEnum declarations or change one of the source/target pairs.",
        duplicate.declaration
    )
}

/**
 * Merges [synthetic] enum-derived entries into [sameModule] hand-written
 * `@KraftConverter` entries. Pairs that exist in both produce a compile-time
 * ambiguity error and are dropped from the result so processing can continue
 * with at most one entry per pair. The synthetic entry wins arbitrarily on
 * the dropped slot — the error will surface either way.
 */
fun mergeWithEnumAmbiguityCheck(
    sameModule: GlobalConverterRegistry,
    synthetic: Map<ConverterTypeKey, ConverterEntry.Synthetic>,
    logger: KSPLogger
): GlobalConverterRegistry {
    if (synthetic.isEmpty()) return sameModule
    val merged = LinkedHashMap<ConverterTypeKey, ConverterEntry>(sameModule.entries)
    for ((key, syntheticEntry) in synthetic) {
        val existing = merged[key]
        if (existing is ConverterEntry.Real) {
            reportAmbiguity(key, existing, syntheticEntry, logger)
            // Keep the Real entry — its source is the user's hand-written
            // function, which gives a better navigation target than the
            // synthetic. The error has already been emitted.
            continue
        }
        merged[key] = syntheticEntry
    }
    return GlobalConverterRegistry(merged)
}

private fun reportAmbiguity(
    key: ConverterTypeKey,
    existing: ConverterEntry.Real,
    duplicate: ConverterEntry.Synthetic,
    logger: KSPLogger
) {
    val source = "${key.sourceFqName}${if (key.sourceNullable) "?" else ""}"
    val target = "${key.targetFqName}${if (key.targetNullable) "?" else ""}"
    val existingName = existing.function.qualifiedName?.asString() ?: existing.simpleName
    logger.error(
        "Ambiguous converter for ($source → $target): registered both as " +
            "@KraftConverter '$existingName' and as @MapEnum-generated " +
            "'${duplicate.packageName}.${duplicate.simpleName}'. " +
            "Remove one — the @MapEnum-generated mapper already auto-resolves " +
            "for this enum pair.",
        existing.function
    )
}

private fun buildKey(desc: EnumMappingDescriptor): ConverterTypeKey? {
    val sourceNullable = desc.sourceType.ksType.nullableFlag() ?: return null
    val targetNullable = desc.targetType.ksType.nullableFlag() ?: return null
    return ConverterTypeKey(
        sourceFqName = desc.sourceType.qualifiedName,
        sourceNullable = sourceNullable,
        targetFqName = desc.targetType.qualifiedName,
        targetNullable = targetNullable
    )
}

private fun com.google.devtools.ksp.symbol.KSType.nullableFlag(): Boolean? = when (nullability) {
    Nullability.NULLABLE -> true
    Nullability.NOT_NULL -> false
    Nullability.PLATFORM -> null
}
