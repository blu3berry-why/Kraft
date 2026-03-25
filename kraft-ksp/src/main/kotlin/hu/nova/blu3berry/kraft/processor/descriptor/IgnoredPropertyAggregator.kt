package hu.nova.blu3berry.kraft.processor.descriptor

import com.google.devtools.ksp.processing.KSPLogger
import hu.nova.blu3berry.kraft.config.IgnoreSide
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.scan.ClassMappingScanResult
import hu.nova.blu3berry.kraft.model.scan.ConfigObjectScanResult

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

    // Only TARGET and BOTH entries are applied here (forward-only generation).
    // SOURCE entries are stored in ConfigObjectScanResult for future use when
    // reverse-mapping generation is added.
    // BOTH with a name absent from the current target's constructor is silently
    // skipped — the property may legitimately exist only on the reverse target.
    private fun buildConfigIgnored(
        configObjects: List<ConfigObjectScanResult>,
        targetProps: List<PropertyInfo>,
        targetTypeName: String
    ): Set<String> {
        val targetPropNames = targetProps.map { it.name }.toSet()
        val result = mutableSetOf<String>()

        for (configObj in configObjects) {
            for (ignored in configObj.ignoredMappings) {
                when (ignored.direction) {
                    IgnoreSide.SOURCE -> continue
                    IgnoreSide.TARGET -> {
                        if (ignored.name !in targetPropNames) {
                            logger.error(
                                "@MapIgnoreField(\"${ignored.name}\", TARGET): property not found " +
                                    "in target '$targetTypeName' constructor. " +
                                    "Available: ${targetPropNames.sorted()}",
                                configObj.configObject
                            )
                        } else {
                            result.add(ignored.name)
                        }
                    }
                    IgnoreSide.BOTH -> {
                        if (ignored.name in targetPropNames) result.add(ignored.name)
                        // Not in this target → may be valid for the reverse direction; skip silently.
                    }
                }
            }
        }

        return result
    }
}
