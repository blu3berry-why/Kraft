package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.processing.KSPLogger
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
import com.blu3berry.kraft.processor.util.enumEntryNames

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
class AutoEnumMappingDeriver(
    private val logger: KSPLogger,
) {
    fun derive(
        classMappings: List<ClassMappingScanResult>,
        configMappings: List<ConfigObjectScanResult>,
        existingEnumMappings: List<EnumMappingDescriptor>,
        sameModuleConverters: GlobalConverterRegistry,
    ): List<EnumMappingDescriptor> {
        val pairs = collectParentClassPairs(classMappings, configMappings)
        if (pairs.isEmpty()) return emptyList()

        val covered = coveredPairs(existingEnumMappings, sameModuleConverters)
        val out = LinkedHashMap<Pair<String, String>, EnumMappingDescriptor>()

        for ((source, target) in pairs) {
            val targetProps = target.collectPropertyTypeRefs()
            val sourceProps = source.collectPropertyTypeRefs()
            for ((propName, targetProp) in targetProps) {
                val sourceProp = sourceProps[propName] ?: continue
                val descriptor = tryDeriveEnumDescriptor(
                    sourceProp, targetProp, covered, out
                ) ?: continue
                val key = descriptor.sourceType.qualifiedName to descriptor.targetType.qualifiedName
                out.putIfAbsent(key, descriptor)
            }
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
        // Auto-derivation only handles non-nullable enum property pairs.
        // Nullable property types require the user to declare @MapEnum
        // explicitly so the nullable-key shape is intentional. Platform
        // types are likewise out of scope (the same rule the rest of the
        // pipeline applies).
        if (sourceType.nullability != Nullability.NOT_NULL) return null
        if (targetType.nullability != Nullability.NOT_NULL) return null

        val sourceDecl = sourceType.declaration as? KSClassDeclaration ?: return null
        val targetDecl = targetType.declaration as? KSClassDeclaration ?: return null
        if (sourceDecl.classKind != ClassKind.ENUM_CLASS) return null
        if (targetDecl.classKind != ClassKind.ENUM_CLASS) return null
        if (sourceDecl.qualifiedName?.asString() == targetDecl.qualifiedName?.asString()) return null

        // Scope to the same module: KSP returns null containingFile for
        // declarations that live on the compile classpath. We only auto-derive
        // when both enums are in the current source set.
        if (sourceDecl.containingFile == null || targetDecl.containingFile == null) return null

        val sourceFq = sourceDecl.qualifiedName?.asString() ?: return null
        val targetFq = targetDecl.qualifiedName?.asString() ?: return null
        val pairKey = sourceFq to targetFq
        if (pairKey in covered) return null
        if (pairKey in already.keys) return null

        val sourceEntries = sourceDecl.enumEntryNames()
        val targetEntries = targetDecl.enumEntryNames().toSet()
        val unmappable = sourceEntries.filterNot { it in targetEntries }
        if (unmappable.isNotEmpty()) return null

        val sourceTypeInfo = TypeInfo.fromKSType(sourceType)
        val targetTypeInfo = TypeInfo.fromKSType(targetType)
        val entries = sourceEntries.map { EnumEntryMapping(source = it, target = it) }
        return EnumMappingDescriptor(
            sourceType = sourceTypeInfo,
            targetType = targetTypeInfo,
            entries = entries,
            // No KSClassDeclaration to anchor diagnostics at — derived
            // descriptors are synthetic. The enum source/target files
            // themselves still appear in originatingFiles via TypeInfo.
            declaration = null,
        )
    }

}
