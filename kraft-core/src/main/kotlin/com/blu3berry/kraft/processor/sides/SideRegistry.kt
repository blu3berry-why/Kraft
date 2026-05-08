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

    /**
     * Returns the side that names this target FQN, or null if no registered
     * side matches. Throws [IllegalStateException] (framed as a gradle config
     * error) if multiple sides match — Phase 2 of overlap detection from the
     * spec, used for cases the eager subset analysis can't prove.
     */
    fun resolveSide(targetFqn: String): SideConfig? {
        val matches = sides.filter { it.packagePattern.matches(targetFqn) }
        return when (matches.size) {
            0 -> null
            1 -> matches.single()
            else -> {
                val lines = matches.joinToString("\n") {
                    "  - kraft.side.${it.slot}.packagePattern  = \"${it.packagePattern.raw}\""
                }
                error(
                    """
                    Kraft side configuration error: package patterns overlap.

                    Class $targetFqn matches ${matches.size} sides:
                    $lines

                    Patterns must be disjoint. Tighten one of the patterns in
                    build.gradle.kts so the classes you intend each side to
                    match no longer overlap.
                    """.trimIndent()
                )
            }
        }
    }

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

            validateNoOverlap(sides)
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

        private fun validateNoOverlap(sides: List<SideConfig>) {
            for (i in sides.indices) for (j in i + 1 until sides.size) {
                val a = sides[i]
                val b = sides[j]
                if (a.packagePattern.raw == b.packagePattern.raw) {
                    error(
                        "Kraft side configuration error in build.gradle.kts: " +
                            "sides `kraft.side.${a.slot}` and `kraft.side.${b.slot}` " +
                            "have identical packagePattern '${a.packagePattern.raw}'. " +
                            "Patterns must be disjoint."
                    )
                }
                if (a.packagePattern.isStrictSubsetOf(b.packagePattern)) {
                    error(
                        "Kraft side configuration error in build.gradle.kts: " +
                            "`kraft.side.${a.slot}.packagePattern` ('${a.packagePattern.raw}') is a strict subset " +
                            "of `kraft.side.${b.slot}.packagePattern` ('${b.packagePattern.raw}'). " +
                            "Every class matched by the first would also match the second. " +
                            "Patterns must be disjoint — tighten one of the two."
                    )
                }
                if (b.packagePattern.isStrictSubsetOf(a.packagePattern)) {
                    error(
                        "Kraft side configuration error in build.gradle.kts: " +
                            "`kraft.side.${b.slot}.packagePattern` ('${b.packagePattern.raw}') is a strict subset " +
                            "of `kraft.side.${a.slot}.packagePattern` ('${a.packagePattern.raw}'). " +
                            "Every class matched by the first would also match the second. " +
                            "Patterns must be disjoint — tighten one of the two."
                    )
                }
            }
        }
    }
}
