package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.blu3berry.kraft.model.descriptor.EnumEntryMapping
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.processor.util.KraftKspConstants
import com.blu3berry.kraft.processor.util.annotationTargetError
import com.blu3berry.kraft.processor.util.findAnnotation
import com.blu3berry.kraft.processor.util.getKClassArgOrNull
import com.blu3berry.kraft.processor.util.unmappedEnumEntries

/**
 * Scanner for finding and processing @MapEnum annotations.
 */
class EnumMapScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {

    /**
     * Scan all @MapEnum annotations in the project.
     *
     * @return List of EnumMappingDescriptor objects for each valid mapping
     */
    fun scan(): List<EnumMappingDescriptor> {
        val results = mutableListOf<EnumMappingDescriptor>()

        resolver
            .getSymbolsWithAnnotation(KraftKspConstants.FQ_MAP_ENUM)
            .filter { it.validate() }
            .forEach { symbol ->

                if (symbol !is KSClassDeclaration) {
                    logger.annotationTargetError(
                        annotationName = KraftKspConstants.FQ_MAP_ENUM,
                        expectedTarget = KraftKspConstants.ARG_CLASS,
                        actualNode = symbol
                    )
                    return@forEach
                }

                results += buildDescriptors(symbol)
            }

        return results
    }

    /**
     * Build descriptors for a single `@MapEnum`-annotated class/object —
     * the forward direction always, plus the reverse direction when
     * `@MapReverse` is on the same declaration.
     *
     * For the reverse, explicit `FieldMapping` entries are inverted
     * (`source` ↔ `target`) and the same auto-by-same-name logic that the
     * forward uses fills in the rest. Uncovered reverse-source entries
     * produce a compile-time error, mirroring the forward.
     */
    private fun buildDescriptors(
        decl: KSClassDeclaration
    ): List<EnumMappingDescriptor> {
        val annotation = decl.findAnnotation(
            KraftKspConstants.FQ_MAP_ENUM
        ) ?: return emptyList()

        val enumPair = resolveEnumPair(annotation, decl) ?: return emptyList()

        val fromEntries = getEnumEntries(enumPair.fromDecl)
        val toEntries = getEnumEntries(enumPair.toDecl)

        val customMappings = extractCustomMappings(
            annotation, fromEntries, toEntries, decl
        )

        val forwardEntries = buildAllMappings(
            enumPair, fromEntries, toEntries, customMappings, decl
        ) ?: return emptyList()

        val forward = EnumMappingDescriptor(
            sourceType = TypeInfo.fromKSType(enumPair.sourceKSType),
            targetType = TypeInfo.fromKSType(enumPair.targetKSType),
            entries = forwardEntries,
        )

        val reverse = buildReverseDescriptor(
            decl, enumPair, fromEntries, toEntries, customMappings
        )
        return listOfNotNull(forward, reverse)
    }

    private fun buildReverseDescriptor(
        decl: KSClassDeclaration,
        enumPair: EnumPair,
        fromEntries: List<String>,
        toEntries: List<String>,
        customMappings: List<EnumEntryMapping>
    ): EnumMappingDescriptor? {
        if (decl.findAnnotation(KraftKspConstants.FQ_MAP_REVERSE) == null) return null
        val reverseCustom = customMappings
            .map { EnumEntryMapping(source = it.target, target = it.source) }
        val reverseEntries = buildAllMappings(
            enumPair.swapped(), toEntries, fromEntries, reverseCustom, decl
        ) ?: return null
        return EnumMappingDescriptor(
            sourceType = TypeInfo.fromKSType(enumPair.targetKSType),
            targetType = TypeInfo.fromKSType(enumPair.sourceKSType),
            entries = reverseEntries,
        )
    }

    private data class EnumPair(
        val sourceKSType: com.google.devtools.ksp.symbol.KSType,
        val targetKSType: com.google.devtools.ksp.symbol.KSType,
        val fromDecl: KSClassDeclaration,
        val toDecl: KSClassDeclaration
    ) {
        fun swapped(): EnumPair = EnumPair(
            sourceKSType = targetKSType,
            targetKSType = sourceKSType,
            fromDecl = toDecl,
            toDecl = fromDecl
        )
    }

    @Suppress("ReturnCount")
    private fun resolveEnumPair(
        annotation: KSAnnotation,
        decl: KSClassDeclaration
    ): EnumPair? {
        val sourceKSType = annotation.getKClassArgOrNull(
            name = KraftKspConstants.ARG_SOURCE,
            logger = logger, symbol = decl,
            annotationFqName = KraftKspConstants.FQ_MAP_ENUM
        ) ?: return null

        val targetKSType = annotation.getKClassArgOrNull(
            name = KraftKspConstants.ARG_TARGET,
            logger = logger, symbol = decl,
            annotationFqName = KraftKspConstants.FQ_MAP_ENUM
        ) ?: return null

        val fromDecl = sourceKSType.declaration as? KSClassDeclaration
        val toDecl = targetKSType.declaration as? KSClassDeclaration

        if (fromDecl == null || toDecl == null) {
            logger.error(
                "@MapEnum 'source' and 'target' must reference enum classes.",
                decl
            )
            return null
        }

        if (fromDecl.classKind != ClassKind.ENUM_CLASS ||
            toDecl.classKind != ClassKind.ENUM_CLASS
        ) {
            logger.error(
                "@MapEnum supports only mapping between enum classes.",
                decl
            )
            return null
        }

        return EnumPair(sourceKSType, targetKSType, fromDecl, toDecl)
    }

