package hu.nova.blu3berry.kraft.config

/**
 * Controls which mapping direction(s) a [MapIgnoreField] applies to.
 *
 * When the config object generates only a single direction today, [SOURCE] entries
 * are stored but not applied; they will activate automatically once reverse-mapping
 * generation is introduced.
 */
enum class IgnoreSide {
    /** Apply only when mapping `from → to` (forward direction; name is a **target**-side constructor parameter). */
    TARGET,

    /** Apply only when mapping `to → from` (reverse direction, reserved for future use; name is a **source**-side constructor parameter). */
    SOURCE,

    /**
     * Apply in both directions.
     *
     * If the property name exists in only one direction's target the processor
     * will auto-infer and apply it there; if it exists in both, it is applied in both.
     */
    BOTH
}
