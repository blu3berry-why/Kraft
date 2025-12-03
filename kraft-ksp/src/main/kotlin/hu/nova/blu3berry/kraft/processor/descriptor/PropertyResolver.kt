package hu.nova.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.Nullability
import hu.nova.blu3berry.kraft.model.*
import hu.nova.blu3berry.kraft.processor.scanner.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.processor.util.*

/**
 * Phase 1 resolver:
 *  - Ignored
 *  - Overrides (@MapField)
 *  - Direct matches
 *  - Required-field errors (improved)
 *
 * Next phases will add:
 *  - Converters
 *  - Nested mapping
 *  - Collections
 */
class PropertyResolver(
    private val logger: KSPLogger,
    private val sourceProperties: Map<String, PropertyInfo>,
    private val configMappings: List<ConfigObjectScanResult>,
    private val classLevelOverrides: Map<String, String>,
    private val converters: List<ConverterDescriptor> = emptyList()
) {

    fun resolve(
        targetProperty: PropertyInfo,
        sourceTypeName: String,
        targetTypeName: String
    ): PropertyMappingStrategy? {

        val targetName = targetProperty.name

        // -------------------------------------------------------
        // 0) CONVERTER FUNCTION
        // -------------------------------------------------------
        val converter = findConverter(targetName)
        if (converter != null) {
            val sourcePropName = getSourcePropertyNameForConverter(converter)
            val sourceProp = sourceProperties[sourcePropName]

            if (sourceProp == null) {
                logger.error(
                    "Unknown source property '$sourcePropName' in @MapUsing of ${converter.enclosingObject?.simpleName?.asString() ?: "unknown"}. " +
                    "Available: ${sourceProperties.keys.joinToString(", ")}",
                    converter.function
                )
                return null
            }

            // We don't check type compatibility here because the converter's purpose
            // is to convert between different types

            return PropertyMappingStrategy.ConverterFunction(
                targetProperty = targetProperty,
                sourceProperty = sourceProp,
                converter = converter
            )
        }

        // -------------------------------------------------------
        // 1) IGNORE
        // -------------------------------------------------------
        if (isIgnored(targetName)) {
            return PropertyMappingStrategy.Ignored(targetProperty)
        }

        // -------------------------------------------------------
        // 2) CLASS-LEVEL OVERRIDE: @MapField("sourceName")
        // -------------------------------------------------------
        val overrideSourceName = classLevelOverrides[targetName]
        if (overrideSourceName != null) {

            val sourceProp = sourceProperties[overrideSourceName]

            if (sourceProp == null) {
                logger.invalidMapFieldOverride(
                    sourceType = sourceTypeName,
                    targetPropertyName = targetName,
                    referencedSourceName = overrideSourceName,
                    sourceProperties = sourceProperties,
                    symbol = targetProperty.declaration
                )
                return null
            }

            if (!typesMatch(sourceProp, targetProperty)) {
                logger.detailedTypeMismatch(
                    sourceType = sourceTypeName,
                    targetType = targetTypeName,
                    sourceProperty = sourceProp,
                    targetProperty = targetProperty,
                    symbol = targetProperty.declaration
                )
                return null
            }

            return PropertyMappingStrategy.Renamed(
                targetProperty = targetProperty,
                sourceProperty = sourceProp
            )
        }

        // -------------------------------------------------------
        // 3) DIRECT MATCH
        // -------------------------------------------------------
        val directSource = sourceProperties[targetName]
        if (directSource != null) {
            if (!typesMatch(directSource, targetProperty)) {
                logger.detailedTypeMismatch(
                    sourceType = sourceTypeName,
                    targetType = targetTypeName,
                    sourceProperty = directSource,
                    targetProperty = targetProperty,
                    symbol = targetProperty.declaration
                )
                return null
            }

            return PropertyMappingStrategy.Direct(
                targetProperty = targetProperty,
                sourceProperty = directSource
            )
        }

        // -------------------------------------------------------
        // 4) REQUIRED FIELD ERROR
        // -------------------------------------------------------
        val isRequired =
            targetProperty.type.ksType.nullability == Nullability.NOT_NULL &&
                    !targetProperty.hasDefault

        if (isRequired) {
            logger.detailedMissingMapping(
                sourceType = sourceTypeName,
                targetType = targetTypeName,
                targetProperty = targetProperty,
                sourceProperties = sourceProperties,
                classLevelOverrides = classLevelOverrides,
                configOverrides = configMappings.flatMap { it.fieldOverrides },
                symbol = targetProperty.declaration
            )
        } else {
            // Optional property without mapping
            logger.error(
                "Optional property '${targetProperty.name}' in target type '$targetTypeName' has no mapping.",
                targetProperty.declaration
            )
        }

        return null
    }

    // -------------------------------------------------------
    // Helper functions
    // -------------------------------------------------------

    private fun findConverter(targetName: String): ConverterDescriptor? {
        // Find a converter where the 'to' property matches the target property name
        // In case of multiple matches, we could add error handling here
        return converters.firstOrNull { converter ->
            val mapUsingAnn = converter.function.annotations.first { 
                it.annotationType.resolve().declaration.qualifiedName?.asString() == hu.nova.blu3berry.kraft.processor.scanner.ConfigObjectScanner.MAP_USING_FQ
            }

            val toProp = mapUsingAnn.arguments.firstOrNull { 
                it.name?.asString() == "to" 
            }?.value?.toString()

            toProp == targetName
        }
    }

    private fun getSourcePropertyNameForConverter(converter: ConverterDescriptor): String {
        val mapUsingAnn = converter.function.annotations.first { 
            it.annotationType.resolve().declaration.qualifiedName?.asString() == hu.nova.blu3berry.kraft.processor.scanner.ConfigObjectScanner.MAP_USING_FQ
        }

        return mapUsingAnn.arguments.firstOrNull { 
            it.name?.asString() == "from" 
        }?.value?.toString() ?: ""
    }

    private fun isIgnored(targetName: String): Boolean =
        configMappings.any { targetName in it.ignoredFields }

    private fun typesMatch(source: PropertyInfo, target: PropertyInfo): Boolean =
        source.type.ksType == target.type.ksType
}
