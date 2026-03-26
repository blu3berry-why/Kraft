package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import hu.nova.blu3berry.kraft.model.scan.ClassMappingScanResult
import hu.nova.blu3berry.kraft.model.scan.MapNestedAnnotation
import hu.nova.blu3berry.kraft.model.descriptor.MappingDirection
import hu.nova.blu3berry.kraft.model.scan.PropertyScanResult
import hu.nova.blu3berry.kraft.processor.util.KraftKspConstants
import hu.nova.blu3berry.kraft.processor.util.annotationTargetError
import hu.nova.blu3berry.kraft.processor.util.findAnnotation
import hu.nova.blu3berry.kraft.processor.util.getKClassArgOrNull

class ClassAnnotationScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {

    fun scan(): List<ClassMappingScanResult> {
        val results = mutableListOf<ClassMappingScanResult>()

        // First, collect all symbols with either annotation and check if they are classes
        val allMapFromSymbols = resolver.getSymbolsWithAnnotation(KraftKspConstants.FQ_MAP_FROM).filter { it.validate() }
        val allMapToSymbols = resolver.getSymbolsWithAnnotation(KraftKspConstants.FQ_MAP_TO).filter { it.validate() }

        // Check for non-class elements with @MapFrom and show error
        allMapFromSymbols.forEach { symbol ->
            if (symbol !is KSClassDeclaration || symbol.classKind != ClassKind.CLASS) {
                logger.annotationTargetError(
                    actualNode = symbol,
                    annotationName = KraftKspConstants.FQ_MAP_FROM,
                    expectedTarget = KraftKspConstants.ARG_CLASS
                )
            }
        }

        // Check for non-class elements with @MapTo and show error
        allMapToSymbols.forEach { symbol ->
            if (symbol !is KSClassDeclaration || symbol.classKind != ClassKind.CLASS) {
                logger.annotationTargetError(
                    actualNode = symbol,
                    annotationName = KraftKspConstants.FQ_MAP_TO,
                    expectedTarget = KraftKspConstants.ARG_CLASS
                )
            }
        }

        // Filter to only include regular class declarations (not objects, interfaces, enums, etc.)
        val classesWithMapFrom = allMapFromSymbols
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.CLASS }
            .toSet()

        val classesWithMapTo = allMapToSymbols
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.CLASS }
            .toSet()

        // Find classes with both annotations
        val classesWithBothAnnotations = classesWithMapFrom.intersect(classesWithMapTo)

        // Report error for classes with both annotations
        classesWithBothAnnotations.forEach { classDeclaration ->
            logger.error(
                "Class ${classDeclaration.simpleName.asString()} has both @MapFrom and @MapTo annotations. " +
                "Only one mapping annotation is allowed per class.",
                classDeclaration
            )
        }

        // Process valid @MapFrom classes (excluding those with both annotations)
        (classesWithMapFrom - classesWithBothAnnotations).forEach { classDeclaration ->
            processMapFromClass(classDeclaration, results)
        }

        // Process valid @MapTo classes (excluding those with both annotations)
        (classesWithMapTo - classesWithBothAnnotations).forEach { classDeclaration ->
            processMapToClass(classDeclaration, results)
        }

        return results
    }

    private fun processMapFromClass(
        classDeclaration: KSClassDeclaration,
        results: MutableList<ClassMappingScanResult>
    ) {
        val ann = classDeclaration.findAnnotation(KraftKspConstants.FQ_MAP_FROM) ?: return

        val sourceType = ann.getKClassArgOrNull(
            name = KraftKspConstants.ARG_SOURCE,
            logger = logger,
            symbol = classDeclaration,
            annotationFqName = KraftKspConstants.FQ_MAP_FROM
        ) ?: return

        val propertyScanResults = scanPropertyAnnotations(classDeclaration)

        results += ClassMappingScanResult(
            direction = MappingDirection.MAP_FROM,
            sourceType = sourceType.declaration as KSClassDeclaration,
            targetType = classDeclaration,
            annotatedClass = classDeclaration,
            propertyScanResults = propertyScanResults
        )
    }

    private fun processMapToClass(
        classDeclaration: KSClassDeclaration,
        results: MutableList<ClassMappingScanResult>
    ) {
        val ann = classDeclaration.findAnnotation(KraftKspConstants.FQ_MAP_TO) ?: return

        val targetType = ann.getKClassArgOrNull(
            name = KraftKspConstants.ARG_TARGET,
            logger = logger,
            symbol = classDeclaration,
            annotationFqName = KraftKspConstants.FQ_MAP_TO
        ) ?: return

        val propertyScanResults = scanPropertyAnnotations(classDeclaration)

        results += ClassMappingScanResult(
            direction = MappingDirection.MAP_TO,
            sourceType = classDeclaration,
            targetType = targetType.declaration as KSClassDeclaration,
            annotatedClass = classDeclaration,
            propertyScanResults = propertyScanResults
        )
    }

    /**
     * Scan all declared properties of the annotated class for:
     *  - @MapField(counterPartName = "...")
     *  - @MapNested(sourceName = "...")
     *  - @MapIgnore
     */
    private fun scanPropertyAnnotations(
        klass: KSClassDeclaration
    ): List<PropertyScanResult> {

        val props = mutableListOf<PropertyScanResult>()

        for (prop in klass.getDeclaredProperties()) {
            val mapFieldAnn = prop.findAnnotation(KraftKspConstants.FQ_MAP_FIELD)
            val counterPartName: String? = mapFieldAnn
                ?.arguments
                ?.firstOrNull { it.name?.asString() == KraftKspConstants.ARG_OTHER_NAME }
                ?.value as? String

            val isIgnored = prop.findAnnotation(KraftKspConstants.FQ_MAP_IGNORE) != null

            val mapNestedAnn = prop.findAnnotation(KraftKspConstants.FQ_MAP_NESTED)
            val mapNested: MapNestedAnnotation = when {
                mapNestedAnn == null -> MapNestedAnnotation.NotAnnotated
                else -> {
                    val sourceName = mapNestedAnn.arguments
                        .firstOrNull { it.name?.asString() == KraftKspConstants.ARG_SOURCE_NAME }
                        ?.value as? String ?: ""
                    if (sourceName.isEmpty()) MapNestedAnnotation.SameName
                    else MapNestedAnnotation.Renamed(sourceName)
                }
            }

            props += PropertyScanResult(
                property = prop,
                mapFieldSourceName = counterPartName,
                isIgnored = isIgnored,
                mapNested = mapNested
            )
        }
        return props
    }
}
