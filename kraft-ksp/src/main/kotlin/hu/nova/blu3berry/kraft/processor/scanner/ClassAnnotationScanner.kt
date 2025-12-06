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

        resolver.getSymbolsWithAnnotation(MAP_FROM_FQ).forEach { symbol ->

            if (symbol !is KSClassDeclaration) {
                logger.annotationTargetError(
                    actualNode = symbol,
                    annotationName = MAP_FROM_FQ,
                    expectedTarget = CLASS
                )
                return@forEach
            }

            val ann = symbol.findAnnotation(MAP_FROM_FQ)
                ?: return@forEach

            val sourceType = ann.getKClassArgOrNull(
                name = VALUE,
                logger = logger,
                symbol = symbol,
                annotationFqName = MAP_FROM_FQ
            ) ?: return@forEach

            val propertyScanResults = scanPropertyAnnotations(symbol)

            results += ClassMappingScanResult(
                direction = MappingDirection.FROM,
                sourceType = sourceType.declaration as KSClassDeclaration,
                targetType = symbol,
                annotatedClass = symbol,
                propertyScanResults = propertyScanResults
            )
        }

        // ---- @MapTo ----
        resolver.getSymbolsWithAnnotation(MAP_TO_FQ).forEach { symbol ->

            if (symbol !is KSClassDeclaration) {
                logger.annotationTargetError(
                    actualNode = symbol,
                    annotationName = MAP_TO_FQ,
                    expectedTarget = CLASS
                )
                return@forEach
            }

            val ann = symbol.findAnnotation(MAP_TO_FQ)
                ?: return@forEach

            val targetType = ann.getKClassArgOrNull(
            name = VALUE,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_TO_FQ
        ) ?: return@forEach

            val propertyScanResults = scanPropertyAnnotations(symbol)

            results += ClassMappingScanResult(
                direction = MappingDirection.TO,
                sourceType = symbol,
                targetType = targetType.declaration as KSClassDeclaration,
                annotatedClass = symbol,
                propertyScanResults = propertyScanResults
            )
        }

        return results
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
