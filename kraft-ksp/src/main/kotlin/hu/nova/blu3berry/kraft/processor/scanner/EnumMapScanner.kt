package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.config.MapEnum
import hu.nova.blu3berry.kraft.model.EnumEntryMapping
import hu.nova.blu3berry.kraft.model.EnumMappingDescriptor
import hu.nova.blu3berry.kraft.model.TypeInfo
import hu.nova.blu3berry.kraft.processor.util.KraftKspConstants
import hu.nova.blu3berry.kraft.processor.util.annotationTargetError
import hu.nova.blu3berry.kraft.processor.util.findAnnotation
import hu.nova.blu3berry.kraft.processor.util.getKClassArgOrNull
import hu.nova.blu3berry.kraft.processor.util.unmappedEnumEntries


/**
 * Scanner for finding and processing @MapEnum annotations.
 */
class EnumMapScanner(
    protected val resolver: Resolver,
    protected val logger: KSPLogger
) {

    companion object {
        val ENUM_MAP_FQ = MapEnum::class.qualifiedName!!
    }

    /**
     * Scan all @MapEnum annotations in the project.
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
     * Build a descriptor for a single @MapEnum annotated class/object.
     *
     * @param decl The class declaration annotated with @MapEnum
     * @return An EnumMappingDescriptor if the mapping is valid, null otherwise
     */
    protected fun buildDescriptor(decl: KSClassDeclaration): EnumMappingDescriptor? {

        val annotation = decl.findAnnotation(ENUM_MAP_FQ) ?: return null

        // ---- get from = X::class ----
        val fromKSType = annotation.getKClassArgOrNull(
            name = KraftKspConstants.ARG_SOURCE,
            logger = logger,
            symbol = decl,
            annotationFqName = ENUM_MAP_FQ
        ) ?: return null

        // ---- get to = Y::class ----
        val toKSType = annotation.getKClassArgOrNull(
            name = KraftKspConstants.ARG_TARGET,
            logger = logger,
            symbol = decl,
            annotationFqName = ENUM_MAP_FQ
        ) ?: return null

        val fromDecl = fromKSType.declaration as? KSClassDeclaration
        val toDecl = toKSType.declaration as? KSClassDeclaration

        if (fromDecl == null || toDecl == null) {
            logger.error("@MapEnum 'from' and 'to' must reference enum classes.", decl)
            return null
        }

        if (fromDecl.classKind != ClassKind.ENUM_CLASS ||
            toDecl.classKind != ClassKind.ENUM_CLASS
        ) {
            logger.error("@MapEnum supports only mapping between enum classes.", decl)
            return null
        }

        val fromEntries = getEnumEntries(fromDecl)
        val toEntries = getEnumEntries(toDecl)
        val toEntriesSet = toEntries.toSet()

        // ---- read fieldMappings = [FieldMapping("A","B"), ...] ----
        val customMappings: List<EnumEntryMapping> = extractCustomMappings(annotation, fromEntries, toEntries, decl)
        val allMappings = customMappings.toMutableList()
        val mappedSources = customMappings.mapTo(mutableSetOf()) { it.source }
        val autoMappedEntries = mutableListOf<String>()

        // ---- add default 1:1 mappings for matching names ----
        for (sourceName in fromEntries) {
            if (sourceName in mappedSources) continue
            if (sourceName in toEntriesSet) {
                allMappings += EnumEntryMapping(sourceName, sourceName)
                mappedSources += sourceName
                autoMappedEntries += sourceName
            }
        }

        // ---- every source entry must be accounted for ----
        val unmappedEntries = fromEntries.filter { name -> name !in mappedSources }
        if (unmappedEntries.isNotEmpty()) {
            logger.unmappedEnumEntries(
                declaringClass = decl.simpleName.asString(),
                sourceQualifiedName = fromDecl.qualifiedName?.asString() ?: fromDecl.simpleName.asString(),
                targetQualifiedName = toDecl.qualifiedName?.asString() ?: toDecl.simpleName.asString(),
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
     * Parse @MapEnum.fieldMappings entries.
     *
     * @param annotation The @MapEnum annotation
     * @param fromEntries List of source enum entry names
     * @param toEntries List of target enum entry names
     * @param decl The class declaration annotated with @MapEnum
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
            .firstOrNull { it.name?.asString() == KraftKspConstants.ARG_FIELD_MAPPINGS }
            ?.value as? List<*>
            ?: return results

        for (pairAnn in arg) {
            val ann = pairAnn as? KSAnnotation ?: continue

            val from = ann.arguments.firstOrNull { it.name?.asString() == KraftKspConstants.ARG_SOURCE }?.value as? String
            if (from == null) {
                logger.error("@MapEnum: malformed @FieldMapping annotation — missing or non-String 'from' argument.", decl)
                continue
            }

            val to = ann.arguments.firstOrNull { it.name?.asString() == KraftKspConstants.ARG_TARGET }?.value as? String
            if (to == null) {
                logger.error("@MapEnum: malformed @FieldMapping annotation — missing or non-String 'to' argument.", decl)
                continue
            }

            if (from !in fromEntries) {
                logger.error("@MapEnum: '$from' is not a value of source enum.", decl)
            }

            if (to !in toEntries) {
                logger.error("@MapEnum: '$to' is not a value of target enum.", decl)
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