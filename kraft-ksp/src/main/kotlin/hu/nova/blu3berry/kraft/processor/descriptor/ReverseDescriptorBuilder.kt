package hu.nova.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import hu.nova.blu3berry.kraft.model.MapperId
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.descriptor.ConverterDescriptor
import hu.nova.blu3berry.kraft.model.descriptor.MapperDescriptor
import hu.nova.blu3berry.kraft.model.descriptor.MappingContext
import hu.nova.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.model.scan.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.PropertyResolver
import hu.nova.blu3berry.kraft.processor.descriptor.util.toPropertyInfoMap
import hu.nova.blu3berry.kraft.processor.util.missingPrimaryConstructor

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
    private val errorNode: KSNode
) {

    fun build(): MapperDescriptor? {
        // Reverse: old target becomes new source, old source becomes new target
        val newSourceDecl = forwardDescriptor.targetType.declaration
        val newTargetDecl = forwardDescriptor.sourceType.declaration

        val newSourceTypeName = newSourceDecl.qualifiedName?.asString() ?: newSourceDecl.simpleName.asString()
        val newTargetTypeName = newTargetDecl.qualifiedName?.asString() ?: newTargetDecl.simpleName.asString()

        val newSourceTypeInfo = forwardDescriptor.targetType
        val newTargetTypeInfo = forwardDescriptor.sourceType

        // Extract new target's constructor properties (old source's properties)
        val newTargetCtor = newTargetDecl.primaryConstructor ?: run {
            logger.missingPrimaryConstructor(newTargetTypeName, newTargetDecl)
            return null
        }
        val newTargetProps = TargetPropertyExtractor(logger)
            .extract(newTargetDecl, newTargetCtor, newTargetTypeName) ?: return null

        // Extract new source properties (old target's declared properties)
        val newSourceProps = newSourceDecl.toPropertyInfoMap(logger)

        // Invert rename maps: forward was targetName → sourceName, reverse needs newTargetName → newSourceName
        val invertedClassRenames = extractInvertedClassRenames()
        val invertedConfigRenames = extractInvertedConfigRenames()

        // Resolve ignored properties with reverse=true (SOURCE entries now active)
        val ignoredMappings = configObjects.flatMap { it.ignoredMappings }
        val ignoredProperties = if (ignoredMappings.isNotEmpty()) {
            IgnoredPropertyAggregator.resolveConfigIgnored(
                logger = logger,
                ignoredMappings = ignoredMappings,
                targetPropNames = newTargetProps.map { it.name }.toSet(),
                targetTypeName = newTargetTypeName,
                errorNode = errorNode,
                reverse = true
            )
        } else emptySet()

        // Validate and collect reverse converters
        val reverseConverters = resolveReverseConverters(newTargetTypeName) ?: return null

        // Build nested mapping descriptors with swapped source/target
        val reverseNestedMappings = forwardDescriptor.nestedMappings.map { nested ->
            nested.copy(
                nestedMapperId = MapperId(
                    sourceQualifiedName = nested.nestedMapperId.targetQualifiedName,
                    targetQualifiedName = nested.nestedMapperId.sourceQualifiedName
                ),
                sourceType = nested.targetType,
                targetType = nested.sourceType
            )
        }

        val ctx = MappingContext(
            logger = logger,
            sourceProps = newSourceProps,
            classRenames = invertedClassRenames,
            configRenames = invertedConfigRenames,
            converters = reverseConverters,
            nestedMappings = reverseNestedMappings,
            ignoredProperties = ignoredProperties,
            sourceTypeName = newSourceTypeName,
            targetTypeName = newTargetTypeName
        )

        val resolver = PropertyResolver()
        val mappings = resolveAllProperties(newTargetProps, resolver, ctx) ?: return null

        return MapperDescriptor(
            id = MapperId(newSourceTypeName, newTargetTypeName),
            sourceType = newSourceTypeInfo,
            targetType = newTargetTypeInfo,
            source = forwardDescriptor.source,
            propertyMappings = mappings,
            nestedMappings = reverseNestedMappings,
            converters = reverseConverters
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

        if (forwardConverterStrategies.isEmpty()) return allConverters

        // Identify forward converter descriptors so we can exclude them from reverse resolution
        val forwardConverterDescriptors = forwardConverterStrategies.map { it.converter }.toSet()

        // Start with only non-forward converters (reverse-direction or unrelated)
        val reverseConverters = allConverters.filter { it !in forwardConverterDescriptors }.toMutableList()

        for (fwdStrategy in forwardConverterStrategies) {
            val fwdConverter = fwdStrategy.converter
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
                    "@MapUsing(source = \"${fwdConverter.sourcePropertyName}\", target = \"${fwdConverter.targetPropertyName}\")"
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
