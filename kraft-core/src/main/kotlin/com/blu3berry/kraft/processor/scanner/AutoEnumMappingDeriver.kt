package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.EnumEntryMapping
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import com.blu3berry.kraft.model.scan.ClassMappingScanResult
import com.blu3berry.kraft.model.scan.ConfigObjectScanResult
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.processor.util.collectPropertyTypeRefs
import com.blu3berry.kraft.processor.util.collectionKindOf
import com.blu3berry.kraft.processor.util.elementTypeInfo
import com.blu3berry.kraft.processor.util.enumEntryNames
import com.blu3berry.kraft.processor.util.isMappableClass

/**
 * Walks every parent `@MapConfig` / `@MapTo` mapping pair and synthesizes an
 * [EnumMappingDescriptor] for every property pair whose source and target are
 * two different enum classes, both declared in the current module, when every
 * source entry has a same-named target entry.
 *
 * Skips pairs that are already covered by a user-declared `@MapEnum`
 * descriptor or a hand-written `@KraftConverter`. Skips cross-module pairs
 * (one or both enums on the classpath only). Pairs that don't auto-pair are
 * left alone — the existing `RequiredFieldErrorRule` path will still emit the
 * "type mismatch" diagnostic, which is the correct outcome.
 *
 * Output is keyed by `(sourceFqName, targetFqName)` so two parent mappers
 * referencing the same enum pair don't produce duplicate descriptors.
 */
class AutoEnumMappingDeriver {
    fun derive(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        existingEnumMappings: List<EnumMappingDescriptor>,
        sameModuleConverters: GlobalConverterRegistry,
    ): List<EnumMappingDescriptor> {
        val seeds = collectParentClassPairs(classMappings, configMappings)
        if (seeds.isEmpty()) return emptyList()

        val covered = coveredPairs(existingEnumMappings, sameModuleConverters)
        val out = LinkedHashMap<Pair<String, String>, EnumMappingDescriptor>()
        val visited = HashSet<Pair<String, String>>()
        val worklist = ArrayDeque<ClassPair>().apply { addAll(seeds) }

        while (worklist.isNotEmpty()) {
            val pair = worklist.removeFirst()
            val key = pair.fqKey() ?: continue
            if (!visited.add(key)) continue
            walkProperties(pair, covered, worklist, out)
        }
        return out.values.toList()
    }

    private data class ClassPair(val source: KSClassDeclaration, val target: KSClassDeclaration)

    private fun collectParentClassPairs(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
    ): List<ClassPair> {
        val pairs = mutableListOf<ClassPair>()
        for (m in classMappings) {
            pairs += ClassPair(m.sourceType, m.targetType)
            if (m.hasReverse) pairs += ClassPair(m.targetType, m.sourceType)
        }
        for (m in configMappings) {
            pairs += ClassPair(m.sourceType, m.targetType)
            if (m.hasReverse) pairs += ClassPair(m.targetType, m.sourceType)
        }
        return pairs
    }

    private fun walkProperties(
        pair: ClassPair,
        covered: Set<Pair<String, String>>,
        worklist: ArrayDeque<ClassPair>,
        out: MutableMap<Pair<String, String>, EnumMappingDescriptor>,
    ) {
        val sourceProps = pair.source.collectPropertyTypeRefs()
        val targetProps = pair.target.collectPropertyTypeRefs()
        for ((propName, targetProp) in targetProps) {
            val sourceProp = sourceProps[propName] ?: continue
            tryDeriveEnumDescriptor(sourceProp, targetProp, covered, out)
                ?.also { out.putIfAbsent(it.fqKey(), it) }
            enqueueIfNestedClassPair(sourceProp, targetProp, worklist)
        }
    }

    @Suppress("ReturnCount")
    private fun enqueueIfNestedClassPair(
        sourceType: KSType,
        targetType: KSType,
        worklist: ArrayDeque<ClassPair>,
    ) {
        val (resolvedSource, resolvedTarget) = resolveRecursableTypes(sourceType, targetType) ?: return
        val srcDecl = resolvedSource.declaration as? KSClassDeclaration ?: return
        val tgtDecl = resolvedTarget.declaration as? KSClassDeclaration ?: return
        val srcFq = srcDecl.qualifiedName?.asString() ?: return
        val tgtFq = tgtDecl.qualifiedName?.asString() ?: return
        if (srcFq == tgtFq) return
        val srcInfo = TypeInfo.fromKSType(resolvedSource)
        val tgtInfo = TypeInfo.fromKSType(resolvedTarget)
        if (!isMappableClass(srcInfo) || !isMappableClass(tgtInfo)) return
        if (srcDecl.containingFile == null || tgtDecl.containingFile == null) return
        worklist.addLast(ClassPair(srcDecl, tgtDecl))
    }

