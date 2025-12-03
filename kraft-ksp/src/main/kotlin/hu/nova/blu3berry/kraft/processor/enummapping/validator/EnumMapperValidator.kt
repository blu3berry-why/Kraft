package hu.nova.blu3berry.kraft.processor.enummapping.validator

import com.google.devtools.ksp.processing.KSPLogger

/**
 * Validates the enum mapping configuration.
 *
 * This function checks that:
 * 1. All custom mapping keys exist in the source enum
 * 2. All custom mapping values exist in the target enum
 * 3. All source enum entries have a mapping to the target enum
 * 4. No duplicate mappings exist for the same source entry
 * 5. The mapping is not empty
 *
 * @param fromEntries List of source enum entry names
 * @param toEntries List of target enum entry names
 * @param customMapping Map of custom mappings from source to target enum entries
 * @param logger KSP logger for reporting errors
 * @return True if the mapping is valid, false otherwise
 */
fun validateEnumMapping(
    fromEntries: List<String>,
    toEntries: List<String>,
    customMapping: Map<String, String>,
    logger: KSPLogger
): Boolean {
    var valid = true

    // Handle empty enums
    if (fromEntries.isEmpty()) {
        logger.error("Source enum cannot be empty")
        return false
    }

    if (toEntries.isEmpty()) {
        logger.error("Target enum cannot be empty")
        return false
    }

    // 1. Check customMapping keys
    customMapping.keys.forEach { key ->
        if (key !in fromEntries) {
            logger.error("Custom mapping error: Source enum entry '$key' does not exist in the source enum. Please check the spelling.")
            valid = false
        }
    }

    // 2. Check customMapping values
    customMapping.values.forEach { value ->
        if (value !in toEntries) {
            // Find similar entries to suggest
            val similarEntries = toEntries.filter { it.contains(value, ignoreCase = true) || value.contains(it, ignoreCase = true) }
            val suggestion = if (similarEntries.isNotEmpty()) {
                ". Did you mean one of these: ${similarEntries.joinToString(", ")}"
            } else {
                ""
            }

            logger.error("Custom mapping error: Target enum entry '$value' does not exist in the target enum$suggestion")
            valid = false
        }
    }

    // 3. Check unmapped differing entries
    fromEntries.forEach { entry ->
        if (entry !in toEntries && entry !in customMapping.keys) {
            logger.error("Mapping error: Source enum entry '$entry' has no mapping to the target enum. " +
                    "Either add a custom mapping or ensure the target enum has a matching entry.")
            valid = false
        }
    }

    // 4. Check for duplicate mappings (should be handled by Map, but just in case)
    val duplicateKeys = customMapping.keys.groupBy { it }.filter { it.value.size > 1 }.keys
    if (duplicateKeys.isNotEmpty()) {
        duplicateKeys.forEach { key ->
            logger.error("Duplicate mapping error: Source enum entry '$key' is mapped multiple times. Each source entry must map to exactly one target entry.")
            valid = false
        }
    }

    return valid
}
