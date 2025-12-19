package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.MapUsing
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.model.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.model.ConverterDescriptor
import hu.nova.blu3berry.kraft.model.FieldOverride
import hu.nova.blu3berry.kraft.model.MapperId
import hu.nova.blu3berry.kraft.model.NestedMappingDescriptor
import hu.nova.blu3berry.kraft.model.toTypeInfo
import hu.nova.blu3berry.kraft.onclass.MapIgnore
import hu.nova.blu3berry.kraft.processor.util.*

/**
 * Scans for configuration objects annotated with @MapConfig and extracts mapping information.
 */
class ConfigObjectScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {
    companion object {
        val MAP_CONFIG_FQ = MapConfig::class.qualifiedName!!
        val MAP_USING_FQ = MapUsing::class.qualifiedName!!
        val MAP_IGNORE_FQ = MapIgnore::class.qualifiedName!!
        val STRING_PAIR_FQ = hu.nova.blu3berry.kraft.config.FieldOverride::class.qualifiedName!!
        val NESTED_FQ = hu.nova.blu3berry.kraft.config.NestedMapping::class.qualifiedName!!

        // Annotation parameter names
        const val ARG_FROM = "from"
        const val ARG_TO = "to"
        const val ARG_FIELD_MAPPING = "fieldMappings"
        const val ARG_NESTED_MAPPING = "nestedMappings"
        const val ARG_VALUE = "value"

        const val OBJECT = "object"
    }

    /**
     * Scans for configuration objects and returns the results.
     */
    fun scan(): List<ConfigObjectScanResult> {
        val results = mutableListOf<ConfigObjectScanResult>()

        resolver.getSymbolsWithAnnotation(MAP_CONFIG_FQ).forEach { symbol ->
            processConfigObject(symbol)?.let { results.add(it) }
        }

        return results
    }

    /**
     * Processes a single configuration object and returns a scan result.
     */
    private fun processConfigObject(symbol: KSAnnotated): ConfigObjectScanResult? {
        // Validate annotation target: must be an object
        if (!validateConfigObject(symbol)) {
            return null
        }

        val classDeclaration = symbol as KSClassDeclaration
        val annotation = classDeclaration.findAnnotation(MAP_CONFIG_FQ) ?: return null

        // Extract source and target types
        val (fromType, toType) = extractSourceAndTargetTypes(annotation, classDeclaration) ?: return null

        // Extract field mappings
        val fieldOverrides = extractFieldOverrides(annotation, classDeclaration)

        // Extract nested mappings
        val nestedMappings = extractNestedMappings(annotation, classDeclaration)

        // Extract and validate converter functions
        val converters = extractConverterFunctions(classDeclaration, fromType, toType)

        // Extract ignored fields
        val ignoredFields = extractIgnoredFields(classDeclaration)

        return ConfigObjectScanResult(
            fromType = fromType,
            toType = toType,
            configObject = classDeclaration,
            fieldOverrides = fieldOverrides,
            ignoredFields = ignoredFields,
            converters = converters,
            nestedMappings = nestedMappings
        )
    }

    /**
     * Validates that the symbol is a valid configuration object.
     */
    private fun validateConfigObject(symbol: KSAnnotated): Boolean {
        if (symbol !is KSClassDeclaration || symbol.classKind != ClassKind.OBJECT) {
            logger.annotationTargetError(
                annotationName = MapConfig::class.simpleName!!,
                expectedTarget = OBJECT,
                actualNode = symbol
            )
            return false
        }
        return true
    }

    /**
     * Extracts source and target types from the annotation.
     */
    private fun extractSourceAndTargetTypes(
        annotation: KSAnnotation,
        symbol: KSClassDeclaration
    ): Pair<KSClassDeclaration, KSClassDeclaration>? {
        val fromKSType = annotation.getKClassArgOrNull(
            name = ARG_FROM,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_CONFIG_FQ
        ) ?: return null

        val toKSType = annotation.getKClassArgOrNull(
            name = ARG_TO,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_CONFIG_FQ
        ) ?: return null

        val fromType = fromKSType.declaration as KSClassDeclaration
        val toType = toKSType.declaration as KSClassDeclaration

        return Pair(fromType, toType)
    }

