package com.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import com.blu3berry.kraft.config.IgnoreSide
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.scan.ClassMappingScanResult
import com.blu3berry.kraft.model.scan.ConfigObjectScanResult
import com.blu3berry.kraft.model.scan.IgnoredMappingConfig

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
         * In forward mode (`reverse = false`): TARGET and BOTH entries are applied; SOURCE is skipped.
         * In reverse mode (`reverse = true`): SOURCE and BOTH entries are applied; TARGET is skipped.
         * BOTH entries with a name absent from the current target emit a warning and are skipped.
         */
        fun resolveConfigIgnored(
            logger: KSPLogger,
            ignoredMappings: List<IgnoredMappingConfig>,
            targetPropNames: Set<String>,
            targetTypeName: String,
            errorNode: KSNode,
            reverse: Boolean = false
        ): Set<String> {
            val result = mutableSetOf<String>()
            val directionLabel = if (reverse) "reverse" else "forward"

            for (ignored in ignoredMappings) {
                val isActive = when (ignored.direction) {
                    IgnoreSide.TARGET -> !reverse
                    IgnoreSide.SOURCE -> reverse
                    IgnoreSide.BOTH -> true
                }

                if (!isActive) continue

                if (ignored.name in targetPropNames) {
                    result.add(ignored.name)
                } else if (ignored.direction == IgnoreSide.BOTH) {
                    logger.warn(
                        "@MapIgnoreField(\"${ignored.name}\", BOTH): property not found in " +
                            "target '$targetTypeName' constructor; skipped for $directionLabel direction. " +
                            "Available: ${targetPropNames.sorted()}",
                        errorNode
                    )
                } else {
                    logger.error(
                        "@MapIgnoreField(\"${ignored.name}\", ${ignored.direction.name}): property not found " +
                            "in target '$targetTypeName' constructor. " +
                            "Available: ${targetPropNames.sorted()}",
                        errorNode
                    )
                }
            }

            return result
        }
    }
}
