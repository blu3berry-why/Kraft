package com.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.blu3berry.kraft.config.ConverterDirection
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.MappingDirection
import com.blu3berry.kraft.model.descriptor.MappingSource
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.model.scan.ClassMappingScanResult
import com.blu3berry.kraft.model.scan.ConfigObjectScanResult
import com.blu3berry.kraft.model.scan.FieldOverride
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.model.scan.MapNestedAnnotation
import com.blu3berry.kraft.model.toTypeInfo
import com.blu3berry.kraft.processor.descriptor.propertyresolver.PropertyResolver
import com.blu3berry.kraft.processor.descriptor.util.toPropertyInfoMap
import com.blu3berry.kraft.processor.util.missingPrimaryConstructor

class ClassDescriptorBuilder(
    private val logger: KSPLogger,
    private val mapping: ClassMappingScanResult,
    private val configObjects: List<ConfigObjectScanResult>,
    private val enumMappings: List<EnumMappingDescriptor>,
    private val globalConverters: GlobalConverterRegistry = GlobalConverterRegistry.EMPTY
) {

    fun build(): MapperDescriptor? {
        val sourceDecl = mapping.sourceType
        val targetDecl = mapping.targetType

        val sourceTypeName =
            sourceDecl.qualifiedName?.asString() ?: sourceDecl.simpleName.asString()
        val targetTypeName =
            targetDecl.qualifiedName?.asString() ?: targetDecl.simpleName.asString()

        val fromTypeInfo = sourceDecl.toTypeInfo(sourceDecl.asStarProjectedType())
        val toTypeInfo = targetDecl.toTypeInfo(targetDecl.asStarProjectedType())

        val targetCtor = targetDecl.primaryConstructor ?: run {
            logger.missingPrimaryConstructor(targetTypeName, targetDecl)
            return null
        }

        val sourceProps = sourceDecl.toPropertyInfoMap(logger)
        val targetProps =
            TargetPropertyExtractor(logger).extract(targetDecl, targetCtor, targetTypeName)
                ?: return null

        val classRenames = extractClassOverrides()
        val ignoredProperties =
            IgnoredPropertyAggregator(logger).aggregate(mapping, configObjects, targetProps, targetTypeName)
        val configRenames = configObjects.toConfigOverridesMap()
        // Exclude reverse-only converters from the forward mapping context
        val converters = configObjects.flatMap { it.converters }
            .filter { it.resolvedDirection != ConverterDirection.REVERSE }
        val nestedMappings = configObjects.flatMap { it.nestedMappings }
        val classNestedOverrides = extractClassNestedOverrides()

        val configsAllowGlobal = configObjects.all { it.useGlobalConverters }
        val ctx = MappingContext(
            logger = logger,
            sourceProps = sourceProps,
            classRenames = classRenames,
            configRenames = configRenames,
            converters = converters,
            globalConverters = if (configsAllowGlobal) globalConverters else GlobalConverterRegistry.EMPTY,
            ignoredProperties = ignoredProperties,
            nestedMappings = nestedMappings,
            classNestedOverrides = classNestedOverrides,
            sourceTypeName = sourceTypeName,
            targetTypeName = targetTypeName
        )

        val resolver = PropertyResolver()
        val mappings = resolveAllProperties(targetProps, resolver, ctx) ?: return null

        return MapperDescriptor(
            id = MapperId(sourceTypeName, targetTypeName),
            sourceType = fromTypeInfo,
            targetType = toTypeInfo,
            source = MappingSource.ClassAnnotation(mapping.annotatedClass, mapping.direction),
            propertyMappings = mappings,
            enumMappings = enumMappings.filter {
                it.sourceType.declaration == sourceDecl &&
                        it.targetType.declaration == targetDecl
            },
            converters = converters
        )
    }

    // ---------------------------------------------------------
    // Extract class-level nested overrides (@MapNested)
    // ---------------------------------------------------------
    private fun extractClassNestedOverrides(): Map<String, MapNestedAnnotation> =
        mapping.propertyScanResults
            .filter { it.mapNested != MapNestedAnnotation.NotAnnotated }
            .associate { it.property.simpleName.asString() to it.mapNested }

    // ---------------------------------------------------------
    // Extract class-level overrides (@MapField)
    // ---------------------------------------------------------
    private fun extractClassOverrides(): Map<String, String> =
        mapping.propertyScanResults
            .mapNotNull { s ->
                val name = s.property.simpleName.asString()
                val from = s.mapFieldSourceName ?: return@mapNotNull null
                if (mapping.direction == MappingDirection.MAP_FROM) {
                    // MAP_FROM: counterPartName = source property name
                    name to from
                } else {
                    // MAP_TO: counterPartName = target property name
                    from to name
                }
            }.toMap()

    // ---------------------------------------------------------
    // Resolve all mappings with the chain resolver
    // ---------------------------------------------------------
    private fun resolveAllProperties(
        targetProps: List<PropertyInfo>,
        resolver: PropertyResolver,
        ctx: MappingContext
    ): List<PropertyMappingStrategy>? {

        val result = mutableListOf<PropertyMappingStrategy>()

        for (prop in targetProps) {
            val resolved = resolver.resolve(prop, ctx) ?: return null
            result += resolved
        }

        return result
    }
}


// ---------------------------------------------------------
// Helpers for config-level overrides
// ---------------------------------------------------------


fun List<ConfigObjectScanResult>.toConfigOverridesMap() =
    this.flatMap { it.fieldOverrides }.toTargetToSourceMap()

/** Maps each [FieldOverride] as targetPropertyName → sourcePropertyName. */
fun List<FieldOverride>.toTargetToSourceMap() = this.associate { it.target to it.source }
