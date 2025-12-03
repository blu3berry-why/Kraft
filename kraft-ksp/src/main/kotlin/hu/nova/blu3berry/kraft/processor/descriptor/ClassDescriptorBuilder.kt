package hu.nova.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.model.*
import hu.nova.blu3berry.kraft.processor.scanner.ClassMappingScanResult
import hu.nova.blu3berry.kraft.processor.scanner.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.processor.util.constructorPropertyMismatch
import hu.nova.blu3berry.kraft.processor.util.missingConstructorProperty
import hu.nova.blu3berry.kraft.processor.util.missingPrimaryConstructor
import hu.nova.blu3berry.kraft.processor.util.unmappedNonNullableProperty
import hu.nova.blu3berry.kraft.processor.util.unsupportedTypeInConstructor

/**
 * Builds a single [MapperDescriptor] from one class-level mapping declaration:
 *
 *   sourceType -> targetType
 *
 * coming from a single @MapFrom / @MapTo annotated class.
 *
 * Strict behaviour:
 *  - Every primary constructor parameter of the TARGET must have a matching
 *    property in the SOURCE with the same name and the same KSType.
 *  - If no matching property is found → error, returns null.
 *  - If the type does not match exactly → error, returns null.
 *
 * Config objects are accepted but not used here – they represent an
 * alternative way to declare mappers (handled separately).
 */

class ClassDescriptorBuilder(
    private val logger: KSPLogger,
    private val mapping: ClassMappingScanResult,
    private val configObjects: List<ConfigObjectScanResult>,
    private val enumMappings: List<EnumMappingDescriptor>
) {

    fun build(): MapperDescriptor? {
        val sourceClass = mapping.sourceType
        val targetClass = mapping.targetType

        val sourceTypeName = sourceClass.qualifiedName?.asString() ?: sourceClass.simpleName.asString()
        val targetTypeName = targetClass.qualifiedName?.asString() ?: targetClass.simpleName.asString()

        val fromTypeInfo = sourceClass.toTypeInfo(sourceClass.asStarProjectedType())
        val toTypeInfo = targetClass.toTypeInfo(targetClass.asStarProjectedType())

        // --------------------------------------------------------------------
        // Missing primary constructor
        // --------------------------------------------------------------------
        val targetCtor = targetClass.primaryConstructor
            ?: run {
                logger.missingPrimaryConstructor(targetTypeName, targetClass)
                return null
            }

        // --------------------------------------------------------------------
        // Source properties
        // --------------------------------------------------------------------
        val sourcePropertiesByName = sourceClass.toPropertyInfoMap()

        // --------------------------------------------------------------------
        // Target properties
        // --------------------------------------------------------------------
        val targetProperties = targetCtor.parameters.mapNotNull { param ->
            val paramName = param.name?.asString() ?: return@mapNotNull null

            val declProp = targetClass.getDeclaredProperties()
                .firstOrNull { it.simpleName.asString() == paramName }
                ?: run {
                    logger.missingConstructorProperty(
                        typeName = targetTypeName,
                        parameterName = paramName,
                        available = targetClass.getDeclaredProperties().map { it.simpleName.asString() }.toList(),
                        symbol = param
                    )
                    return@mapNotNull null
                }

            val ksType = param.type.resolve()
            val classDecl = ksType.declaration as? KSClassDeclaration
                ?: run {
                    logger.unsupportedTypeInConstructor(
                        typeName = targetTypeName,
                        parameterName = paramName,
                        actualType = ksType.toString(),
                        symbol = param
                    )
                    return@mapNotNull null
                }

            PropertyInfo(
                name = paramName,
                type = classDecl.toTypeInfo(ksType),
                declaration = declProp,
                hasDefault = param.hasDefault
            )
        }

        // Constructor mismatch
        if (targetProperties.size != targetCtor.parameters.size) {
            logger.constructorPropertyMismatch(targetTypeName, targetClass)
            return null
        }

        // --------------------------------------------------------------------
        // Override map (@MapField)
        // --------------------------------------------------------------------
        val classOverrides = mapping.propertyScanResults
            .mapNotNull { s ->
                val targetProp = s.property.simpleName.asString()
                val src = s.mapFieldOther
                if (src != null) targetProp to src else null
            }
            .toMap()

        // --------------------------------------------------------------------
        // Collect converters from config objects
        // --------------------------------------------------------------------
        val converters = configObjects.flatMap { it.converters }

        // --------------------------------------------------------------------
        // PropertyResolver (handles mapping errors)
        // --------------------------------------------------------------------
        val resolver = PropertyResolver(
            logger = logger,
            sourceProperties = sourcePropertiesByName,
            configMappings = configObjects,
            classLevelOverrides = classOverrides,
            converters = converters
        )

        val propertyMappings = mutableListOf<PropertyMappingStrategy>()
        for (prop in targetProperties) {
            val strategy = resolver.resolve(
                targetProperty = prop,
                sourceTypeName = sourceTypeName,
                targetTypeName = targetTypeName
            )
            if (strategy == null) return null
            propertyMappings += strategy
        }

        return MapperDescriptor(
            id = MapperId(sourceTypeName, targetTypeName),
            fromType = fromTypeInfo,
            toType = toTypeInfo,
            source = MappingSource.ClassAnnotation(
                annotatedClass = mapping.annotatedClass,
                direction = mapping.direction
            ),
            propertyMappings = propertyMappings,
            enumMappings = enumMappings,
            converters = converters
        )
    }

    private fun KSClassDeclaration.toPropertyInfoMap(): Map<String, PropertyInfo> =
        getDeclaredProperties().associate { prop ->
            val ks = prop.type.resolve()
            val typeDecl = ks.declaration as KSClassDeclaration
            prop.simpleName.asString() to PropertyInfo(
                name = prop.simpleName.asString(),
                type = typeDecl.toTypeInfo(ks),
                declaration = prop,
                hasDefault = false
            )
        }
}
