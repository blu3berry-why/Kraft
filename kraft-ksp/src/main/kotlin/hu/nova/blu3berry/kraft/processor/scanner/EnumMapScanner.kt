package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.config.EnumMap
import hu.nova.blu3berry.kraft.model.EnumEntryMapping
import hu.nova.blu3berry.kraft.model.EnumMappingDescriptor
import hu.nova.blu3berry.kraft.model.TypeInfo
import hu.nova.blu3berry.kraft.processor.util.KraftKspConstants
import hu.nova.blu3berry.kraft.processor.util.annotationTargetError
import hu.nova.blu3berry.kraft.processor.util.findAnnotation
import hu.nova.blu3berry.kraft.processor.util.getKClassArgOrNull
import hu.nova.blu3berry.kraft.processor.util.unmappedEnumEntries


/**
 * Scanner for finding and processing @EnumMap annotations.
 */
class EnumMapScanner(
    protected val resolver: Resolver,
    protected val logger: KSPLogger
) {

    companion object {
        val ENUM_MAP_FQ = EnumMap::class.qualifiedName!!
    }

    /**
     * Scan all @EnumMap annotations in the project.
     *
     * @return List of EnumMappingDescriptor objects for each valid mapping
     */
    fun scan(): List<EnumMappingDescriptor> {
        val results = mutableListOf<EnumMappingDescriptor>()

        resolver
            .getSymbolsWithAnnotation(ENUM_MAP_FQ)
            .forEach { symbol ->

                if (symbol !is KSClassDeclaration) {
                    logger.annotationTargetError(
                        annotationName = ENUM_MAP_FQ,
                        expectedTarget = KraftKspConstants.ARG_CLASS,
                        actualNode = symbol
                    )
                    return@forEach
                }

                buildDescriptor(symbol)?.let(results::add)
            }

        return results
    }

    /**
     * Build a descriptor for a single @EnumMap annotated class/object.
     *
     * @param decl The class declaration annotated with @EnumMap
     * @return An EnumMappingDescriptor if the mapping is valid, null otherwise
     */
    protected fun buildDescriptor(decl: KSClassDeclaration): EnumMappingDescriptor? {

        val annotation = decl.findAnnotation(ENUM_MAP_FQ) ?: return null

        // ---- get from = X::class ----
        val fromKSType = annotation.getKClassArgOrNull(
            name = KraftKspConstants.ARG_FROM,
            logger = logger,
            symbol = decl,
            annotationFqName = ENUM_MAP_FQ
        ) ?: return null

        // ---- get to = Y::class ----
        val toKSType = annotation.getKClassArgOrNull(
            name = KraftKspConstants.ARG_TO,
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

        // ---- read fieldMapping = [FieldOverride("A","B"), ...] ----
        val customMappings = extractCustomMappings(annotation, fromEntries, toEntries, decl)
        val allMappings = customMappings.toMutableList()
        val autoMappedEntries = mutableListOf<String>()

        // ---- add default 1:1 mappings for matching names ----
        for (sourceName in fromEntries) {
            if (allMappings.any { it.source == sourceName }) continue
            if (sourceName in toEntries) {
                allMappings += EnumEntryMapping(sourceName, sourceName)
                autoMappedEntries += sourceName
            }
        }

        // ---- every source entry must be accounted for ----
        val unmappedEntries = fromEntries.filter { name -> allMappings.none { it.source == name } }
        if (unmappedEntries.isNotEmpty()) {
            logger.unmappedEnumEntries(
                declaringClass = decl.simpleName.asString(),
                fromQualifiedName = fromDecl.qualifiedName?.asString() ?: fromDecl.simpleName.asString(),
                toQualifiedName = toDecl.qualifiedName?.asString() ?: toDecl.simpleName.asString(),
                fromSimpleName = fromDecl.simpleName.asString(),
                toSimpleName = toDecl.simpleName.asString(),
                unmappedEntries = unmappedEntries,
                customEntries = customMappings.map { it.source to it.target },
                autoEntries = autoMappedEntries,
                availableTargetEntries = toEntries,
                symbol = decl
            )
            return null
        }

        return EnumMappingDescriptor(
            sourceType = TypeInfo.fromKSType(fromKSType),
            targetType = TypeInfo.fromKSType(toKSType),
            entries = allMappings,
            allowDefault = false,
            defaultTarget = null
        )
    }

    /**
     * Parse @EnumMap.fieldMapping entries.
     *
     * @param annotation The @EnumMap annotation
     * @param fromEntries List of source enum entry names
     * @param toEntries List of target enum entry names
     * @param decl The class declaration annotated with @EnumMap
     * @return List of EnumEntryMapping objects for custom mappings
     */
    protected fun extractCustomMappings(
        annotation: KSAnnotation,
        fromEntries: List<String>,
        toEntries: List<String>,
        decl: KSClassDeclaration
    ): MutableList<EnumEntryMapping> {

        val results = mutableListOf<EnumEntryMapping>()

        val arg = annotation.arguments
            .firstOrNull { it.name?.asString() == KraftKspConstants.ARG_FIELD_MAPPING }
            ?.value as? List<*>
            ?: return results

        for (pairAnn in arg) {
            val ann = pairAnn as? KSAnnotation ?: continue

            val from = ann.arguments.firstOrNull { it.name?.asString() == KraftKspConstants.ARG_FROM }?.value as? String
            if (from == null) {
                logger.error("EnumMap: malformed @FieldOverride annotation — missing or non-String 'from' argument.", decl)
                continue
            }

            val to = ann.arguments.firstOrNull { it.name?.asString() == KraftKspConstants.ARG_TO }?.value as? String
            if (to == null) {
                logger.error("EnumMap: malformed @FieldOverride annotation — missing or non-String 'to' argument.", decl)
                continue
            }

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
     *
     * @param decl The enum class declaration
     * @return A list of enum entry names
     */
    protected fun getEnumEntries(decl: KSClassDeclaration): List<String> =
        decl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { it.simpleName.asString() }
            .toList()
}