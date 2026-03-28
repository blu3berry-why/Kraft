package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.blu3berry.kraft.model.scan.ClassMappingScanResult
import com.blu3berry.kraft.model.scan.MapNestedAnnotation
import com.blu3berry.kraft.model.descriptor.MappingDirection
import com.blu3berry.kraft.model.scan.PropertyScanResult
import com.blu3berry.kraft.processor.util.KraftKspConstants
import com.blu3berry.kraft.processor.util.annotationTargetError
import com.blu3berry.kraft.processor.util.findAnnotation
import com.blu3berry.kraft.processor.util.getKClassArgOrNull

class ClassAnnotationScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {

    fun scan(): List<ClassMappingScanResult> {
        val classesWithMapFrom = collectAndValidateAnnotated(
            KraftKspConstants.FQ_MAP_FROM
        )
        val classesWithMapTo = collectAndValidateAnnotated(
            KraftKspConstants.FQ_MAP_TO
        )
        val classesWithMapReverse = collectAndValidateReverse(
            classesWithMapFrom, classesWithMapTo
        )

        val bothAnnotations = classesWithMapFrom.intersect(classesWithMapTo)
        bothAnnotations.forEach { decl ->
            logger.error(
                "Class ${decl.simpleName.asString()} has both " +
                    "@MapFrom and @MapTo annotations. " +
                    "Only one mapping annotation is allowed per class.",
                decl
            )
        }

        val results = mutableListOf<ClassMappingScanResult>()

        (classesWithMapFrom - bothAnnotations).forEach { decl ->
            processMapFromClass(
                decl, decl in classesWithMapReverse, results
            )
        }
        (classesWithMapTo - bothAnnotations).forEach { decl ->
            processMapToClass(
                decl, decl in classesWithMapReverse, results
            )
        }

        return results
    }

    private fun collectAndValidateAnnotated(
        annotationFq: String
    ): Set<KSClassDeclaration> {
        val symbols = resolver
            .getSymbolsWithAnnotation(annotationFq)
            .filter { it.validate() }

        symbols.forEach { symbol ->
            if (symbol !is KSClassDeclaration ||
                symbol.classKind != ClassKind.CLASS
            ) {
                logger.annotationTargetError(
                    actualNode = symbol,
                    annotationName = annotationFq,
                    expectedTarget = KraftKspConstants.ARG_CLASS
                )
            }
        }

        return symbols
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.CLASS }
            .toSet()
    }

    private fun collectAndValidateReverse(
        classesWithMapFrom: Set<KSClassDeclaration>,
        classesWithMapTo: Set<KSClassDeclaration>
    ): Set<KSClassDeclaration> {
        val symbols = resolver
            .getSymbolsWithAnnotation(KraftKspConstants.FQ_MAP_REVERSE)
            .filter { it.validate() }

        // @MapReverse is valid on CLASS (@MapFrom/@MapTo) and OBJECT (@MapConfig)
        symbols.forEach { symbol ->
            if (symbol !is KSClassDeclaration ||
                (symbol.classKind != ClassKind.CLASS &&
                    symbol.classKind != ClassKind.OBJECT)
            ) {
                logger.annotationTargetError(
                    actualNode = symbol,
                    annotationName = KraftKspConstants.FQ_MAP_REVERSE,
                    expectedTarget = KraftKspConstants.ARG_CLASS
                )
            }
        }

        val classesWithReverse = symbols
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.CLASS }
            .toSet()

        val orphaned = classesWithReverse -
            classesWithMapFrom - classesWithMapTo
        orphaned.forEach { decl ->
            logger.error(
                "@MapReverse on '${decl.simpleName.asString()}' " +
                    "requires @MapFrom or @MapTo on the same class.",
                decl
            )
        }

        return classesWithReverse
    }

    private fun processMapFromClass(
        classDeclaration: KSClassDeclaration,
        hasReverse: Boolean,
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
            propertyScanResults = propertyScanResults,
            hasReverse = hasReverse
        )
    }

    private fun processMapToClass(
        classDeclaration: KSClassDeclaration,
        hasReverse: Boolean,
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
            propertyScanResults = propertyScanResults,
            hasReverse = hasReverse
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
