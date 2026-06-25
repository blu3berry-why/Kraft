package com.blu3berry.kraft.config

/**
 * Controls which mapping direction(s) a [MapIgnoreField] applies to.
 *
 * Use [SOURCE] together with [MapReverse] to ignore a property only on the reverse
 * direction; [TARGET] applies to the forward direction; [BOTH] (the default) applies
 * to whichever direction the named property exists in.
 */
enum class IgnoreSide {
    /** Apply only when mapping `from → to` (forward direction; name is a **target**-side constructor parameter). */
    TARGET,

    /** Apply only when mapping `to → from` (reverse direction, honored by `@MapReverse`; name is a **source**-side constructor parameter). */
    SOURCE,

    /**
     * Apply in both directions.
     *
     * If the property name exists in only one direction's target the processor
     * will auto-infer and apply it there; if it exists in both, it is applied in both.
     */
    BOTH
}
