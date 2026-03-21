package hu.nova.blu3berry.kraft.config

/**
 * Controls which mapping direction(s) an [IgnoreField] applies to.
 *
 * When the config object generates only a single direction today, [REVERSE] entries
 * are stored but not applied; they will activate automatically once reverse-mapping
 * generation is introduced.
 */
enum class IgnoreDirection {
    /** Apply only when mapping `from → to` (forward direction). */
    FORWARD,

    /** Apply only when mapping `to → from` (reverse direction, reserved for future use). */
    REVERSE,

    /**
     * Apply in both directions.
     *
     * If the property name exists in only one direction's target the processor
     * will auto-infer and apply it there; if it exists in both, it is applied in both.
     */
    BOTH
}
