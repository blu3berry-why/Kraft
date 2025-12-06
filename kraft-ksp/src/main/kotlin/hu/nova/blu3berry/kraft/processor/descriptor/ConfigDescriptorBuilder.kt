package hu.nova.blu3berry.kraft.processor.descriptor

import PropertyResolver
import hu.nova.blu3berry.kraft.model.*
import hu.nova.blu3berry.kraft.processor.descriptor.util.toPropertyInfoMap
import hu.nova.blu3berry.kraft.model.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.processor.util.missingConstructorProperty
import hu.nova.blu3berry.kraft.processor.util.missingPrimaryConstructor
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class ConfigDescriptorBuilder(
    private val logger: KSPLogger,
    private val config: ConfigObjectScanResult,
    private val enumMappings: List<EnumMappingDescriptor>
) {

    fun build(): MapperDescriptor? {
        val fromDecl = config.fromType
        val toDecl = config.toType

        val fromTypeInfo = fromDecl.toTypeInfo(fromDecl.asStarProjectedType())
        val toTypeInfo = toDecl.toTypeInfo(toDecl.asStarProjectedType())

        val sourceProps = fromDecl.toPropertyInfoMap()
        val targetCtor = toDecl.primaryConstructor ?: run {
            logger.missingPrimaryConstructor(toDecl.simpleName.asString(), toDecl)
            return null
        }

        val targetProps = extractTargetProperties(toDecl, targetCtor) ?: return null

        val ctx = buildMappingContext(fromDecl, toDecl, sourceProps, config.nestedMappings)

        val resolver = PropertyResolver()
        val mappings = resolveTargetProperties(targetProps, resolver, ctx) ?: return null

        val enums = enumMappingsFor(fromDecl, toDecl)


        return MapperDescriptor(
            id = MapperId(fromDecl.toString(), toDecl.toString()),
            fromType = fromTypeInfo,
            toType = toTypeInfo,
            source = MappingSource.ConfigObject(config.configObject),
            propertyMappings = mappings,
            enumMappings = enums,
            converters = config.converters,
            nestedMappings = config.nestedMappings,
        )
    }

    // ---------------------------------------------------------
    // Extract constructor → PropertyInfo list
    // ---------------------------------------------------------
    private fun extractTargetProperties(
        toDecl: KSClassDeclaration,
        targetCtor: KSFunctionDeclaration
    ): List<PropertyInfo>? {

        return targetCtor.parameters.mapNotNull { param ->
            val name = param.name?.asString() ?: return@mapNotNull null

            val declProp = toDecl.getDeclaredProperties()
                .firstOrNull { it.simpleName.asString() == name }
                ?: run {
                    logger.missingConstructorProperty(
                        typeName = toDecl.simpleName.asString(),
                        parameterName = name,
                        available = toDecl.getDeclaredProperties().map { it.simpleName.asString() }.toList(),
                        symbol = param
                    )
                    return null
                }

            val ksType = param.type.resolve()
            val classDecl = ksType.declaration as? KSClassDeclaration ?: return@mapNotNull null

            PropertyInfo(
                name = name,
                type = classDecl.toTypeInfo(ksType),
                declaration = declProp,
                hasDefault = param.hasDefault
            )
        }
    }

    // ---------------------------------------------------------
    // Build MappingContext used by chain resolver
    // ---------------------------------------------------------
    private fun buildMappingContext(
        fromDecl: KSClassDeclaration,
        toDecl: KSClassDeclaration,
        sourceProps: Map<String, PropertyInfo>,
        nestedMappings: List<NestedMappingDescriptor>
    ): MappingContext {

        return MappingContext(
            logger = logger,
            sourceProps = sourceProps,
            classOverrides = emptyMap(),                       // config mode → no @MapField
            configOverrides = config.fieldOverrides.associate { it.to to it.from },
            converters = config.converters,
            nestedMappings = nestedMappings,
            sourceTypeName = fromDecl.qualifiedName?.asString() ?: fromDecl.simpleName.asString(),
            targetTypeName = toDecl.qualifiedName?.asString() ?: toDecl.simpleName.asString()
        )
    }

    // ---------------------------------------------------------
    // Use chain resolver to map all target properties
    // ---------------------------------------------------------
    private fun resolveTargetProperties(
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
