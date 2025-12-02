package hu.nova.blu3berry.kraft.enummapping

import com.google.devtools.ksp.processing.KSPLogger

fun validateEnumMapping(
    fromEntries: List<String>,
    toEntries: List<String>,
    customMapping: Map<String,String>,
    logger: KSPLogger
): Boolean {
    var valid = true

    // 1. Check customMapping keys
    customMapping.keys.forEach { key ->
        if (key !in fromEntries) {
            logger.error("Custom mapping key '$key' not found in source enum")
            valid = false
        }
    }

    // 2. Check customMapping values
    customMapping.values.forEach { value ->
        if (value !in toEntries) {
            logger.error("Custom mapping value '$value' not found in target enum")
            valid = false
        }
    }

    // 3. Check unmapped differing entries
    fromEntries.forEach { entry ->
        if (entry !in toEntries && entry !in customMapping.keys) {
            logger.error("Enum entry '$entry' has no mapping to target enum")
            valid = false
        }
    }

    return valid
}
