package com.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.model.scan.ConfigObjectScanResult
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.processor.descriptor.propertyresolver.PropertyResolver
import com.blu3berry.kraft.processor.descriptor.util.toPropertyInfoMap
import com.blu3berry.kraft.processor.util.missingPrimaryConstructor

/**
 * Builds a reverse [MapperDescriptor] by swapping source/target of a forward descriptor.
 *
 * The reverse descriptor is a regular [MapperDescriptor] with swapped types, inverted
 * rename maps, flipped ignore sides, and validated reverse converters. It is consumed by the
 * same code generators as forward descriptors — no special codegen logic is needed.
 */
class ReverseDescriptorBuilder(
    private val logger: KSPLogger,
    private val forwardDescriptor: MapperDescriptor,
    private val configObjects: List<ConfigObjectScanResult>,
    private val errorNode: KSNode,
    private val globalConverters: GlobalConverterRegistry = GlobalConverterRegistry.EMPTY
) {

    fun build(): MapperDescriptor? {
        val newSourceDecl = forwardDescriptor.targetType.declaration
        val newTargetDecl = forwardDescriptor.sourceType.declaration
        val newTargetTypeName = newTargetDecl.qualifiedName?.asString()
            ?: newTargetDecl.simpleName.asString()
        val newSourceTypeName = newSourceDecl.qualifiedName?.asString()
            ?: newSourceDecl.simpleName.asString()
        val reverseNestedMappings = buildReverseNestedMappings()

        val newTargetProps = extractTargetProps(
            newTargetDecl, newTargetTypeName
        ) ?: return null

        val reverseConverters = resolveReverseConverters(
            newTargetTypeName
        ) ?: return null

        val ctx = buildMappingContext(
            newSourceDecl, newTargetProps,
            newSourceTypeName, newTargetTypeName,
            reverseConverters, reverseNestedMappings
        )

        val mappings = resolveAllProperties(
            newTargetProps, PropertyResolver(), ctx
        ) ?: return null

        return MapperDescriptor(
            id = MapperId(newSourceTypeName, newTargetTypeName),
            sourceType = forwardDescriptor.targetType,
            targetType = forwardDescriptor.sourceType,
            source = forwardDescriptor.source,
            propertyMappings = mappings,
            nestedMappings = reverseNestedMappings,
            converters = reverseConverters,
            aliasEmitMode = forwardDescriptor.aliasEmitMode,
        )
    }

    private fun extractTargetProps(
        newTargetDecl: KSClassDeclaration,
        newTargetTypeName: String
    ): List<PropertyInfo>? {
        val newTargetCtor = newTargetDecl.primaryConstructor ?: run {
            logger.missingPrimaryConstructor(newTargetTypeName, newTargetDecl)
            return null
        }
        return TargetPropertyExtractor(logger)
            .extract(newTargetDecl, newTargetCtor, newTargetTypeName)
    }

    private fun buildMappingContext(
        newSourceDecl: KSClassDeclaration,
        newTargetProps: List<PropertyInfo>,
        newSourceTypeName: String,
        newTargetTypeName: String,
        reverseConverters: List<ConverterDescriptor>,
        reverseNestedMappings: List<NestedMappingDescriptor>
    ): MappingContext {
        val newSourceProps = newSourceDecl.toPropertyInfoMap(logger)

        val ignoredMappings = configObjects.flatMap { it.ignoredMappings }
        val ignoredProperties = if (ignoredMappings.isNotEmpty()) {
            IgnoredPropertyAggregator.resolveConfigIgnored(
                logger = logger,
                ignoredMappings = ignoredMappings,
                targetPropNames = newTargetProps
                    .map { it.name }.toSet(),
                targetTypeName = newTargetTypeName,
                errorNode = errorNode,
                reverse = true
            )
        } else emptySet()

        val configsAllowGlobal = configObjects.all { it.useGlobalConverters }
        return MappingContext(
            logger = logger,
            sourceProps = newSourceProps,
            classRenames = extractInvertedClassRenames(),
            configRenames = extractInvertedConfigRenames(),
            converters = reverseConverters,
            globalConverters = if (configsAllowGlobal) globalConverters else GlobalConverterRegistry.EMPTY,
            nestedMappings = reverseNestedMappings,
            ignoredProperties = ignoredProperties,
            sourceTypeName = newSourceTypeName,
            targetTypeName = newTargetTypeName
        )
    }

    private fun buildReverseNestedMappings() =
        forwardDescriptor.nestedMappings.map { nested ->
            nested.copy(
                nestedMapperId = MapperId(
                    sourceQualifiedName = nested.nestedMapperId
                        .targetQualifiedName,
                    targetQualifiedName = nested.nestedMapperId
                        .sourceQualifiedName
                ),
                sourceType = nested.targetType,
                targetType = nested.sourceType
            )
        }

    /**
     * Invert class-level renames from the forward descriptor's property mappings.
     * Forward: Renamed(targetProperty=T, sourceProperty=S) → reverse needs T→S inverted to S→T
     * In MappingContext, renames are stored as targetName → sourceName.
     * So forward had (forwardTarget → forwardSource), reverse needs (newTarget → newSource)
     * where newTarget = forwardSource and newSource = forwardTarget.
     */
    private fun extractInvertedClassRenames(): Map<String, String> {
        val renames = mutableMapOf<String, String>()
        for (strategy in forwardDescriptor.propertyMappings) {
            if (strategy is PropertyMappingStrategy.Renamed) {
                // Forward: target.name mapped from source.name
                // Reverse: source.name (now target) mapped from target.name (now source)
                renames[strategy.sourceProperty.name] = strategy.targetProperty.name
            }
        }
        return renames
    }

    /**
     * Invert config-level renames: swap keys and values of forward fieldOverrides.
     */
    private fun extractInvertedConfigRenames(): Map<String, String> {
        val forward = configObjects.flatMap { it.fieldOverrides }
        // Forward: FieldOverride(source=S, target=T) → configRenames was T→S
        // Reverse: configRenames should be S→T
        return forward.associate { it.source to it.target }
    }

    /**
     * For each forward converter, find the matching reverse converter in the same config.
     * Forward: @MapUsing(source="a", target="b") fun convert(v: Int): String
     * Reverse needs: @MapUsing(source="b", target="a") fun reverseConvert(v: String): Int
     */
    private fun resolveReverseConverters(newTargetTypeName: String): List<ConverterDescriptor>? {
        val allConverters = configObjects.flatMap { it.converters }
        val forwardConverterStrategies = forwardDescriptor.propertyMappings
            .filterIsInstance<PropertyMappingStrategy.ConverterFunction>()

        if (forwardConverterStrategies.isEmpty()) {
            return allConverters.filter { it.resolvedDirection != ConverterDirection.FORWARD }
        }

        // Identify forward converter descriptors so we can exclude them from reverse resolution
        val forwardConverterDescriptors = forwardConverterStrategies.map { it.converter }.toSet()

        // Start with only non-forward converters (reverse-direction or unrelated)
        val reverseConverters = allConverters.filter {
            it !in forwardConverterDescriptors && it.resolvedDirection != ConverterDirection.FORWARD
        }.toMutableList()

        for (fwdStrategy in forwardConverterStrategies) {
            val fwdConverter = fwdStrategy.converter
            // Converters resolved from the global registry (synthetic enum mappers or @KraftConverter
            // extensions) have no enclosing config object. Their reverse direction is handled by the
            // GlobalConverterRule during resolveAllProperties — no config-object reverse is required.
            if (fwdConverter.enclosingObject == null) continue
            // The forward converter maps source.propA → target.propB
            // The reverse needs a converter mapping source.propB (old target) → target.propA (old source)
            val reversePropSource = fwdConverter.targetPropertyName  // old target prop is now source
            val reversePropTarget = fwdConverter.sourcePropertyName  // old source prop is now target

            val reverseConverter = reverseConverters.find { candidate ->
                if (reversePropTarget != null) {
                    // Property-source: reverse needs source=oldTarget, target=oldSource
                    candidate.sourcePropertyName == reversePropSource &&
                        candidate.targetPropertyName == reversePropTarget
                } else {
                    // Whole-source: reverse is also whole-source targeting the old source property
                    candidate.sourcePropertyName == null &&
                        candidate.targetPropertyName == reversePropSource
                }
            }

            if (reverseConverter == null) {
                val fwdSourceDesc = if (fwdConverter.sourcePropertyName != null)
                    "@MapUsing(source = \"${fwdConverter.sourcePropertyName}\", " +
                        "target = \"${fwdConverter.targetPropertyName}\")"
                else
                    "@MapUsing(target = \"${fwdConverter.targetPropertyName}\") (whole-source)"
                val neededDesc = if (reversePropTarget != null)
                    "@MapUsing(source = \"$reversePropSource\", target = \"$reversePropTarget\")"
                else
                    "@MapUsing(target = \"$reversePropSource\") with a reverse whole-source converter"

                logger.error(
                    "@MapReverse: property '${reversePropTarget ?: reversePropSource}' in reverse target " +
                        "'$newTargetTypeName' uses a forward converter but no reverse converter is defined.\n" +
                        "  Forward:  $fwdSourceDesc fun ${fwdConverter.functionName}(...)\n" +
                        "  Reverse needs: $neededDesc\n" +
                        "  How to fix:\n" +
                        "  - Add a reverse @MapUsing function in the same config object\n" +
                        "  - Or add @MapIgnoreField(\"${reversePropTarget ?: reversePropSource}\", " +
                        "direction = IgnoreSide.SOURCE) to skip in reverse",
                    errorNode
                )
                return null
            }
        }

        return reverseConverters
    }

    private fun resolveAllProperties(
        targetProps: List<PropertyInfo>,
        resolver: PropertyResolver,
        ctx: MappingContext
    ): List<PropertyMappingStrategy>? {
        val result = mutableListOf<PropertyMappingStrategy>()
        for (prop in targetProps) {
            val strategy = resolver.resolve(prop, ctx) ?: return null
            result += strategy
        }
        return result
    }
}