    /**
     * Extracts field overrides from the annotation.
     */
    private fun extractFieldOverrides(
        annotation: KSAnnotation,
        symbol: KSClassDeclaration
    ): List<FieldOverride> {
        val fieldPairAnnotations = annotation.getArrayArgOrNull<KSAnnotation>(
            name = ARG_FIELD_MAPPING,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_CONFIG_FQ
        ) ?: emptyList()

        return fieldPairAnnotations.mapNotNull { pair ->
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

            FieldOverride(from = from, to = to)
        }
    }

    /**
     * Extracts nested mappings from the annotation.
     */
    private fun extractNestedMappings(
        annotation: KSAnnotation,
        symbol: KSClassDeclaration
    ): List<NestedMappingDescriptor> {
        val nestedAnnotations = annotation.getArrayArgOrNull<KSAnnotation>(
            name = ARG_NESTED_MAPPING,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_CONFIG_FQ
        ) ?: emptyList()

        return nestedAnnotations.mapNotNull { nestedAnn ->
            val nestedFrom = nestedAnn.getKClassArgOrNull(ARG_FROM, logger, symbol, NESTED_FQ)
                ?: return@mapNotNull null
            val nestedTo = nestedAnn.getKClassArgOrNull(ARG_TO, logger, symbol, NESTED_FQ)
                ?: return@mapNotNull null

            val fromDecl = nestedFrom.declaration as KSClassDeclaration
            val toDecl = nestedTo.declaration as KSClassDeclaration

            NestedMappingDescriptor(
                nestedMapperId = MapperId(
                    fromQualifiedName = fromDecl.qualifiedName!!.asString(),
                    toQualifiedName = toDecl.qualifiedName!!.asString(),
                ),
                sourceType = fromDecl.toTypeInfo(fromDecl.asStarProjectedType()),
                targetType = toDecl.toTypeInfo(toDecl.asStarProjectedType())
            )
        }
    }

    /**
     * Extracts and validates converter functions from the configuration object.
     */
    private fun extractConverterFunctions(
        symbol: KSClassDeclaration,
        fromType: KSClassDeclaration,
        toType: KSClassDeclaration
    ): List<ConverterDescriptor> {
        val converters = mutableListOf<ConverterDescriptor>()
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
            val converter = validateAndCreateConverter(
                fn, symbol, sourceProperties, targetProperties,
                sourcePropertyMap, targetPropertyMap, propertyPairs
            ) ?: continue

            converters.add(converter)
        }

