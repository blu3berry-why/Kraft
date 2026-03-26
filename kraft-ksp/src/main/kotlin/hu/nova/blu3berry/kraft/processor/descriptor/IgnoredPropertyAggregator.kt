package hu.nova.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import hu.nova.blu3berry.kraft.config.IgnoreSide
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.scan.ClassMappingScanResult
import hu.nova.blu3berry.kraft.model.scan.ConfigObjectScanResult
import hu.nova.blu3berry.kraft.model.scan.IgnoredMappingConfig

/**
 * Aggregates the full set of target property names that should be ignored during mapping.
 *
 * Merges two sources:
 * - Class-level `@MapIgnore` annotations on the annotated class's properties.
 * - Config-level `@MapIgnoreField` entries from any matching `@MapConfig` objects
 *   (TARGET and BOTH sides only; SOURCE entries are reserved for future reverse-mapping).
 */
internal class IgnoredPropertyAggregator(private val logger: KSPLogger) {

    fun aggregate(
        mapping: ClassMappingScanResult,
        configObjects: List<ConfigObjectScanResult>,
        targetProps: List<PropertyInfo>,
        targetTypeName: String
    ): Set<String> =
        extractClassIgnored(mapping) + buildConfigIgnored(configObjects, targetProps, targetTypeName)

    private fun extractClassIgnored(mapping: ClassMappingScanResult): Set<String> =
        mapping.propertyScanResults
            .filter { it.isIgnored }
            .map { it.property.simpleName.asString() }
            .toSet()

    private fun buildConfigIgnored(
        configObjects: List<ConfigObjectScanResult>,
        targetProps: List<PropertyInfo>,
        targetTypeName: String
    ): Set<String> {
        val targetPropNames = targetProps.map { it.name }.toSet()
        val result = mutableSetOf<String>()
        for (configObj in configObjects) {
            result += resolveConfigIgnored(
                logger, configObj.ignoredMappings, targetPropNames, targetTypeName, configObj.configObject
            )
        }
        return result
    }

    companion object {
        /**
         * Validates a list of [IgnoredMappingConfig] entries against the target constructor
         * property names and returns the set of property names to ignore.
         *
         * Only TARGET and BOTH entries are applied (forward-only generation).
         * SOURCE entries are reserved for future reverse-mapping generation.
         * BOTH entries with a name absent from the current target emit a warning and are
         * skipped — the property may legitimately exist only on the reverse target.
         */
        fun resolveConfigIgnored(
            logger: KSPLogger,
            ignoredMappings: List<IgnoredMappingConfig>,
            targetPropNames: Set<String>,
            targetTypeName: String,
            errorNode: KSNode
        ): Set<String> {
            val result = mutableSetOf<String>()

            for (ignored in ignoredMappings) {
                when (ignored.direction) {
                    IgnoreSide.SOURCE -> continue
                    IgnoreSide.TARGET -> {
                        if (ignored.name !in targetPropNames) {
                            logger.error(
                                "@MapIgnoreField(\"${ignored.name}\", TARGET): property not found " +
                                    "in target '$targetTypeName' constructor. " +
                                    "Available: ${targetPropNames.sorted()}",
                                errorNode
                            )
                        } else {
                            result.add(ignored.name)
                        }
                    }
                    IgnoreSide.BOTH -> {
                        if (ignored.name in targetPropNames) {
                            result.add(ignored.name)
                        } else {
                            logger.warn(
                                "@MapIgnoreField(\"${ignored.name}\", BOTH): property not found in " +
                                    "target '$targetTypeName' constructor; skipped for forward direction. " +
                                    "Available: ${targetPropNames.sorted()}",
                                errorNode
                            )
                        }
                    }
                }
            }

            return result
        }
    }
}
