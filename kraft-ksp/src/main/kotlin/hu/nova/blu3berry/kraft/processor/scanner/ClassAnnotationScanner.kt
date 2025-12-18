package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.model.ClassMappingScanResult
import hu.nova.blu3berry.kraft.model.MappingDirection
import hu.nova.blu3berry.kraft.model.PropertyScanResult
import hu.nova.blu3berry.kraft.onclass.from.MapField
import hu.nova.blu3berry.kraft.onclass.from.MapFrom
import hu.nova.blu3berry.kraft.onclass.to.MapTo
import hu.nova.blu3berry.kraft.processor.util.annotationTargetError
import hu.nova.blu3berry.kraft.processor.util.findAnnotation
import hu.nova.blu3berry.kraft.processor.util.getKClassArgOrNull

class ClassAnnotationScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {
    companion object {
        const val CLASS = "class"
        const val VALUE = "value"
        val MAP_FROM_FQ = MapFrom::class.qualifiedName!!
        val MAP_TO_FQ = MapTo::class.qualifiedName!!
        val MAP_FIELD_FQ = MapField::class.qualifiedName!!
    }

    fun scan(): List<ClassMappingScanResult> {
        val results = mutableListOf<ClassMappingScanResult>()

        // First, collect all symbols with either annotation and check if they are classes
        val allMapFromSymbols = resolver.getSymbolsWithAnnotation(MAP_FROM_FQ)
        val allMapToSymbols = resolver.getSymbolsWithAnnotation(MAP_TO_FQ)

        // Check for non-class elements with @MapFrom and show error
        allMapFromSymbols.forEach { symbol ->
            if (symbol !is KSClassDeclaration) {
                logger.annotationTargetError(
                    actualNode = symbol,
                    annotationName = MAP_FROM_FQ,
                    expectedTarget = CLASS
                )
            }
        }

        // Check for non-class elements with @MapTo and show error
        allMapToSymbols.forEach { symbol ->
            if (symbol !is KSClassDeclaration) {
                logger.annotationTargetError(
                    actualNode = symbol,
                    annotationName = MAP_TO_FQ,
                    expectedTarget = CLASS
                )
            }
        }

        // Filter to only include class declarations
        val classesWithMapFrom = allMapFromSymbols
            .filterIsInstance<KSClassDeclaration>()
            .toSet()

        val classesWithMapTo = allMapToSymbols
            .filterIsInstance<KSClassDeclaration>()
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
        val ann = classDeclaration.findAnnotation(MAP_FROM_FQ) ?: return

        val sourceType = ann.getKClassArgOrNull(
            name = VALUE,
            logger = logger,
            symbol = classDeclaration,
            annotationFqName = MAP_FROM_FQ
        ) ?: return

        val propertyScanResults = scanPropertyAnnotations(classDeclaration)

        results += ClassMappingScanResult(
            direction = MappingDirection.FROM,
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
        val ann = classDeclaration.findAnnotation(MAP_TO_FQ) ?: return

        val targetType = ann.getKClassArgOrNull(
            name = VALUE,
            logger = logger,
            symbol = classDeclaration,
            annotationFqName = MAP_TO_FQ
        ) ?: return

        val propertyScanResults = scanPropertyAnnotations(classDeclaration)

        results += ClassMappingScanResult(
            direction = MappingDirection.TO,
            sourceType = classDeclaration,
            targetType = targetType.declaration as KSClassDeclaration,
            annotatedClass = classDeclaration,
            propertyScanResults = propertyScanResults
        )
    }

    /**
     * Scan all declared properties of the annotated class for:
     *  - @MapField(otherName = "sourceName")
     *  - (later: @MapIgnore, etc.)
     */
    private fun scanPropertyAnnotations(
        klass: KSClassDeclaration
    ): List<PropertyScanResult> {

        val props = mutableListOf<PropertyScanResult>()

        for (prop in klass.getDeclaredProperties()) {
            val mapFieldAnn = prop.findAnnotation(MAP_FIELD_FQ)
            val otherName: String? = mapFieldAnn
                ?.arguments
                ?.firstOrNull { it.name?.asString() == "otherName" }
                ?.value as? String

            // TODO: if you later add @MapIgnore for class-level, set isIgnored = true
            val isIgnored = false

            props += PropertyScanResult(
                property = prop,
                mapFieldOther = otherName,
                isIgnored = isIgnored
            )
        }


        return props
    }

}
