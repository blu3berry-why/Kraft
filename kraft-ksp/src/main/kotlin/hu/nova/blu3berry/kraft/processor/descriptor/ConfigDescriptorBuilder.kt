package hu.nova.blu3berry.kraft.processor.descriptor

import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.PropertyResolver
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import hu.nova.blu3berry.kraft.model.scan.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import hu.nova.blu3berry.kraft.model.descriptor.MapperDescriptor
import hu.nova.blu3berry.kraft.model.MapperId
import hu.nova.blu3berry.kraft.model.descriptor.MappingContext
import hu.nova.blu3berry.kraft.model.descriptor.MappingSource
import hu.nova.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.model.toTypeInfo
import hu.nova.blu3berry.kraft.processor.descriptor.util.toPropertyInfoMap
import hu.nova.blu3berry.kraft.processor.util.missingPrimaryConstructor

class ConfigDescriptorBuilder(
    private val logger: KSPLogger,
    private val config: ConfigObjectScanResult,
    private val enumMappings: List<EnumMappingDescriptor>
) {

    fun build(): MapperDescriptor? {
        val fromDecl = config.sourceType
        val toDecl = config.targetType

        val fromTypeInfo = fromDecl.toTypeInfo(fromDecl.asStarProjectedType())
        val toTypeInfo = toDecl.toTypeInfo(toDecl.asStarProjectedType())

        val sourceProps = fromDecl.toPropertyInfoMap(logger)
        val targetCtor = toDecl.primaryConstructor ?: run {
            logger.missingPrimaryConstructor(toDecl.simpleName.asString(), toDecl)
            return null
        }

        val targetProps = TargetPropertyExtractor(logger).extract(toDecl, targetCtor, toDecl.simpleName.asString()) ?: return null

        val ignoredProperties = buildIgnoredProperties(targetProps, toDecl.simpleName.asString())
        val ctx = buildMappingContext(fromDecl, toDecl, sourceProps, config.nestedMappings, ignoredProperties)

        val resolver = PropertyResolver()
        val mappings = resolveAllProperties(targetProps, resolver, ctx) ?: return null

        val enums = enumMappingsFor(fromDecl, toDecl)


        return MapperDescriptor(
            id = MapperId(
                fromDecl.qualifiedName?.asString() ?: fromDecl.simpleName.asString(),
                toDecl.qualifiedName?.asString() ?: toDecl.simpleName.asString()
            ),
            sourceType = fromTypeInfo,
            targetType = toTypeInfo,
            source = MappingSource.ConfigObject(config.configObject),
            propertyMappings = mappings,
            enumMappings = enums,
            converters = config.converters,
            nestedMappings = config.nestedMappings,
        )
    }

    // ---------------------------------------------------------
    // Build MappingContext used by chain resolver
    // ---------------------------------------------------------
    private fun buildMappingContext(
        fromDecl: KSClassDeclaration,
        toDecl: KSClassDeclaration,
        sourceProps: Map<String, PropertyInfo>,
        nestedMappings: List<NestedMappingDescriptor>,
        ignoredProperties: Set<String>
    ): MappingContext {
        return MappingContext(
            logger = logger,
            sourceProps = sourceProps,
            classRenames = emptyMap(),                         // config mode → no @MapField
            configRenames = config.fieldOverrides.associate { it.target to it.source },
            converters = config.converters,
            nestedMappings = nestedMappings,
            ignoredProperties = ignoredProperties,
            sourceTypeName = fromDecl.qualifiedName?.asString() ?: fromDecl.simpleName.asString(),
            targetTypeName = toDecl.qualifiedName?.asString() ?: toDecl.simpleName.asString()
        )
    }

    // ---------------------------------------------------------
    // Build the set of target property names to ignore
    // ---------------------------------------------------------
    private fun buildIgnoredProperties(
        targetProps: List<PropertyInfo>,
        targetTypeName: String
    ): Set<String> {
        val targetPropNames = targetProps.map { it.name }.toSet()
        return IgnoredPropertyAggregator.resolveConfigIgnored(
            logger, config.ignoredMappings, targetPropNames, targetTypeName, config.configObject
        )
    }

    // ---------------------------------------------------------
    // Use chain resolver to map all target properties
    // ---------------------------------------------------------
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

    // ---------------------------------------------------------
    // Filter relevant enum converters
    // ---------------------------------------------------------
    private fun enumMappingsFor(
        fromDecl: KSClassDeclaration,
        toDecl: KSClassDeclaration
    ): List<EnumMappingDescriptor> =
        enumMappings.filter {
            it.sourceType.declaration == fromDecl &&
                    it.targetType.declaration == toDecl
        }
}