        return converters
    }

    /**
     * Validates a converter function and creates a ConverterDescriptor if valid.
     */
    private fun validateAndCreateConverter(
        fn: KSFunctionDeclaration,
        symbol: KSClassDeclaration,
        sourceProperties: Set<String>,
        targetProperties: Set<String>,
        sourcePropertyMap: Map<String, KSPropertyDeclaration>,
        targetPropertyMap: Map<String, KSPropertyDeclaration>,
        propertyPairs: MutableMap<Pair<String, String>, KSFunctionDeclaration>
    ): ConverterDescriptor? {
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
            return null
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
            return null
        }
        propertyPairs[propPair] = fn

        // Validate that properties exist in source and target classes
        if (!validatePropertyExists(fromProp, sourceProperties, symbol, fn, "source")) {
            return null
        }

        if (!validatePropertyExists(toProp, targetProperties, symbol, fn, "target")) {
            return null
        }

        // Get property declarations for type checking
        val sourceProp = sourcePropertyMap[fromProp]!!
        val targetProp = targetPropertyMap[toProp]!!

        // Get property types
        val sourceType = sourceProp.type.resolve()
        val targetType = targetProp.type.resolve()

        // Validate function signature
        if (!validateFunctionSignature(fn)) {
            return null
        }

        // Get parameter type and return type
        val paramType = getParameterType(fn)
        val returnType = getReturnType(fn) ?: return null

        // Create TypeInfo objects
        val paramTypeDecl = getTypeDeclaration(paramType, fn, "parameter") ?: return null
        val returnTypeDecl = getTypeDeclaration(returnType, fn, "return") ?: return null

        // Validate type compatibility
        if (!validateTypeCompatibility(
                paramType, sourceType, returnType, targetType,
                fromProp, toProp, fn
            )
        ) {
            return null
        }

        val fromTypeInfo = paramTypeDecl.toTypeInfo(paramType)
        val toTypeInfo = returnTypeDecl.toTypeInfo(returnType)

        // Create ConverterDescriptor
        return ConverterDescriptor(
            enclosingObject = symbol,
            function = fn,
            mapUsingFrom = fromProp,
            mapUsingTo = toProp,
            fromType = fromTypeInfo,
            toType = toTypeInfo
        )
    }

    /**
     * Validates that a property exists in the given set of properties.
     */
    private fun validatePropertyExists(
        propertyName: String,
        properties: Set<String>,
        symbol: KSClassDeclaration,
        fn: KSFunctionDeclaration,
        propertyType: String
    ): Boolean {
        if (propertyName !in properties) {
            logger.error(
                "Unknown $propertyType property '$propertyName' in @MapUsing of ${symbol.simpleName.asString()}. " +
                "Available: ${properties.joinToString(", ")}",
                fn
            )
            return false
        }
        return true
    }

    /**
     * Validates that the function signature is valid for a converter function.
     */
    private fun validateFunctionSignature(fn: KSFunctionDeclaration): Boolean {
        val params = fn.parameters
        if (params.size != 1 && fn.extensionReceiver == null) {
            logger.error(
                "@MapUsing function must have exactly one parameter or be an extension function",
                fn
            )
            return false
        }
        return true
    }

    /**
     * Gets the parameter type of a function.
     */
    private fun getParameterType(fn: KSFunctionDeclaration): KSType {
        return if (fn.extensionReceiver != null) {
            fn.extensionReceiver!!.resolve()
        } else {
            fn.parameters.first().type.resolve()
        }
    }

    /**
     * Gets the return type of a function.
     */
    private fun getReturnType(fn: KSFunctionDeclaration): KSType? {
        val returnType = fn.returnType?.resolve()
        if (returnType == null) {
            logger.error(
                "@MapUsing function must have a return type",
                fn
            )
            return null
        }
        return returnType
    }

    /**
     * Gets the type declaration of a KSType.
     */
    private fun getTypeDeclaration(
        type: KSType,
        fn: KSFunctionDeclaration,
        typeName: String
    ): KSClassDeclaration? {
        val typeDecl = type.declaration as? KSClassDeclaration
        if (typeDecl == null) {
            logger.error(
                "@MapUsing function $typeName type must be a class",
                fn
            )
            return null
        }
        return typeDecl
    }

    /**
     * Validates that the types are compatible.
     */
    private fun validateTypeCompatibility(
        paramType: KSType,
        sourceType: KSType,
        returnType: KSType,
        targetType: KSType,
        fromProp: String,
        toProp: String,
        fn: KSFunctionDeclaration
    ): Boolean {
        if (paramType.toString() != sourceType.toString()) {
            logger.error(
                "Type mismatch in @MapUsing converter function '${fn.simpleName.asString()}': " +
                "Parameter type '${paramType}' doesn't match source property '${fromProp}' type '${sourceType}'",
                fn
            )
            return false
        }

        if (returnType.toString() != targetType.toString()) {
            logger.error(
                "Type mismatch in @MapUsing converter function '${fn.simpleName.asString()}': " +
                "Return type '${returnType}' doesn't match target property '${toProp}' type '${targetType}'",
                fn
            )
            return false
        }

        return true
    }

    /**
     * Extracts ignored fields from the configuration object.
     */
    private fun extractIgnoredFields(symbol: KSClassDeclaration): List<String> {
        return symbol.getDeclaredProperties().mapNotNull { prop ->
            val ignoreAnn = prop.annotations.firstOrNull { it.isAnnotation(MAP_IGNORE_FQ) }
            if (ignoreAnn != null) {
                ignoreAnn.getStringArgOrNull(
                    name = ARG_VALUE,
                    logger = logger,
                    symbol = prop,
                    annotationFqName = MAP_IGNORE_FQ
                ) ?: prop.simpleName.asString()
            } else null
        }.toList()
    }
}

/**
 * Checks if an annotation has the specified fully qualified name.
 */
private fun KSAnnotation.isAnnotation(fqName: String): Boolean {
    return annotationType
        .resolve()
        .declaration
        .qualifiedName
        ?.asString() == fqName
}