    private fun buildAllMappings(
        enumPair: EnumPair,
        fromEntries: List<String>,
        toEntries: List<String>,
        customMappings: List<EnumEntryMapping>,
        decl: KSClassDeclaration
    ): List<EnumEntryMapping>? {
        val toEntriesSet = toEntries.toSet()

        val duplicateSources = customMappings.groupBy { it.source }
            .filter { it.value.size > 1 }
            .keys
        if (duplicateSources.isNotEmpty()) {
            logger.error(
                "@MapEnum has duplicate source entries: ${duplicateSources.sorted()}. " +
                    "Each source enum entry may only appear once in fieldMappings.",
                decl
            )
            return null
        }
        val allMappings = customMappings.toMutableList()
        val mappedSources = customMappings
            .mapTo(mutableSetOf()) { it.source }
        val autoMappedEntries = mutableListOf<String>()

        for (sourceName in fromEntries) {
            if (sourceName in mappedSources) continue
            if (sourceName in toEntriesSet) {
                allMappings += EnumEntryMapping(sourceName, sourceName)
                mappedSources += sourceName
                autoMappedEntries += sourceName
            }
        }

        val unmappedEntries = fromEntries
            .filter { name -> name !in mappedSources }
        if (unmappedEntries.isNotEmpty()) {
            logger.unmappedEnumEntries(
                declaringClass = decl.simpleName.asString(),
                sourceQualifiedName = enumPair.fromDecl
                    .qualifiedName?.asString()
                    ?: enumPair.fromDecl.simpleName.asString(),
                targetQualifiedName = enumPair.toDecl
                    .qualifiedName?.asString()
                    ?: enumPair.toDecl.simpleName.asString(),
                fromSimpleName = enumPair.fromDecl
                    .simpleName.asString(),
                toSimpleName = enumPair.toDecl
                    .simpleName.asString(),
                unmappedEntries = unmappedEntries,
                customEntries = customMappings
                    .map { it.source to it.target },
                autoEntries = autoMappedEntries,
                availableTargetEntries = toEntries,
                symbol = decl
            )
            return null
        }

        return allMappings
    }

    /**
     * Parse @MapEnum.fieldMappings entries.
     *
     * @param annotation The @MapEnum annotation
     * @param fromEntries List of source enum entry names
     * @param toEntries List of target enum entry names
     * @param decl The class declaration annotated with @MapEnum
     * @return List of EnumEntryMapping objects for custom mappings
     */
    private fun extractCustomMappings(
        annotation: KSAnnotation,
        fromEntries: List<String>,
        toEntries: List<String>,
        decl: KSClassDeclaration
    ): List<EnumEntryMapping> {
        val arg = annotation.arguments
            .firstOrNull {
                it.name?.asString() == KraftKspConstants.ARG_FIELD_MAPPINGS
            }
            ?.value as? List<*>
            ?: return emptyList()

        return arg
            .filterIsInstance<KSAnnotation>()
            .mapNotNull { ann ->
                parseFieldMapping(ann, fromEntries, toEntries, decl)
            }
    }

    private fun parseFieldMapping(
        ann: KSAnnotation,
        fromEntries: List<String>,
        toEntries: List<String>,
        decl: KSClassDeclaration
    ): EnumEntryMapping? {
        val from = ann.arguments
            .firstOrNull {
                it.name?.asString() == KraftKspConstants.ARG_SOURCE
            }
            ?.value as? String
        if (from == null) {
            logger.error(
                "@MapEnum: malformed @FieldMapping annotation" +
                    " — missing or non-String 'source' argument.",
                decl
            )
            return null
        }

        val to = ann.arguments
            .firstOrNull {
                it.name?.asString() == KraftKspConstants.ARG_TARGET
            }
            ?.value as? String
        if (to == null) {
            logger.error(
                "@MapEnum: malformed @FieldMapping annotation" +
                    " — missing or non-String 'target' argument.",
                decl
            )
            return null
        }

        if (from !in fromEntries) {
            logger.error(
                "@MapEnum: '$from' is not a value of source enum.",
                decl
            )
        }

        if (to !in toEntries) {
            logger.error(
                "@MapEnum: '$to' is not a value of target enum.",
                decl
            )
        }

        return EnumEntryMapping(source = from, target = to)
    }

    /**
     * Extract enum entries using proper KSP approach.
     *
     * @param decl The enum class declaration
     * @return A list of enum entry names
     */
    private fun getEnumEntries(decl: KSClassDeclaration): List<String> =
        decl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { it.simpleName.asString() }
            .toList()
}