    /**
     * Returns the pair of types the deriver should treat as the candidates for
     * recursion. When [sourceType] and [targetType] are the same kind of
     * single-element collection wrapper (`List`/`List` or `Set`/`Set`), peels
     * them to their element types so the mappability gate runs against the
     * elements (e.g. `Set<User>` → `User`). Returns `null` when one side is a
     * collection and the other isn't, or when the kinds differ — neither
     * `NestedRule` nor the deriver auto-maps mismatched wrappers. For
     * non-collection inputs returns the inputs unchanged.
     */
    @Suppress("ReturnCount")
    private fun resolveRecursableTypes(
        sourceType: KSType,
        targetType: KSType,
    ): Pair<KSType, KSType>? {
        val srcInfo = TypeInfo.fromKSType(sourceType)
        val tgtInfo = TypeInfo.fromKSType(targetType)
        val srcKind = collectionKindOf(srcInfo)
        val tgtKind = collectionKindOf(tgtInfo)
        if (srcKind == null && tgtKind == null) return sourceType to targetType
        if (srcKind != tgtKind) return null
        val srcElement = elementTypeInfo(srcInfo) ?: return null
        val tgtElement = elementTypeInfo(tgtInfo) ?: return null
        return srcElement.ksType to tgtElement.ksType
    }

    private fun ClassPair.fqKey(): Pair<String, String>? {
        val s = source.qualifiedName?.asString() ?: return null
        val t = target.qualifiedName?.asString() ?: return null
        return s to t
    }

    private fun EnumMappingDescriptor.fqKey(): Pair<String, String> =
        sourceType.qualifiedName to targetType.qualifiedName

    private fun coveredPairs(
        existingEnumMappings: List<EnumMappingDescriptor>,
        sameModuleConverters: GlobalConverterRegistry,
    ): Set<Pair<String, String>> {
        val covered = HashSet<Pair<String, String>>()
        for (m in existingEnumMappings) {
            if (!m.sourceType.isNullable && !m.targetType.isNullable) {
                covered += m.sourceType.qualifiedName to m.targetType.qualifiedName
            }
        }
        for ((key, _) in sameModuleConverters.entries) {
            if (!key.sourceNullable && !key.targetNullable) {
                covered += key.sourceFqName to key.targetFqName
            }
        }
        return covered
    }

    private fun tryDeriveEnumDescriptor(
        sourceType: KSType,
        targetType: KSType,
        covered: Set<Pair<String, String>>,
        already: Map<Pair<String, String>, EnumMappingDescriptor>,
    ): EnumMappingDescriptor? {
        val candidate = validateEnumPair(sourceType, targetType, covered, already) ?: return null
        val sourceEntries = candidate.sourceDecl.enumEntryNames()
        val targetEntries = candidate.targetDecl.enumEntryNames().toSet()
        if (sourceEntries.any { it !in targetEntries }) return null

        return EnumMappingDescriptor(
            sourceType = TypeInfo.fromKSType(sourceType),
            targetType = TypeInfo.fromKSType(targetType),
            entries = sourceEntries.map { EnumEntryMapping(source = it, target = it) },
            // No KSClassDeclaration to anchor diagnostics at — derived
            // descriptors are synthetic. The enum source/target files
            // themselves still appear in originatingFiles via TypeInfo.
            declaration = null,
        )
    }

    private data class EnumPairCandidate(
        val sourceDecl: KSClassDeclaration,
        val targetDecl: KSClassDeclaration,
    )

    /**
     * Validates that [sourceType] and [targetType] form an auto-derivable enum
     * pair: both non-nullable, both same-module enum classes with distinct
     * qualified names, and not already covered by a user-declared `@MapEnum`
     * or hand-written `@KraftConverter`. Returns the resolved declarations on
     * success, `null` on any failed gate. Each gate's purpose is described
     * inline; collapsing them makes the validator denser but matches the
     * project's other multi-gate scanners (see [EnumMapScanner]).
     */
    @Suppress("ReturnCount")
    private fun validateEnumPair(
        sourceType: KSType,
        targetType: KSType,
        covered: Set<Pair<String, String>>,
        already: Map<Pair<String, String>, EnumMappingDescriptor>,
    ): EnumPairCandidate? {
        // Auto-derivation only handles non-nullable enum property pairs.
        // Nullable / platform property types require the user to declare
        // @MapEnum explicitly so the nullable-key shape is intentional.
        if (sourceType.nullability != Nullability.NOT_NULL) return null
        if (targetType.nullability != Nullability.NOT_NULL) return null

        val sourceDecl = sourceType.declaration as? KSClassDeclaration ?: return null
        val targetDecl = targetType.declaration as? KSClassDeclaration ?: return null
        if (!sourceDecl.isLocalEnum() || !targetDecl.isLocalEnum()) return null

        val sourceFq = sourceDecl.qualifiedName?.asString() ?: return null
        val targetFq = targetDecl.qualifiedName?.asString() ?: return null
        if (sourceFq == targetFq) return null

        val pairKey = sourceFq to targetFq
        if (pairKey in covered || pairKey in already.keys) return null

        return EnumPairCandidate(sourceDecl, targetDecl)
    }

    /**
     * `true` when [this] is an enum class declared in the current module.
     * KSP returns `null` for [KSClassDeclaration.containingFile] on classpath
     * declarations, so the file check scopes auto-derivation to the source
     * set under processing.
     */
    private fun KSClassDeclaration.isLocalEnum(): Boolean =
        classKind == ClassKind.ENUM_CLASS && containingFile != null
}
