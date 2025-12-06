import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import hu.nova.blu3berry.kraft.model.EnumMappingDescriptor
import hu.nova.blu3berry.kraft.model.MapperDescriptor
import hu.nova.blu3berry.kraft.model.MapperId
import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.MappingSource
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.model.toTypeInfo
import hu.nova.blu3berry.kraft.processor.descriptor.util.toPropertyInfoMap
import hu.nova.blu3berry.kraft.model.ClassMappingScanResult
import hu.nova.blu3berry.kraft.model.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.model.FieldOverride
import hu.nova.blu3berry.kraft.processor.util.constructorPropertyMismatch
import hu.nova.blu3berry.kraft.processor.util.missingConstructorProperty
import hu.nova.blu3berry.kraft.processor.util.missingPrimaryConstructor
import hu.nova.blu3berry.kraft.processor.util.unsupportedTypeInConstructor


class ClassDescriptorBuilder(
    private val logger: KSPLogger,
    private val mapping: ClassMappingScanResult,
    private val configObjects: List<ConfigObjectScanResult>,
    private val enumMappings: List<EnumMappingDescriptor>
) {

    fun build(): MapperDescriptor? {
        val sourceDecl = mapping.sourceType
        val targetDecl = mapping.targetType

        val sourceTypeName = sourceDecl.qualifiedName?.asString() ?: sourceDecl.simpleName.asString()
        val targetTypeName = targetDecl.qualifiedName?.asString() ?: targetDecl.simpleName.asString()

        val fromTypeInfo = sourceDecl.toTypeInfo(sourceDecl.asStarProjectedType())
        val toTypeInfo = targetDecl.toTypeInfo(targetDecl.asStarProjectedType())

        val targetCtor = targetDecl.primaryConstructor ?: run {
            logger.missingPrimaryConstructor(targetTypeName, targetDecl)
            return null
        }

        val sourceProps = sourceDecl.toPropertyInfoMap()
        val targetProps = extractTargetProperties(targetDecl, targetCtor, targetTypeName) ?: return null

        val classOverrides = extractClassOverrides()
        val configOverrides = configObjects.toConfigOverridesMap()
        val converters = configObjects.flatMap { it.converters }

        val ctx = MappingContext(
            logger = logger,
            sourceProps = sourceProps,
            classOverrides = classOverrides,
            configOverrides = configOverrides,
            converters = converters,
            sourceTypeName = sourceTypeName,
            targetTypeName = targetTypeName
        )

        val resolver = PropertyResolver()
        val mappings = resolveAllProperties(targetProps, resolver, ctx) ?: return null

        return MapperDescriptor(
            id = MapperId(sourceTypeName, targetTypeName),
            fromType = fromTypeInfo,
            toType = toTypeInfo,
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
    // Extract and validate target-side constructor properties
    // ---------------------------------------------------------
    private fun extractTargetProperties(
        targetDecl: KSClassDeclaration,
        targetCtor: KSFunctionDeclaration,
        targetTypeName: String
    ): List<PropertyInfo>? {

        val props = targetCtor.parameters.mapNotNull { param ->
            val name = param.name?.asString() ?: return@mapNotNull null

            val declProp = targetDecl.getDeclaredProperties()
                .firstOrNull { it.simpleName.asString() == name }
                ?: run {
                    logger.missingConstructorProperty(
                        typeName = targetTypeName,
                        parameterName = name,
                        available = targetDecl.getDeclaredProperties().map { it.simpleName.asString() }.toList(),
                        symbol = param
                    )
                    return null
                }

            val ksType = param.type.resolve()
            val decl = ksType.declaration as? KSClassDeclaration ?: run {
                logger.unsupportedTypeInConstructor(
                    typeName = targetTypeName,
                    parameterName = name,
                    actualType = ksType.toString(),
                    symbol = param
                )
                return@mapNotNull null
            }

            PropertyInfo(
                name = name,
                type = decl.toTypeInfo(ksType),
                declaration = declProp,
                hasDefault = param.hasDefault
            )
        }

        if (props.size != targetCtor.parameters.size) {
            logger.constructorPropertyMismatch(targetTypeName, targetDecl)
            return null
        }

        return props
    }

    // ---------------------------------------------------------
    // Extract class-level overrides (@MapField)
    // ---------------------------------------------------------
    private fun extractClassOverrides(): Map<String, String> =
        mapping.propertyScanResults
            .mapNotNull { s ->
                val name = s.property.simpleName.asString()
                val from = s.mapFieldOther ?: return@mapNotNull null
                name to from
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
    this.flatMap { it.fieldOverrides }.associateKeys()

fun List<FieldOverride>.associateKeys() = this.associate { it.to to it.from }