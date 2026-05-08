package com.blu3berry.kraft.processor.sides

import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.processor.util.KraftKspConstants

/**
 * Project-level alias-side configuration parsed from KSP options.
 *
 * Built once at processor init via [parseFromOptions]. Validation
 * (overlap detection) and resolution (`resolveSide`, `recordAlias`) are
 * added in subsequent tasks.
 */
class SideRegistry private constructor(
    val sides: List<SideConfig>,
) {

    companion object {

        fun parseFromOptions(options: Map<String, String>): SideRegistry {
            val prefix = KraftKspConstants.OPTION_SIDE_PREFIX
            val grouped: Map<String, MutableMap<String, String>> =
                mutableMapOf<String, MutableMap<String, String>>().apply {
                    for ((key, value) in options) {
                        if (!key.startsWith(prefix)) continue
                        val rest = key.removePrefix(prefix)
                        val dot = rest.indexOf('.')
                        if (dot < 0) continue           // not a side field; ignore
                        val slot = rest.substring(0, dot)
                        val field = rest.substring(dot + 1)
                        getOrPut(slot) { mutableMapOf() }[field] = value
                    }
                }

            val sides = grouped.entries
                .sortedBy { it.key }
                .map { (slot, fields) -> buildSide(slot, fields) }

            return SideRegistry(sides)
        }

        private fun buildSide(slot: String, fields: Map<String, String>): SideConfig {
            val name = requireNotNull(fields[KraftKspConstants.OPTION_SIDE_FIELD_NAME]) {
                "Kraft side configuration error in build.gradle.kts: " +
                    "missing required `kraft.side.$slot.${KraftKspConstants.OPTION_SIDE_FIELD_NAME}`."
            }
            val patternRaw = requireNotNull(fields[KraftKspConstants.OPTION_SIDE_FIELD_PATTERN]) {
                "Kraft side configuration error in build.gradle.kts: " +
                    "missing required `kraft.side.$slot.${KraftKspConstants.OPTION_SIDE_FIELD_PATTERN}`."
            }
            val templateRaw = fields[KraftKspConstants.OPTION_SIDE_FIELD_TEMPLATE] ?: "to{side}"
            val emitModeRaw = fields[KraftKspConstants.OPTION_SIDE_FIELD_EMIT_MODE] ?: "BOTH"

            val emitMode = try {
                AliasEmitMode.valueOf(emitModeRaw)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Kraft side configuration error in build.gradle.kts: " +
                        "`kraft.side.$slot.${KraftKspConstants.OPTION_SIDE_FIELD_EMIT_MODE}=$emitModeRaw` " +
                        "is not a valid AliasEmitMode. Allowed: BOTH, FULL_NAME_ONLY (INHERIT is per-mapper only).",
                    e
                )
            }
            require(emitMode != AliasEmitMode.INHERIT) {
                "Kraft side configuration error in build.gradle.kts: " +
                    "`kraft.side.$slot.${KraftKspConstants.OPTION_SIDE_FIELD_EMIT_MODE}=INHERIT` " +
                    "is not valid as a project default. Use BOTH or FULL_NAME_ONLY."
            }

            return SideConfig(
                slot = slot,
                name = name,
                packagePattern = PackageGlob.parse(patternRaw),
                template = AliasTemplate.parse(templateRaw),
                emitMode = emitMode,
            )
        }
    }
}
