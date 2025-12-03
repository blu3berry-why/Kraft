package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.MapUsing
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.model.ConverterDescriptor
import hu.nova.blu3berry.kraft.model.TypeInfo
import hu.nova.blu3berry.kraft.model.toTypeInfo
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

            // MapUsing functions - collect and validate
            val converters = mutableListOf<ConverterDescriptor>()

            // Track property pairs to detect duplicates
            val propertyPairs = mutableMapOf<Pair<String, String>, KSFunctionDeclaration>()

            // Get source and target properties for validation
            val sourcePropertiesList = fromType.getDeclaredProperties().toList()
            val targetPropertiesList = toType.getDeclaredProperties().toList()
            val sourceProperties = sourcePropertiesList.map { it.simpleName.asString() }.toSet()
            val targetProperties = targetPropertiesList.map { it.simpleName.asString() }.toSet()

            // Create maps of property name to property declaration for type checking
            val sourcePropertyMap = sourcePropertiesList.associateBy { it.simpleName.asString() }
            val targetPropertyMap = targetPropertiesList.associateBy { it.simpleName.asString() }

            val converterFunctions = symbol.getDeclaredFunctions().filter { fn ->
                fn.annotations.any { it.isAnnotation(MAP_USING_FQ) }
            }

            for (fn in converterFunctions) {
                val mapUsingAnn = fn.annotations.first { it.isAnnotation(MAP_USING_FQ) }

                // Extract from and to property names
                val fromProp = mapUsingAnn.getStringArgOrNull(
                    name = ARG_FROM,
                    logger = logger,
                    symbol = fn,
                    annotationFqName = MAP_USING_FQ
                )

                val toProp = mapUsingAnn.getStringArgOrNull(
                    name = ARG_TO,
                    logger = logger,
                    symbol = fn,
                    annotationFqName = MAP_USING_FQ
                )

                // Validate non-empty from and to values
                if (fromProp.isNullOrBlank() || toProp.isNullOrBlank()) {
                    logger.error(
                        "@MapUsing must specify non-empty 'from' and 'to' values",
                        fn
                    )
                    continue
                }

                // Check for duplicate property pairs
                val propPair = Pair(fromProp, toProp)
                val existingFn = propertyPairs[propPair]
                if (existingFn != null) {
                    logger.error(
                        "Multiple @MapUsing converters defined for property mapping $fromProp → $toProp. " +
                        "First defined in ${existingFn.simpleName.asString()}, " +
                        "then again in ${fn.simpleName.asString()}.",
                        fn
                    )
                    continue
                }
                propertyPairs[propPair] = fn

                // Validate that properties exist in source and target classes
                if (fromProp !in sourceProperties) {
                    logger.error(
                        "Unknown source property '$fromProp' in @MapUsing of ${symbol.simpleName.asString()}. " +
                        "Available: ${sourceProperties.joinToString(", ")}",
                        fn
                    )
                    continue
                }

                if (toProp !in targetProperties) {
                    logger.error(
                        "Unknown target property '$toProp' in @MapUsing of ${symbol.simpleName.asString()}. " +
                        "Available: ${targetProperties.joinToString(", ")}",
                        fn
                    )
                    continue
                }

                // Get property declarations for type checking
                val sourceProp = sourcePropertyMap[fromProp]!!
                val targetProp = targetPropertyMap[toProp]!!

                // Get property types
                val sourceType = sourceProp.type.resolve()
                val targetType = targetProp.type.resolve()

                // Validate function signature - must have exactly one parameter or be an extension function
                val params = fn.parameters
                if (params.size != 1 && fn.extensionReceiver == null) {
                    logger.error(
                        "@MapUsing function must have exactly one parameter or be an extension function",
                        fn
                    )
                    continue
                }

                // Get parameter type and return type
                val paramType = if (fn.extensionReceiver != null) {
                    fn.extensionReceiver!!.resolve()
                } else {
                    params.first().type.resolve()
                }

                val returnType = fn.returnType?.resolve()
                if (returnType == null) {
                    logger.error(
                        "@MapUsing function must have a return type",
                        fn
                    )
                    continue
                }

                // Create TypeInfo objects
                val paramTypeDecl = paramType.declaration as? KSClassDeclaration
                if (paramTypeDecl == null) {
                    logger.error(
                        "@MapUsing function parameter type must be a class",
                        fn
                    )
                    continue
                }

                val returnTypeDecl = returnType.declaration as? KSClassDeclaration
                if (returnTypeDecl == null) {
                    logger.error(
                        "@MapUsing function return type must be a class",
                        fn
                    )
                    continue
                }

                // Validate type compatibility
                if (paramType.toString() != sourceType.toString()) {
                    logger.error(
                        "Type mismatch in @MapUsing converter function '${fn.simpleName.asString()}': " +
                        "Parameter type '${paramType}' doesn't match source property '${fromProp}' type '${sourceType}'",
                        fn
                    )
                    continue
                }

                if (returnType.toString() != targetType.toString()) {
                    logger.error(
                        "Type mismatch in @MapUsing converter function '${fn.simpleName.asString()}': " +
                        "Return type '${returnType}' doesn't match target property '${toProp}' type '${targetType}'",
                        fn
                    )
                    continue
                }

                val fromTypeInfo = paramTypeDecl.toTypeInfo(paramType)
                val toTypeInfo = returnTypeDecl.toTypeInfo(returnType)

                // Create ConverterDescriptor
                val converter = ConverterDescriptor(
                    enclosingObject = symbol,
                    function = fn,
                    fromType = fromTypeInfo,
                    toType = toTypeInfo
                )

                converters.add(converter)
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
                converters = converters
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
