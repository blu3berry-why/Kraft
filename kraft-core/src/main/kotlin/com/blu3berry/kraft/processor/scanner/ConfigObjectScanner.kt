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
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.config.IgnoreSide
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.descriptor.ConstantDescriptor
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.scan.ConfigObjectScanResult
import com.blu3berry.kraft.model.scan.FieldOverride
import com.blu3berry.kraft.model.scan.IgnoredMappingConfig
import com.blu3berry.kraft.model.toTypeInfo
import com.blu3berry.kraft.processor.util.KraftKspConstants
import com.blu3berry.kraft.processor.util.annotationTargetError
import com.blu3berry.kraft.processor.util.fieldMappingInBothForms
import com.blu3berry.kraft.processor.util.findAnnotation
import com.blu3berry.kraft.processor.util.flatFieldMappingWithoutMapConfig
import com.blu3berry.kraft.processor.util.flatMapIgnoreFieldWithoutMapConfig
import com.blu3berry.kraft.processor.util.mapIgnoreFieldInBothForms
import com.blu3berry.kraft.processor.util.getArrayArgOrNull
import com.blu3berry.kraft.processor.util.getEnumArgOrDefault
import com.blu3berry.kraft.processor.util.getKClassArgOrNull
import com.blu3berry.kraft.processor.util.getStringArgOrDefault
import com.blu3berry.kraft.processor.util.getStringArgOrNull
import com.blu3berry.kraft.processor.util.mapConstantConflict
import com.blu3berry.kraft.processor.util.mapConstantOutsideMapConfig
import com.blu3berry.kraft.processor.util.unwrapTypeAliases
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.report

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
            // Only report if it has no other valid mapping annotation:
            //   @MapFrom/@MapTo are handled by ClassAnnotationScanner.
            //   @MapEnum is handled by EnumMapScanner, which now also
            //   honours @MapReverse to emit the inverse enum mapper.
            val hasOtherMappingAnnotation =
                obj.findAnnotation(KraftKspConstants.FQ_MAP_FROM) != null ||
                obj.findAnnotation(KraftKspConstants.FQ_MAP_TO) != null ||
                obj.findAnnotation(KraftKspConstants.FQ_MAP_ENUM) != null
            if (!hasOtherMappingAnnotation) {
                logger.report(
                    KraftDiagnostic.MAP_REVERSE_WITHOUT_MAPPING,
                    "@MapReverse on '${obj.simpleName.asString()}' requires " +
                    "@MapConfig, @MapEnum, @MapFrom, or @MapTo on the same object.",
                    obj
                )
            }
        }

        reportFlatRulesWithoutMapConfig()
        reportConstantsOutsideMapConfig()

        return results
    }

    /**
     * Reports KRAFT-E069 for each `@MapConstant` member whose enclosing declaration is not a
     * `@MapConfig` object. Members of `@MapConfig` objects are validated by [ConstantMemberScanner].
     */
    private fun reportConstantsOutsideMapConfig() {
        resolver.getSymbolsWithAnnotation(KraftKspConstants.FQ_MAP_CONSTANT)
            .filterIsInstance<KSDeclaration>()
            .filter { member ->
                val parent = member.parentDeclaration as? KSClassDeclaration
                parent?.findAnnotation(KraftKspConstants.FQ_MAP_CONFIG) == null
            }
            .forEach { member ->
                val name = listOfNotNull(member.parentDeclaration?.simpleName?.asString(), member.simpleName.asString())
                    .joinToString(".")
                logger.mapConstantOutsideMapConfig(name, member)
            }
    }

    /**
     * Reports a flat [FieldMapping][com.blu3berry.kraft.config.FieldMapping] or
     * [MapIgnoreField][com.blu3berry.kraft.config.MapIgnoreField] (KRAFT-E071) written on a declaration
     * that does not carry `@MapConfig`. Annotations used as `@MapConfig` array arguments are not
     * annotations on a symbol, so `getSymbolsWithAnnotation` never returns them.
     */
    private fun reportFlatRulesWithoutMapConfig() {
        resolver.getSymbolsWithAnnotation(KraftKspConstants.FQ_FIELD_MAPPING)
            .filter { it.findAnnotation(KraftKspConstants.FQ_MAP_CONFIG) == null }
            .forEach { logger.flatFieldMappingWithoutMapConfig(it.displayName(), it) }
        resolver.getSymbolsWithAnnotation(KraftKspConstants.FQ_MAP_IGNORE_FIELD)
            .filter { it.findAnnotation(KraftKspConstants.FQ_MAP_CONFIG) == null }
            .forEach { logger.flatMapIgnoreFieldWithoutMapConfig(it.displayName(), it) }
    }

    private fun KSAnnotated.displayName(): String =
        (this as? KSDeclaration)?.simpleName?.asString() ?: toString()

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
        val taggedFieldOverrides = extractFieldOverrides(annotation, classDeclaration)

        // Extract nested mappings
        val nestedMappings = extractNestedMappings(annotation, classDeclaration)

        // Extract and validate converter functions
        val converters = extractConverterFunctions(classDeclaration, fromType, toType, hasReverse)

        // Extract ignored mappings
        val taggedIgnoredMappings = extractIgnoredMappings(annotation, classDeclaration)

        reportRulesInBothForms(classDeclaration, taggedFieldOverrides, taggedIgnoredMappings)
        val fieldOverrides = taggedFieldOverrides.map { it.value }
        val ignoredMappings = taggedIgnoredMappings.map { it.value }

        val constants = ConstantMemberScanner(logger).scan(classDeclaration, fromType, toType, hasReverse)
        reportConstantConflicts(
            classDeclaration, constants,
            OtherRules(fieldOverrides, ignoredMappings, converters),
            forwardTargetName = toType.simpleName.asString(),
            reverseTargetName = fromType.simpleName.asString()
        )

        val useGlobalConverters = annotation.arguments
            .firstOrNull { it.name?.asString() == KraftKspConstants.ARG_USE_GLOBAL_CONVERTERS }
            ?.value as? Boolean ?: KraftKspConstants.DEFAULT_MAP_CONFIG_USE_GLOBAL_CONVERTERS

        val aliasEmitModeName = annotation.getEnumArgOrDefault(
            name = KraftKspConstants.ARG_ALIAS_EMIT_MODE,
            default = KraftKspConstants.DEFAULT_MAP_CONFIG_ALIAS_EMIT_MODE,
            logger = logger,
            symbol = classDeclaration,
            annotationFqName = KraftKspConstants.FQ_MAP_CONFIG
        )
        val aliasEmitMode = try {
            AliasEmitMode.valueOf(aliasEmitModeName)
        } catch (_: IllegalArgumentException) {
            AliasEmitMode.INHERIT
        }

        return ConfigObjectScanResult(
            sourceType = fromType,
            targetType = toType,
            configObject = classDeclaration,
            fieldOverrides = fieldOverrides,
            ignoredMappings = ignoredMappings,
            converters = converters,
            nestedMappings = nestedMappings,
            hasReverse = hasReverse,
            useGlobalConverters = useGlobalConverters,
            aliasEmitMode = aliasEmitMode,
            constants = constants,
        )
    }

    /** The non-constant rules of one config object that can claim a target property. */
    private data class OtherRules(
        val fieldOverrides: List<FieldOverride>,
        val ignoredMappings: List<IgnoredMappingConfig>,
        val converters: List<ConverterDescriptor>
    )

    /**
     * Reports KRAFT-E070 once for each pair of rules that claim one target in one direction,
     * where at least one of them is a `@MapConstant`. Forward claims: `FieldMapping.target`,
     * `@MapIgnoreField` TARGET/BOTH, forward (or AUTO) converters and forward constants.
     * Reverse claims: `FieldMapping.source` (renames are inverted in reverse), `@MapIgnoreField`
     * SOURCE/BOTH, reverse converters and reverse constants.
     */
    private fun reportConstantConflicts(
        symbol: KSClassDeclaration,
        constants: List<ConstantDescriptor>,
        others: OtherRules,
        forwardTargetName: String,
        reverseTargetName: String
    ) {
        if (constants.isEmpty()) return
        for (reverse in listOf(false, true)) {
            val inDirection = constants.filter { (it.resolvedDirection == ConverterDirection.REVERSE) == reverse }
            val claims = otherClaims(others, reverse)
            val targetClass = if (reverse) reverseTargetName else forwardTargetName
            inDirection.forEachIndexed { index, constant ->
                val target = constant.targetPropertyName
                val rivals = claims.filter { it.first == target }.map { it.second } +
                    inDirection.drop(index + 1)
                        .filter { it.targetPropertyName == target }
                        .map { "@MapConstant '${it.memberName}'" }
                rivals.forEach { rival ->
                    logger.mapConstantConflict(
                        target = target,
                        targetClass = targetClass,
                        objectName = symbol.simpleName.asString(),
                        constantMember = constant.memberName,
                        otherRule = rival,
                        symbol = constant.declaration
                    )
                }
            }
        }
    }

    /** `(target name, rendered rule)` for each non-constant rule active in one direction. */
    private fun otherClaims(others: OtherRules, reverse: Boolean): List<Pair<String, String>> {
        val renames = others.fieldOverrides.map {
            (if (reverse) it.source else it.target) to
                "@FieldMapping(source = \"${it.source}\", target = \"${it.target}\")"
        }
        val activeSides = setOf(if (reverse) IgnoreSide.SOURCE else IgnoreSide.TARGET, IgnoreSide.BOTH)
        val ignores = others.ignoredMappings
            .filter { it.direction in activeSides }
            .map { it.name to "@MapIgnoreField(\"${it.name}\")" }
        val converters = others.converters
            .filter { (it.resolvedDirection == ConverterDirection.REVERSE) == reverse }
            .map { it.targetPropertyName to "@MapUsing '${it.functionName}'" }
        return renames + ignores + converters
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
     * Extracts field overrides from both forms: the [fieldMappings][KraftKspConstants.ARG_FIELD_MAPPINGS]
     * array of `@MapConfig` and flat `@FieldMapping` annotations on the object. Array entries come
     * first, then flat ones, each tagged with its [Origin].
     */
    private fun extractFieldOverrides(
        annotation: KSAnnotation,
        symbol: KSClassDeclaration
    ): List<Tagged<FieldOverride>> {
        val arrayAnnotations = annotation.getArrayArgOrNull<KSAnnotation>(
            name = KraftKspConstants.ARG_FIELD_MAPPINGS,
            logger = logger,
            symbol = symbol,
            annotationFqName = KraftKspConstants.FQ_MAP_CONFIG
        ) ?: emptyList()

        return collectTagged(arrayAnnotations, symbol, KraftKspConstants.FQ_FIELD_MAPPING, ::parseFieldOverride)
    }

    private fun parseFieldOverride(pair: KSAnnotation, symbol: KSClassDeclaration): FieldOverride? {
        val from = pair.getStringArgOrNull(
            name = KraftKspConstants.ARG_SOURCE,
            logger = logger,
            symbol = symbol,
            annotationFqName = KraftKspConstants.FQ_FIELD_MAPPING
        ) ?: return null

        val to = pair.getStringArgOrNull(
            name = KraftKspConstants.ARG_TARGET,
            logger = logger,
            symbol = symbol,
            annotationFqName = KraftKspConstants.FQ_FIELD_MAPPING
        ) ?: return null

        return FieldOverride(source = from, target = to)
    }

    /**
     * Collects the annotations of type [fqName] from the `@MapConfig` array argument and from the
     * object itself, parses each one with [parse], and tags it with where it was written.
     */
    private fun <T : Any> collectTagged(
        arrayAnnotations: List<KSAnnotation>,
        symbol: KSClassDeclaration,
        fqName: String,
        parse: (KSAnnotation, KSClassDeclaration) -> T?
    ): List<Tagged<T>> {
        val fromArray = arrayAnnotations
            .filter { it.isAnnotation(fqName) }
            .mapNotNull { ann -> parse(ann, symbol)?.let { Tagged(Origin.ARRAY, it) } }
        val flat = symbol.annotations
            .filter { it.isAnnotation(fqName) }
            .mapNotNull { ann -> parse(ann, symbol)?.let { Tagged(Origin.FLAT, it) } }
            .toList()
        return fromArray + flat
    }

    /**
     * Reports KRAFT-E066 for each target (or ignored name) that is written both in the `@MapConfig`
     * arrays and as a flat annotation. Duplicates inside one form are not checked: they stay last-wins.
     */
    private fun reportRulesInBothForms(
        symbol: KSClassDeclaration,
        fieldOverrides: List<Tagged<FieldOverride>>,
        ignoredMappings: List<Tagged<IgnoredMappingConfig>>
    ) {
        val objectName = symbol.simpleName.asString()
        bothFormsKeys(fieldOverrides) { it.target }.forEach { target ->
            logger.fieldMappingInBothForms(target, objectName, symbol)
        }
        bothFormsKeys(ignoredMappings) { it.name }.forEach { name ->
            logger.mapIgnoreFieldInBothForms(name, objectName, symbol)
        }
    }

    private fun <T> bothFormsKeys(tagged: List<Tagged<T>>, key: (T) -> String): List<String> {
        val arrayKeys = tagged.filter { it.origin == Origin.ARRAY }.map { key(it.value) }.toSet()
        return tagged.filter { it.origin == Origin.FLAT }
            .map { key(it.value) }
            .filter { it in arrayKeys }
            .distinct()
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
            logger.report(
                KraftDiagnostic.MAP_USING_REVERSE_WITHOUT_MAP_REVERSE,
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

        val paramType = (
            if (fn.extensionReceiver != null) {
                fn.extensionReceiver!!.resolve()
            } else {
                fn.parameters.firstOrNull()?.type?.resolve()
            } ?: return ConverterDirection.FORWARD
            ).unwrapTypeAliases()

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
            logger.report(
                KraftDiagnostic.DUPLICATE_MAP_USING_TARGET,
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
            logger.report(
                KraftDiagnostic.MAP_USING_INTERNAL_LOOKUP_FAILED,
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

        val fromProp = mapUsingAnn.getStringArgOrDefault(
            name = KraftKspConstants.ARG_SOURCE,
            default = KraftKspConstants.DEFAULT_MAP_USING_SOURCE,
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
            logger.report(
                KraftDiagnostic.MAP_USING_BLANK_TARGET,
                "@MapUsing must specify a non-empty 'target' value", fn
            )
            return null
        }

        val isWholeSource = fromProp.isNullOrBlank()

        val directionName = mapUsingAnn.getEnumArgOrDefault(
            name = KraftKspConstants.ARG_DIRECTION,
            default = KraftKspConstants.DEFAULT_MAP_USING_DIRECTION,
            logger = logger,
            symbol = fn,
            annotationFqName = KraftKspConstants.FQ_MAP_USING
        )

        val direction = try {
            ConverterDirection.valueOf(directionName)
        } catch (_: IllegalArgumentException) {
            logger.report(
                KraftDiagnostic.MAP_USING_UNKNOWN_DIRECTION,
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
            logger.report(
                KraftDiagnostic.WHOLE_SOURCE_PARAMETER_MISMATCH,
                "@MapUsing whole-source converter " +
                    "'${fn.simpleName.asString()}': parameter type " +
                    "'$paramType' must match source class " +
                    "'${sourceClass.qualifiedName?.asString()}'",
                fn
            )
            return null
        }
        if (!typesMatch(returnType, targetType)) {
            logger.report(
                KraftDiagnostic.MAP_USING_RETURN_TYPE_MISMATCH,
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
            callPackageName = fn.packageName.asString(),
            callFunctionName = fn.simpleName.asString(),
            isExtension = fn.extensionReceiver != null,
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
            logger.report(
                KraftDiagnostic.MAP_USING_INTERNAL_LOOKUP_FAILED,
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
            callPackageName = fn.packageName.asString(),
            callFunctionName = fn.simpleName.asString(),
            isExtension = fn.extensionReceiver != null,
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
            logger.report(
                KraftDiagnostic.MAP_USING_UNKNOWN_PROPERTY,
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
                logger.report(
                    KraftDiagnostic.MAP_USING_EXTENSION_HAS_PARAMETERS,
                    "@MapUsing extension function '${fn.simpleName.asString()}' must not declare " +
                    "any value parameters — only the receiver is used. " +
                    "Found: ${params.map { it.name?.asString() }}",
                    fn
                )
                return false
            }
        } else if (params.size != 1) {
            logger.report(
                KraftDiagnostic.MAP_USING_INVALID_SIGNATURE,
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
        }.unwrapTypeAliases()
    }

    /**
     * Gets the return type of a function.
     */
    private fun getReturnType(fn: KSFunctionDeclaration): KSType? {
        val returnType = fn.returnType?.resolve()
        if (returnType == null) {
            logger.report(
                KraftDiagnostic.MAP_USING_MISSING_RETURN_TYPE,
                "@MapUsing function must have a return type",
                fn
            )
            return null
        }
        return returnType.unwrapTypeAliases()
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
            logger.report(
                KraftDiagnostic.MAP_USING_TYPE_NOT_CLASS,
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
            logger.report(
                KraftDiagnostic.MAP_USING_PARAMETER_MISMATCH,
                "Type mismatch in @MapUsing converter function '${fn.simpleName.asString()}': " +
                "Parameter type '${paramType}' doesn't match source property '${fromProp}' type '${sourceType}'",
                fn
            )
            return false
        }

        if (!typesMatch(returnType, targetType)) {
            logger.report(
                KraftDiagnostic.MAP_USING_RETURN_TYPE_MISMATCH,
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
    private fun typesMatch(rawA: KSType, rawB: KSType): Boolean {
        val a = rawA.unwrapTypeAliases()
        val b = rawB.unwrapTypeAliases()
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
     * Extracts [IgnoredMappingConfig] entries from both forms: the [ignoredMappings] array of
     * [@MapConfig][MapConfig] and flat [@MapIgnoreField][com.blu3berry.kraft.config.MapIgnoreField]
     * annotations on the object.
     */
    private fun extractIgnoredMappings(
        annotation: KSAnnotation,
        symbol: KSClassDeclaration
    ): List<Tagged<IgnoredMappingConfig>> {
        val arrayAnnotations = annotation.getArrayArgOrNull<KSAnnotation>(
            name = KraftKspConstants.ARG_IGNORED_MAPPINGS,
            logger = logger,
            symbol = symbol,
            annotationFqName = KraftKspConstants.FQ_MAP_CONFIG
        ) ?: emptyList()

        return collectTagged(
            arrayAnnotations, symbol, KraftKspConstants.FQ_MAP_IGNORE_FIELD, ::parseIgnoredMapping
        )
    }

    private fun parseIgnoredMapping(ignoreAnn: KSAnnotation, symbol: KSClassDeclaration): IgnoredMappingConfig? {
        val name = ignoreAnn.getStringArgOrNull(
            name = KraftKspConstants.ARG_NAME,
            logger = logger,
            symbol = symbol,
            annotationFqName = KraftKspConstants.FQ_MAP_IGNORE_FIELD
        ) ?: return null

        if (name.isBlank()) {
            logger.report(
                KraftDiagnostic.MAP_IGNORE_FIELD_BLANK_NAME,
                "@MapIgnoreField name must not be blank.",
                symbol
            )
            return null
        }

        val directionName = ignoreAnn.getEnumArgOrDefault(
            name = KraftKspConstants.ARG_DIRECTION,
            default = KraftKspConstants.DEFAULT_MAP_IGNORE_FIELD_DIRECTION,
            logger = logger,
            symbol = symbol,
            annotationFqName = KraftKspConstants.FQ_MAP_IGNORE_FIELD
        )

        val direction = try {
            IgnoreSide.valueOf(directionName)
        } catch (_: IllegalArgumentException) {
            logger.report(
                KraftDiagnostic.MAP_IGNORE_FIELD_UNKNOWN_DIRECTION,
                "@MapIgnoreField unknown direction '$directionName' on property '$name'.",
                symbol
            )
            return null
        }

        return IgnoredMappingConfig(name = name, direction = direction)
    }

    /** Where a rule was written: inside a `@MapConfig` array argument, or as an annotation on the object. */
    private enum class Origin { ARRAY, FLAT }

    private data class Tagged<T>(val origin: Origin, val value: T)
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
