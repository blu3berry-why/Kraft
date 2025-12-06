package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.config.EnumMap
import hu.nova.blu3berry.kraft.model.EnumEntryMapping
import hu.nova.blu3berry.kraft.model.EnumMappingDescriptor
import hu.nova.blu3berry.kraft.model.TypeInfo
import hu.nova.blu3berry.kraft.processor.util.annotationTargetError
import hu.nova.blu3berry.kraft.processor.util.findAnnotation
import hu.nova.blu3berry.kraft.processor.util.getKClassArgOrNull

class EnumMapScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {

    companion object {
        const val CLASS = "class"
        val ENUM_MAP_FQ = EnumMap::class.qualifiedName!!
    }

    /**
     * Scan all @EnumMap annotations in the project
     */
    fun scan(): List<EnumMappingDescriptor> {
        val results = mutableListOf<EnumMappingDescriptor>()

        resolver
            .getSymbolsWithAnnotation(ENUM_MAP_FQ)
            .forEach { symbol ->

                if (symbol !is KSClassDeclaration) {
                    logger.annotationTargetError(
                        annotationName = ENUM_MAP_FQ,
                        expectedTarget = CLASS,
                        actualNode = symbol
                    )
                    return@forEach
                }

                buildDescriptor(symbol)?.let(results::add)
            }

        return results
    }

    /**
     * Build a descriptor for a single @EnumMap annotated class/object
     */
    private fun buildDescriptor(decl: KSClassDeclaration): EnumMappingDescriptor? {

        val annotation = decl.findAnnotation(ENUM_MAP_FQ) ?: return null

        // ---- get from = X::class ----
        val fromKSType = annotation.getKClassArgOrNull(
            name = "from",
            logger = logger,
            symbol = decl,
            annotationFqName = ENUM_MAP_FQ
        ) ?: return null

        // ---- get to = Y::class ----
        val toKSType = annotation.getKClassArgOrNull(
            name = "to",
            logger = logger,
            symbol = decl,
            annotationFqName = ENUM_MAP_FQ
        ) ?: return null

        val fromDecl = fromKSType.declaration as? KSClassDeclaration
        val toDecl = toKSType.declaration as? KSClassDeclaration

        if (fromDecl == null || toDecl == null) {
            logger.error("@EnumMap 'from' and 'to' must reference enum classes.", decl)
            return null
        }

        if (fromDecl.classKind != ClassKind.ENUM_CLASS ||
            toDecl.classKind != ClassKind.ENUM_CLASS
        ) {
            logger.error("@EnumMap supports only mapping between enum classes.", decl)
            return null
        }

        val fromEntries = getEnumEntries(fromDecl)
        val toEntries = getEnumEntries(toDecl)

        // ---- read fieldMapping = [StringPair("A","B"), ...] ----
        val customMappings = extractCustomMappings(annotation, fromEntries, toEntries, decl)

        // ---- add default 1:1 mappings for matching names ----
        for (sourceName in fromEntries) {
            if (customMappings.any { it.source == sourceName }) continue
            if (sourceName in toEntries) {
                customMappings += EnumEntryMapping(sourceName, sourceName)
            }
        }

        return EnumMappingDescriptor(
            sourceType = TypeInfo.fromKSType(fromKSType),
            targetType = TypeInfo.fromKSType(toKSType),
            entries = customMappings,
            allowDefault = false,
            defaultTarget = null
        )
    }

    /**
     * Parse @EnumMap.fieldMapping entries
     */
    private fun extractCustomMappings(
        annotation: KSAnnotation,
        fromEntries: List<String>,
        toEntries: List<String>,
        decl: KSClassDeclaration
    ): MutableList<EnumEntryMapping> {

        val results = mutableListOf<EnumEntryMapping>()

        val arg = annotation.arguments
            .firstOrNull { it.name?.asString() == "fieldMapping" }
            ?.value as? List<*>
            ?: return results

        for (pairAnn in arg) {
            val ann = pairAnn as KSAnnotation

            val from = ann.arguments.first { it.name?.asString() == "from" }.value as String
            val to = ann.arguments.first { it.name?.asString() == "to" }.value as String

            if (from !in fromEntries) {
                logger.error("EnumMap: '$from' is not a value of source enum.", decl)
            }

            if (to !in toEntries) {
                logger.error("EnumMap: '$to' is not a value of target enum.", decl)
            }

            results += EnumEntryMapping(source = from, target = to)
        }

        return results
    }

    /**
     * Extract enum entries using proper KSP approach.
     */
    private fun getEnumEntries(decl: KSClassDeclaration): List<String> =
        decl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { it.simpleName.asString() }
            .toList()
}
