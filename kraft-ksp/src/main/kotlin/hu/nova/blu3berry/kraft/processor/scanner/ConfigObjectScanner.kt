package hu.nova.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.config.MapUsing
import hu.nova.blu3berry.kraft.config.IgnoreSide
import hu.nova.blu3berry.kraft.config.MapIgnoreField
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.model.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.model.ConverterDescriptor
import hu.nova.blu3berry.kraft.model.FieldOverride
import hu.nova.blu3berry.kraft.model.IgnoredMappingConfig
import hu.nova.blu3berry.kraft.model.MapperId
import hu.nova.blu3berry.kraft.model.NestedMappingDescriptor
import hu.nova.blu3berry.kraft.model.TypeInfo
import hu.nova.blu3berry.kraft.model.toTypeInfo
import hu.nova.blu3berry.kraft.processor.util.KraftKspConstants
import hu.nova.blu3berry.kraft.processor.util.*

/**
 * Scans for configuration objects annotated with @MapConfig and extracts mapping information.
 */
class ConfigObjectScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {
    companion object {
        val MAP_CONFIG_FQ    = MapConfig::class.qualifiedName!!
        val MAP_USING_FQ     = MapUsing::class.qualifiedName!!
        val IGNORE_FIELD_FQ  = MapIgnoreField::class.qualifiedName!!
        val STRING_PAIR_FQ   = hu.nova.blu3berry.kraft.config.FieldMapping::class.qualifiedName!!
        val NESTED_FQ        = hu.nova.blu3berry.kraft.config.NestedMapping::class.qualifiedName!!
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

        // Extract ignored mappings
        val ignoredMappings = extractIgnoredMappings(annotation, classDeclaration)

        return ConfigObjectScanResult(
            sourceType = fromType,
            targetType = toType,
            configObject = classDeclaration,
            fieldOverrides = fieldOverrides,
            ignoredMappings = ignoredMappings,
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
                expectedTarget = KraftKspConstants.ARG_OBJECT,
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
            name = KraftKspConstants.ARG_SOURCE,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_CONFIG_FQ
        ) ?: return null

        val toKSType = annotation.getKClassArgOrNull(
            name = KraftKspConstants.ARG_TARGET,
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
            name = KraftKspConstants.ARG_FIELD_MAPPINGS,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_CONFIG_FQ
        ) ?: emptyList()

        return fieldPairAnnotations.mapNotNull { pair ->
            if (!pair.isAnnotation(STRING_PAIR_FQ)) return@mapNotNull null

            val from = pair.getStringArgOrNull(
                name = KraftKspConstants.ARG_SOURCE,
                logger = logger,
                symbol = symbol,
                annotationFqName = STRING_PAIR_FQ
            ) ?: return@mapNotNull null

            val to = pair.getStringArgOrNull(
                name = KraftKspConstants.ARG_TARGET,
                logger = logger,
                symbol = symbol,
                annotationFqName = STRING_PAIR_FQ
            ) ?: return@mapNotNull null

            FieldOverride(source = from, target = to)
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
            name = KraftKspConstants.ARG_NESTED_MAPPINGS,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_CONFIG_FQ
        ) ?: emptyList()

        return nestedAnnotations.mapNotNull { nestedAnn ->
            val nestedFrom = nestedAnn.getKClassArgOrNull(KraftKspConstants.ARG_SOURCE, logger, symbol, NESTED_FQ)
                ?: return@mapNotNull null
            val nestedTo = nestedAnn.getKClassArgOrNull(KraftKspConstants.ARG_TARGET, logger, symbol, NESTED_FQ)
                ?: return@mapNotNull null

            val fromDecl = nestedFrom.declaration as KSClassDeclaration
            val toDecl = nestedTo.declaration as KSClassDeclaration

            NestedMappingDescriptor(
                nestedMapperId = MapperId(
                    sourceQualifiedName = fromDecl.qualifiedName!!.asString(),
                    targetQualifiedName = toDecl.qualifiedName!!.asString(),
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
        val propertyPairs = mutableMapOf<String, KSFunctionDeclaration>()

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
                fn, symbol, fromType, sourceProperties, targetProperties,
                sourcePropertyMap, targetPropertyMap, propertyPairs
            ) ?: continue

            converters.add(converter)
        }

        return converters
    }

    /**
     * Validates a converter function and creates a ConverterDescriptor if valid.
     * When [fromProp] is blank, whole-source mode is used: the function receives the
     * entire source object instead of a single property value.
     */
    private fun validateAndCreateConverter(
        fn: KSFunctionDeclaration,
        symbol: KSClassDeclaration,
        sourceClass: KSClassDeclaration,
        sourceProperties: Set<String>,
        targetProperties: Set<String>,
        sourcePropertyMap: Map<String, KSPropertyDeclaration>,
        targetPropertyMap: Map<String, KSPropertyDeclaration>,
        propertyPairs: MutableMap<String, KSFunctionDeclaration>
    ): ConverterDescriptor? {
        val mapUsingAnn = fn.annotations.first { it.isAnnotation(MAP_USING_FQ) }

        val fromProp = mapUsingAnn.getStringArgOrNull(
            name = KraftKspConstants.ARG_SOURCE,
            logger = logger,
            symbol = fn,
            annotationFqName = MAP_USING_FQ
        )

        val toProp = mapUsingAnn.getStringArgOrNull(
            name = KraftKspConstants.ARG_TARGET,
            logger = logger,
            symbol = fn,
            annotationFqName = MAP_USING_FQ
        )

        if (toProp.isNullOrBlank()) {
            logger.error("@MapUsing must specify a non-empty 'target' value", fn)
            return null
        }

        val isWholeSource = fromProp.isNullOrBlank()

        // Check for duplicate target entries — keyed on toProp alone so that
        // mixed-mode duplicates (whole-source vs property-source) are also rejected.
        val existingFn = propertyPairs[toProp]
        if (existingFn != null) {
            val sourceDesc = if (isWholeSource) "<whole source>" else fromProp
            logger.error(
                "Multiple @MapUsing converters target property '$toProp': " +
                "already defined in '${existingFn.simpleName.asString()}' " +
                "(source: $sourceDesc), then again in '${fn.simpleName.asString()}'. " +
                "Only one converter per target property is allowed.",
                fn
            )
            return null
        }
        propertyPairs[toProp] = fn

        if (!validatePropertyExists(toProp, targetProperties, symbol, fn, "target")) return null

        if (!validateFunctionSignature(fn)) return null

        val paramType = getParameterType(fn)
        val returnType = getReturnType(fn) ?: return null
        val returnTypeDecl = getTypeDeclaration(returnType, fn, "return") ?: return null

        val targetProp = targetPropertyMap[toProp] ?: run {
            logger.error(
                "Internal error: property '$toProp' (target) passed validatePropertyExists " +
                "but was not found in targetPropertyMap. " +
                "Converter '${fn.simpleName.asString()}' in '${symbol.simpleName.asString()}' will be skipped.",
                fn
            )
            return null
        }
        val targetType = targetProp.type.resolve()

        if (isWholeSource) {
            // Whole-source: parameter/receiver must be the source class itself
            val sourceClassType = sourceClass.asStarProjectedType()
            if (paramType.declaration.qualifiedName?.asString() != sourceClass.qualifiedName?.asString()) {
                logger.error(
                    "@MapUsing whole-source converter '${fn.simpleName.asString()}': " +
                    "parameter type '$paramType' must match source class '${sourceClass.qualifiedName?.asString()}'",
                    fn
                )
                return null
            }
            if (returnType.declaration.qualifiedName?.asString() != targetType.declaration.qualifiedName?.asString()) {
                logger.error(
                    "Type mismatch in @MapUsing converter function '${fn.simpleName.asString()}': " +
                    "Return type '$returnType' doesn't match target property '$toProp' type '$targetType'",
                    fn
                )
                return null
            }
            val sourceTypeInfo = sourceClass.toTypeInfo(sourceClassType)
            val targetTypeInfo = returnTypeDecl.toTypeInfo(returnType)
            return ConverterDescriptor(
                enclosingObject = symbol,
                function = fn,
                sourcePropertyName = null,
                targetPropertyName = toProp,
                sourceType = sourceTypeInfo,
                targetType = targetTypeInfo
            )
        }

        // Property-source path
        if (!validatePropertyExists(fromProp, sourceProperties, symbol, fn, "source")) return null

        val sourceProp = sourcePropertyMap[fromProp] ?: run {
            logger.error(
                "Internal error: property '$fromProp' (source) passed validatePropertyExists " +
                "but was not found in sourcePropertyMap. " +
                "Converter '${fn.simpleName.asString()}' in '${symbol.simpleName.asString()}' will be skipped.",
                fn
            )
            return null
        }
        val sourceType = sourceProp.type.resolve()

        val paramTypeDecl = getTypeDeclaration(paramType, fn, "parameter") ?: return null

        if (!validateTypeCompatibility(paramType, sourceType, returnType, targetType, fromProp, toProp, fn)) return null

        return ConverterDescriptor(
            enclosingObject = symbol,
            function = fn,
            sourcePropertyName = fromProp,
            targetPropertyName = toProp,
            sourceType = paramTypeDecl.toTypeInfo(paramType),
            targetType = returnTypeDecl.toTypeInfo(returnType)
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
        if (fn.extensionReceiver != null) {
            // Extension converters must have no value parameters: getParameterType() uses the
            // receiver and codegen emits a zero-argument call, so any declared params are unreachable.
            if (params.isNotEmpty()) {
                logger.error(
                    "@MapUsing extension function '${fn.simpleName.asString()}' must not declare " +
                    "any value parameters — only the receiver is used. " +
                    "Found: ${params.map { it.name?.asString() }}",
                    fn
                )
                return false
            }
        } else if (params.size != 1) {
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
            fn.extensionReceiver?.resolve() ?: return fn.parameters.first().type.resolve()
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
        if (paramType.declaration.qualifiedName?.asString() != sourceType.declaration.qualifiedName?.asString()) {
            logger.error(
                "Type mismatch in @MapUsing converter function '${fn.simpleName.asString()}': " +
                "Parameter type '${paramType}' doesn't match source property '${fromProp}' type '${sourceType}'",
                fn
            )
            return false
        }

        if (returnType.declaration.qualifiedName?.asString() != targetType.declaration.qualifiedName?.asString()) {
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
     * Extracts [IgnoredMappingConfig] entries from the [ignoredMappings] array of [@MapConfig][MapConfig],
     * populated from [@MapIgnoreField][hu.nova.blu3berry.kraft.config.MapIgnoreField] entries.
     */
    private fun extractIgnoredMappings(
        annotation: KSAnnotation,
        symbol: KSClassDeclaration
    ): List<IgnoredMappingConfig> {
        val ignoreAnnotations = annotation.getArrayArgOrNull<KSAnnotation>(
            name = KraftKspConstants.ARG_IGNORED_MAPPINGS,
            logger = logger,
            symbol = symbol,
            annotationFqName = MAP_CONFIG_FQ
        ) ?: emptyList()

        return ignoreAnnotations.mapNotNull { ignoreAnn ->
            if (!ignoreAnn.isAnnotation(IGNORE_FIELD_FQ)) return@mapNotNull null

            val name = ignoreAnn.getStringArgOrNull(
                name = KraftKspConstants.ARG_NAME,
                logger = logger,
                symbol = symbol,
                annotationFqName = IGNORE_FIELD_FQ
            ) ?: return@mapNotNull null

            if (name.isBlank()) {
                logger.error("@MapIgnoreField name must not be blank.", symbol)
                return@mapNotNull null
            }


            val directionName = ignoreAnn.getEnumArgOrNull(
                name = KraftKspConstants.ARG_DIRECTION,
                logger = logger,
                symbol = symbol,
                annotationFqName = IGNORE_FIELD_FQ
            ) ?: IgnoreSide.BOTH.name

            val direction = try {
                IgnoreSide.valueOf(directionName)
            } catch (_: IllegalArgumentException) {
                logger.error(
                    "@MapIgnoreField unknown direction '$directionName' on property '$name'.",
                    symbol
                )
                return@mapNotNull null
            }

            IgnoredMappingConfig(name = name, direction = direction)
        }
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
