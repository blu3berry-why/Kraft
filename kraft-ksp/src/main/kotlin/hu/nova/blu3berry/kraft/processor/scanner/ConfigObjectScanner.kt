package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.MapUsing
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.onclass.MapIgnore
import hu.nova.blu3berry.kraft.processor.util.*

class ConfigObjectScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {
    companion object {
        val MAP_CONFIG_FQ = MapConfig::class.qualifiedName!!

        val MAP_USING_FQ = MapUsing::class.qualifiedName!!
        val MAP_IGNORE_FQ = MapIgnore::class.qualifiedName!!
        val STRING_PAIR_FQ = hu.nova.blu3berry.kraft.config.StringPair::class.qualifiedName!!

        // annotation parameter names
        const val ARG_FROM = "from"
        const val ARG_TO = "to"
        const val ARG_FIELD_MAPPING = "fieldMapping"
        const val ARG_VALUE = "value"

        const val OBJECT = "object"
    }

    fun scan(): List<ConfigObjectScanResult> {
        val results = mutableListOf<ConfigObjectScanResult>()

        resolver.getSymbolsWithAnnotation(MAP_CONFIG_FQ).forEach { symbol ->

            // Validate annotation target: must be an object
            if (symbol !is KSClassDeclaration || symbol.classKind != ClassKind.OBJECT) {
                logger.annotationTargetError(
                    annotationName = MapConfig::class.simpleName!!,
                    expectedTarget = OBJECT,
                    actualNode = symbol
                )
                return@forEach
            }

            val ann = symbol.findAnnotation(MAP_CONFIG_FQ)
                ?: return@forEach

            // Extract "from" and "to"
            val fromKSType = ann.getKClassArgOrNull(
                name = ARG_FROM,
                logger = logger,
                symbol = symbol,
                annotationFqName = MAP_CONFIG_FQ
            ) ?: return@forEach

            val toKSType = ann.getKClassArgOrNull(
                name = ARG_TO,
                logger = logger,
                symbol = symbol,
                annotationFqName = MAP_CONFIG_FQ
            ) ?: return@forEach

            val fromType = fromKSType.declaration as KSClassDeclaration
            val toType = toKSType.declaration as KSClassDeclaration

            // Read fieldMapping = [StringPair(...)]
            val fieldPairAnnotations = ann.getArrayArgOrNull<KSAnnotation>(
                name = ARG_FIELD_MAPPING,
                logger = logger,
                symbol = symbol,
                annotationFqName = MAP_CONFIG_FQ
            ) ?: emptyList()

            val fieldOverrides = fieldPairAnnotations.mapNotNull { pair ->
                if (!pair.isAnnotation(STRING_PAIR_FQ)) return@mapNotNull null

                val from = pair.getStringArgOrNull(
                    name = ARG_FROM,
                    logger = logger,
                    symbol = symbol,
                    annotationFqName = STRING_PAIR_FQ
                ) ?: return@mapNotNull null

                val to = pair.getStringArgOrNull(
                    name = ARG_TO,
                    logger = logger,
                    symbol = symbol,
                    annotationFqName = STRING_PAIR_FQ
                ) ?: return@mapNotNull null

                FieldOverride(from=from, to=to)
            }

            // MapUsing functions
            val converterFunctions = symbol.getDeclaredFunctions().filter { fn ->
                fn.annotations.any { it.isAnnotation(MAP_USING_FQ) }
            }

            // MapIgnore fields
            val ignoredFields = symbol.getDeclaredProperties().mapNotNull { prop ->
                val ignoreAnn = prop.annotations.firstOrNull { it.isAnnotation(MAP_IGNORE_FQ) }
                if (ignoreAnn != null) {
                    ignoreAnn.getStringArgOrNull(
                        name = ARG_VALUE,
                        logger = logger,
                        symbol = prop,
                        annotationFqName = MAP_IGNORE_FQ
                    ) ?: prop.simpleName.asString()
                } else null
            }

            results += ConfigObjectScanResult(
                fromType = fromType,
                toType = toType,
                configObject = symbol,
                fieldOverrides = fieldOverrides,
                ignoredFields = ignoredFields.toList(),
                converterFunctions = converterFunctions.toList()
            )
        }

        return results
    }
}

private fun KSAnnotation.isAnnotation(fqName: String): Boolean {
    return annotationType
        .resolve()
        .declaration
        .qualifiedName
        ?.asString() == fqName
}
