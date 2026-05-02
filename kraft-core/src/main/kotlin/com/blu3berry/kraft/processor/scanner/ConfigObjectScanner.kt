package com.blu3berry.kraft.processor.scanner

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.validate
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.config.IgnoreSide
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.scan.ConfigObjectScanResult
import com.blu3berry.kraft.model.scan.FieldOverride
import com.blu3berry.kraft.model.scan.IgnoredMappingConfig
import com.blu3berry.kraft.model.toTypeInfo
import com.blu3berry.kraft.processor.util.KraftKspConstants
import com.blu3berry.kraft.processor.util.annotationTargetError
import com.blu3berry.kraft.processor.util.findAnnotation
import com.blu3berry.kraft.processor.util.getArrayArgOrNull
import com.blu3berry.kraft.processor.util.getEnumArgOrNull
import com.blu3berry.kraft.processor.util.getKClassArgOrNull
import com.blu3berry.kraft.processor.util.getStringArgOrNull

/**
 * Scans for configuration objects annotated with @MapConfig and extracts mapping information.
 */
@Suppress("TooManyFunctions", "LargeClass")
class ConfigObjectScanner(
    private val resolver: Resolver,
    private val logger: KSPLogger
) {
    /**
     * Scans for configuration objects and returns the results.
     */
    fun scan(): List<ConfigObjectScanResult> {
        val results = mutableListOf<ConfigObjectScanResult>()

        val configSymbols = resolver
            .getSymbolsWithAnnotation(KraftKspConstants.FQ_MAP_CONFIG)
            .filter { it.validate() }.toList()
        val reverseSymbols = resolver
            .getSymbolsWithAnnotation(KraftKspConstants.FQ_MAP_REVERSE)
            .filter { it.validate() }
            .filterIsInstance<KSClassDeclaration>()
            .toSet()

        configSymbols.forEach { symbol ->
            processConfigObject(symbol, hasReverse = symbol in reverseSymbols)?.let { results.add(it) }
        }

        // Validate @MapReverse on objects without @MapConfig
        val configObjects = results.map { it.configObject }.toSet()
        val orphanedReverseObjects = reverseSymbols
            .filter { it.classKind == ClassKind.OBJECT }
            .filter { it !in configObjects }
        orphanedReverseObjects.forEach { obj ->
            // Only report if it doesn't also have @MapFrom/@MapTo (ClassAnnotationScanner handles those)
            val hasClassAnnotation = obj.findAnnotation(KraftKspConstants.FQ_MAP_FROM) != null ||
                obj.findAnnotation(KraftKspConstants.FQ_MAP_TO) != null
            if (!hasClassAnnotation) {
                logger.error(
                    "@MapReverse on '${obj.simpleName.asString()}' requires " +
                    "@MapConfig on the same object.",
                    obj
                )
            }
        }

        return results
    }

    /**
     * Processes a single configuration object and returns a scan result.
     */
    private fun processConfigObject(symbol: KSAnnotated, hasReverse: Boolean): ConfigObjectScanResult? {
        // Validate annotation target: must be an object
        if (!validateConfigObject(symbol)) {
            return null
        }

        val classDeclaration = symbol as KSClassDeclaration
        val annotation = classDeclaration.findAnnotation(KraftKspConstants.FQ_MAP_CONFIG) ?: return null

        // Extract source and target types
        val (fromType, toType) = extractSourceAndTargetTypes(annotation, classDeclaration) ?: return null

        // Extract field mappings
        val fieldOverrides = extractFieldOverrides(annotation, classDeclaration)

        // Extract nested mappings
        val nestedMappings = extractNestedMappings(annotation, classDeclaration)

        // Extract and validate converter functions
        val converters = extractConverterFunctions(classDeclaration, fromType, toType, hasReverse)

        // Extract ignored mappings
        val ignoredMappings = extractIgnoredMappings(annotation, classDeclaration)

        val useGlobalConverters = annotation.arguments
            .firstOrNull { it.name?.asString() == KraftKspConstants.ARG_USE_GLOBAL_CONVERTERS }
            ?.value as? Boolean ?: true

        return ConfigObjectScanResult(
            sourceType = fromType,
            targetType = toType,
            configObject = classDeclaration,
            fieldOverrides = fieldOverrides,
            ignoredMappings = ignoredMappings,
            converters = converters,
            nestedMappings = nestedMappings,
            hasReverse = hasReverse,
            useGlobalConverters = useGlobalConverters
        )
    }

    /**
     * Validates that the symbol is a valid configuration object.
     */
    private fun validateConfigObject(symbol: KSAnnotated): Boolean {
        if (symbol !is KSClassDeclaration || symbol.classKind != ClassKind.OBJECT) {
            logger.annotationTargetError(
                annotationName = KraftKspConstants.FQ_MAP_CONFIG,
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
            annotationFqName = KraftKspConstants.FQ_MAP_CONFIG
        ) ?: return null

        val toKSType = annotation.getKClassArgOrNull(
            name = KraftKspConstants.ARG_TARGET,
            logger = logger,
            symbol = symbol,
            annotationFqName = KraftKspConstants.FQ_MAP_CONFIG
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
            annotationFqName = KraftKspConstants.FQ_MAP_CONFIG
        ) ?: emptyList()

        return fieldPairAnnotations.mapNotNull { pair ->
            if (!pair.isAnnotation(KraftKspConstants.FQ_FIELD_MAPPING)) return@mapNotNull null

            val from = pair.getStringArgOrNull(
                name = KraftKspConstants.ARG_SOURCE,
                logger = logger,
                symbol = symbol,
                annotationFqName = KraftKspConstants.FQ_FIELD_MAPPING
            ) ?: return@mapNotNull null

            val to = pair.getStringArgOrNull(
                name = KraftKspConstants.ARG_TARGET,
                logger = logger,
                symbol = symbol,
                annotationFqName = KraftKspConstants.FQ_FIELD_MAPPING
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
            annotationFqName = KraftKspConstants.FQ_MAP_CONFIG
        ) ?: emptyList()

        return nestedAnnotations.mapNotNull { nestedAnn ->
            val nestedFrom = nestedAnn.getKClassArgOrNull(
                KraftKspConstants.ARG_SOURCE, logger, symbol,
                KraftKspConstants.FQ_NESTED_MAPPING
            ) ?: return@mapNotNull null
            val nestedTo = nestedAnn.getKClassArgOrNull(
                KraftKspConstants.ARG_TARGET, logger, symbol,
                KraftKspConstants.FQ_NESTED_MAPPING
            )
                ?: return@mapNotNull null

            val fromDecl = nestedFrom.declaration as KSClassDeclaration
            val toDecl = nestedTo.declaration as KSClassDeclaration

            NestedMappingDescriptor(
                nestedMapperId = MapperId(
                    sourceQualifiedName = fromDecl.qualifiedName?.asString() ?: fromDecl.simpleName.asString(),
                    targetQualifiedName = toDecl.qualifiedName?.asString() ?: toDecl.simpleName.asString(),
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
        toType: KSClassDeclaration,
        hasReverse: Boolean = false
    ): List<ConverterDescriptor> {
        val sourcePropertiesList = fromType.getDeclaredProperties().toList()
        val targetPropertiesList = toType.getDeclaredProperties().toList()
        val fwdSourceProperties = sourcePropertiesList.map { it.simpleName.asString() }.toSet()
        val fwdTargetProperties = targetPropertiesList.map { it.simpleName.asString() }.toSet()
        val fwdSourcePropertyMap = sourcePropertiesList.associateBy { it.simpleName.asString() }
        val fwdTargetPropertyMap = targetPropertiesList.associateBy { it.simpleName.asString() }

        val converterFunctions = symbol.getDeclaredFunctions().filter { fn ->
            fn.annotations.any { it.isAnnotation(KraftKspConstants.FQ_MAP_USING) }
        }

        if (!hasReverse) {
            val converters = mutableListOf<ConverterDescriptor>()
            val propertyPairs = mutableMapOf<String, KSFunctionDeclaration>()
            for (fn in converterFunctions) {
                val converter = buildForwardOnlyConverter(
                    fn, symbol, fromType,
                    fwdSourceProperties, fwdTargetProperties,
                    fwdSourcePropertyMap, fwdTargetPropertyMap,
                    propertyPairs
                )
                if (converter != null) converters.add(converter)
            }
            return converters
        }

        return extractConverterFunctionsWithReverse(
            converterFunctions, symbol, fromType, toType,
            fwdSourceProperties, fwdTargetProperties,
            fwdSourcePropertyMap, fwdTargetPropertyMap,
            targetPropertiesList, sourcePropertiesList
        )
    }

    @Suppress("LongParameterList")
    private fun buildForwardOnlyConverter(
        fn: KSFunctionDeclaration,
        symbol: KSClassDeclaration,
        fromType: KSClassDeclaration,
        fwdSourceProperties: Set<String>,
        fwdTargetProperties: Set<String>,
        fwdSourcePropertyMap: Map<String, KSPropertyDeclaration>,
        fwdTargetPropertyMap: Map<String, KSPropertyDeclaration>,
        propertyPairs: MutableMap<String, KSFunctionDeclaration>
    ): ConverterDescriptor? {
        val parsed = parseConverterAnnotation(fn) ?: return null
        if (parsed.direction == ConverterDirection.REVERSE) {
            logger.error(
                "@MapUsing(direction = REVERSE) on '${fn.simpleName.asString()}' " +
                    "in '${symbol.simpleName.asString()}' has no effect: the enclosing " +
                    "@MapConfig is not annotated with @MapReverse, so only forward " +
                    "converters are generated. Remove the direction or add @MapReverse.",
                fn
            )
            return null
        }
        if (!checkDuplicateTarget(parsed, fn, propertyPairs)) return null
        return validateAndCreateConverter(
            fn, symbol, fromType, fwdSourceProperties, fwdTargetProperties,
            fwdSourcePropertyMap, fwdTargetPropertyMap, parsed
        )
    }

    /**
     * Handles converter extraction when @MapReverse is present.
     * Classifies each converter as forward or reverse, validates against the
     * appropriate property maps, and tracks duplicates per direction.
     */
    @Suppress("LongParameterList")
    private fun extractConverterFunctionsWithReverse(
        converterFunctions: Sequence<KSFunctionDeclaration>,
        symbol: KSClassDeclaration,
        fromType: KSClassDeclaration,
        toType: KSClassDeclaration,
        fwdSourceProperties: Set<String>,
        fwdTargetProperties: Set<String>,
        fwdSourcePropertyMap: Map<String, KSPropertyDeclaration>,
        fwdTargetPropertyMap: Map<String, KSPropertyDeclaration>,
        targetPropertiesList: List<KSPropertyDeclaration>,
        sourcePropertiesList: List<KSPropertyDeclaration>
    ): List<ConverterDescriptor> {
        val converters = mutableListOf<ConverterDescriptor>()
        val forwardPropertyPairs = mutableMapOf<String, KSFunctionDeclaration>()
        val reversePropertyPairs = mutableMapOf<String, KSFunctionDeclaration>()
        val revSourcePropertyMap = targetPropertiesList.associateBy { it.simpleName.asString() }
        val revTargetPropertyMap = sourcePropertiesList.associateBy { it.simpleName.asString() }

        for (fn in converterFunctions) {
            val parsed = parseConverterAnnotation(fn) ?: continue

            val direction = resolveConverterDirection(
                parsed, fn, fromType, fwdSourcePropertyMap, revSourcePropertyMap
            )

            val pairMap = if (direction == ConverterDirection.FORWARD) forwardPropertyPairs else reversePropertyPairs
            if (!checkDuplicateTarget(parsed, fn, pairMap)) continue

            val converter = when (direction) {
                ConverterDirection.FORWARD, ConverterDirection.AUTO -> validateAndCreateConverter(
                    fn, symbol, fromType, fwdSourceProperties, fwdTargetProperties,
                    fwdSourcePropertyMap, fwdTargetPropertyMap, parsed
                )
                ConverterDirection.REVERSE -> validateAndCreateConverter(
                    fn, symbol, toType, fwdTargetProperties, fwdSourceProperties,
                    revSourcePropertyMap, revTargetPropertyMap, parsed
                )
            } ?: continue

            converters.add(converter.copy(resolvedDirection = direction))
        }

        return converters
    }

    /**
     * Determines the effective direction for a @MapUsing converter.
     *
     * If the annotation specifies an explicit [ConverterDirection.FORWARD] or [ConverterDirection.REVERSE],
     * that value is returned. For [ConverterDirection.AUTO], the converter's parameter type is matched
     * against the source property types of each direction to disambiguate.
     */
    private fun resolveConverterDirection(
        parsed: ConverterAnnotationArgs,
        fn: KSFunctionDeclaration,
        forwardSourceClass: KSClassDeclaration,
        forwardSourcePropertyMap: Map<String, KSPropertyDeclaration>,
        reverseSourcePropertyMap: Map<String, KSPropertyDeclaration>
    ): ConverterDirection {
        if (parsed.direction != ConverterDirection.AUTO) return parsed.direction

        val paramType = if (fn.extensionReceiver != null) {
            fn.extensionReceiver!!.resolve()
        } else {
            fn.parameters.firstOrNull()?.type?.resolve()
        } ?: return ConverterDirection.FORWARD

        if (parsed.isWholeSource) {
            val matchesForward = paramType.declaration.qualifiedName?.asString() ==
                forwardSourceClass.qualifiedName?.asString()
            return if (matchesForward) ConverterDirection.FORWARD else ConverterDirection.REVERSE
        }

        return classifyPropertySourceDirection(
            parsed.fromProp!!, paramType, forwardSourcePropertyMap, reverseSourcePropertyMap
        )
    }

    /**
     * Classifies a property-source converter's direction by matching the parameter type
     * against each direction's source property type.
     */
    private fun classifyPropertySourceDirection(
        sourcePropName: String,
        paramType: KSType,
        forwardSourcePropertyMap: Map<String, KSPropertyDeclaration>,
        reverseSourcePropertyMap: Map<String, KSPropertyDeclaration>
    ): ConverterDirection {
        val forwardProp = forwardSourcePropertyMap[sourcePropName]
        val reverseProp = reverseSourcePropertyMap[sourcePropName]

        // Property exists in only one direction, or neither (validation will error)
        if (forwardProp == null || reverseProp == null) {
            return if (reverseProp != null) ConverterDirection.REVERSE else ConverterDirection.FORWARD
        }

        // Both directions have the property — disambiguate by type matching
        return when {
            typesMatch(paramType, forwardProp.type.resolve()) -> ConverterDirection.FORWARD
            typesMatch(paramType, reverseProp.type.resolve()) -> ConverterDirection.REVERSE
            else -> ConverterDirection.FORWARD
        }
    }

    /**
     * Checks that no other converter already targets the same property in the same direction.
     * Returns true if the target is unique, false (with error logged) if a duplicate is found.
     */
    private fun checkDuplicateTarget(
        parsed: ConverterAnnotationArgs,
        fn: KSFunctionDeclaration,
        propertyPairs: MutableMap<String, KSFunctionDeclaration>
    ): Boolean {
        val existingFn = propertyPairs[parsed.toProp]
        if (existingFn != null) {
            val sourceDesc = if (parsed.isWholeSource) "<whole source>" else parsed.fromProp
            logger.error(
                "Multiple @MapUsing converters target property " +
                    "'${parsed.toProp}': already defined in " +
                    "'${existingFn.simpleName.asString()}' " +
                    "(source: $sourceDesc), then again in " +
                    "'${fn.simpleName.asString()}'. " +
                    "Only one converter per target property is allowed.",
                fn
            )
            return false
        }
        propertyPairs[parsed.toProp] = fn
        return true
    }

    @Suppress("LongParameterList", "ReturnCount")
    private fun validateAndCreateConverter(
        fn: KSFunctionDeclaration,
        symbol: KSClassDeclaration,
        sourceClass: KSClassDeclaration,
        sourceProperties: Set<String>,
        targetProperties: Set<String>,
        sourcePropertyMap: Map<String, KSPropertyDeclaration>,
        targetPropertyMap: Map<String, KSPropertyDeclaration>,
        parsed: ConverterAnnotationArgs
    ): ConverterDescriptor? {
        if (!validatePropertyExists(
                parsed.toProp, targetProperties, symbol, fn, "target"
            )
        ) return null
        if (!validateFunctionSignature(fn)) return null

        val paramType = getParameterType(fn)
        val returnType = getReturnType(fn) ?: return null
        val returnTypeDecl = getTypeDeclaration(
            returnType, fn, "return"
        ) ?: return null

        val targetProp = targetPropertyMap[parsed.toProp] ?: run {
            logger.error(
                "Internal error: property '${parsed.toProp}' (target) " +
                    "passed validatePropertyExists but was not found " +
                    "in targetPropertyMap. Converter " +
                    "'${fn.simpleName.asString()}' in " +
                    "'${symbol.simpleName.asString()}' will be skipped.",
                fn
            )
            return null
        }
        val targetType = targetProp.type.resolve()

        return if (parsed.isWholeSource) {
            createWholeSourceConverter(
                fn, symbol, sourceClass, parsed.toProp,
                paramType, returnType, returnTypeDecl, targetType
            )
        } else {
            createPropertySourceConverter(
                fn, symbol, parsed.fromProp!!, parsed.toProp,
                sourceProperties, sourcePropertyMap,
                paramType, returnType, returnTypeDecl, targetType
            )
        }
    }

    private data class ConverterAnnotationArgs(
        val fromProp: String?,
        val toProp: String,
        val isWholeSource: Boolean,
        val direction: ConverterDirection
    )

    private fun parseConverterAnnotation(
        fn: KSFunctionDeclaration
    ): ConverterAnnotationArgs? {
        val mapUsingAnn = fn.annotations.firstOrNull {
            it.isAnnotation(KraftKspConstants.FQ_MAP_USING)
        } ?: return null

        val fromProp = mapUsingAnn.getStringArgOrNull(
            name = KraftKspConstants.ARG_SOURCE,
            logger = logger,
            symbol = fn,
            annotationFqName = KraftKspConstants.FQ_MAP_USING
        )

        val toProp = mapUsingAnn.getStringArgOrNull(
            name = KraftKspConstants.ARG_TARGET,
            logger = logger,
            symbol = fn,
            annotationFqName = KraftKspConstants.FQ_MAP_USING
        )

        if (toProp.isNullOrBlank()) {
            logger.error(
                "@MapUsing must specify a non-empty 'target' value", fn
            )
            return null
        }

        val isWholeSource = fromProp.isNullOrBlank()

        val directionName = mapUsingAnn.getEnumArgOrNull(
            name = KraftKspConstants.ARG_DIRECTION,
            logger = logger,
            symbol = fn,
            annotationFqName = KraftKspConstants.FQ_MAP_USING
        ) ?: ConverterDirection.AUTO.name

        val direction = try {
            ConverterDirection.valueOf(directionName)
        } catch (_: IllegalArgumentException) {
            logger.error(
                "@MapUsing unknown direction '$directionName'", fn
            )
            return null
        }

        return ConverterAnnotationArgs(fromProp, toProp, isWholeSource, direction)
    }

    @Suppress("LongParameterList")
    private fun createWholeSourceConverter(
        fn: KSFunctionDeclaration,
        symbol: KSClassDeclaration,
        sourceClass: KSClassDeclaration,
        toProp: String,
        paramType: KSType,
        returnType: KSType,
        returnTypeDecl: KSClassDeclaration,
        targetType: KSType
    ): ConverterDescriptor? {
        if (paramType.declaration.qualifiedName?.asString() !=
            sourceClass.qualifiedName?.asString()
        ) {
            logger.error(
                "@MapUsing whole-source converter " +
                    "'${fn.simpleName.asString()}': parameter type " +
                    "'$paramType' must match source class " +
                    "'${sourceClass.qualifiedName?.asString()}'",
                fn
            )
            return null
        }
        if (!typesMatch(returnType, targetType)) {
            logger.error(
                "Type mismatch in @MapUsing converter function " +
                    "'${fn.simpleName.asString()}': Return type " +
                    "'$returnType' doesn't match target property " +
                    "'$toProp' type '$targetType'",
                fn
            )
            return null
        }
        val sourceClassType = sourceClass.asStarProjectedType()
        return ConverterDescriptor(
            enclosingObject = symbol,
            function = fn,
            sourcePropertyName = null,
            targetPropertyName = toProp,
            sourceType = sourceClass.toTypeInfo(sourceClassType),
            targetType = returnTypeDecl.toTypeInfo(returnType)
        )
    }

    @Suppress("LongParameterList", "ReturnCount")
    private fun createPropertySourceConverter(
        fn: KSFunctionDeclaration,
        symbol: KSClassDeclaration,
        fromProp: String,
        toProp: String,
        sourceProperties: Set<String>,
        sourcePropertyMap: Map<String, KSPropertyDeclaration>,
        paramType: KSType,
        returnType: KSType,
        returnTypeDecl: KSClassDeclaration,
        targetType: KSType
    ): ConverterDescriptor? {
        if (!validatePropertyExists(
                fromProp, sourceProperties, symbol, fn, "source"
            )
        ) return null

        val sourceProp = sourcePropertyMap[fromProp] ?: run {
            logger.error(
                "Internal error: property '$fromProp' (source) " +
                    "passed validatePropertyExists but was not found " +
                    "in sourcePropertyMap. Converter " +
                    "'${fn.simpleName.asString()}' in " +
                    "'${symbol.simpleName.asString()}' will be skipped.",
                fn
            )
            return null
        }
        val sourceType = sourceProp.type.resolve()

        val paramTypeDecl = getTypeDeclaration(
            paramType, fn, "parameter"
        ) ?: return null

        if (!validateTypeCompatibility(
                paramType, sourceType, returnType,
                targetType, fromProp, toProp, fn
            )
        ) return null

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
     * For extension functions, returns the receiver type.
     * For regular functions, returns the first parameter type.
     * Must be called after [validateFunctionSignature] has passed.
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
        if (!typesMatch(paramType, sourceType)) {
            logger.error(
                "Type mismatch in @MapUsing converter function '${fn.simpleName.asString()}': " +
                "Parameter type '${paramType}' doesn't match source property '${fromProp}' type '${sourceType}'",
                fn
            )
            return false
        }

        if (!typesMatch(returnType, targetType)) {
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
     * Checks whether two [KSType]s are structurally identical: same qualified name,
     * same nullability, and matching type arguments (recursively for generic types like [List]).
     */
    private fun typesMatch(a: KSType, b: KSType): Boolean {
        if (a.declaration.qualifiedName?.asString() != b.declaration.qualifiedName?.asString()) return false
        if (a.nullability != b.nullability) return false
        if (a.arguments.size != b.arguments.size) return false
        return a.arguments.zip(b.arguments).all { (aArg, bArg) ->
            val aType = aArg.type?.resolve()
            val bType = bArg.type?.resolve()
            when {
                aType == null && bType == null -> true  // both star projections
                aType == null || bType == null -> false  // one star, one not
                else -> typesMatch(aType, bType)
            }
        }
    }

    /**
     * Extracts [IgnoredMappingConfig] entries from the [ignoredMappings] array of [@MapConfig][MapConfig],
     * populated from [@MapIgnoreField][com.blu3berry.kraft.config.MapIgnoreField] entries.
     */
    private fun extractIgnoredMappings(
        annotation: KSAnnotation,
        symbol: KSClassDeclaration
    ): List<IgnoredMappingConfig> {
        val ignoreAnnotations = annotation.getArrayArgOrNull<KSAnnotation>(
            name = KraftKspConstants.ARG_IGNORED_MAPPINGS,
            logger = logger,
            symbol = symbol,
            annotationFqName = KraftKspConstants.FQ_MAP_CONFIG
        ) ?: emptyList()

        return ignoreAnnotations.mapNotNull { ignoreAnn ->
            if (!ignoreAnn.isAnnotation(KraftKspConstants.FQ_MAP_IGNORE_FIELD)) return@mapNotNull null

            val name = ignoreAnn.getStringArgOrNull(
                name = KraftKspConstants.ARG_NAME,
                logger = logger,
                symbol = symbol,
                annotationFqName = KraftKspConstants.FQ_MAP_IGNORE_FIELD
            ) ?: return@mapNotNull null

            if (name.isBlank()) {
                logger.error("@MapIgnoreField name must not be blank.", symbol)
                return@mapNotNull null
            }


            val directionName = ignoreAnn.getEnumArgOrNull(
                name = KraftKspConstants.ARG_DIRECTION,
                logger = logger,
                symbol = symbol,
                annotationFqName = KraftKspConstants.FQ_MAP_IGNORE_FIELD
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
