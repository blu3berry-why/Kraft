package com.blu3berry.kraft.config

/**
 * Controls which mapping direction a [@MapUsing][MapUsing] converter applies to.
 *
 * Used together with [@MapReverse][MapReverse] when one config object declares both
 * forward and reverse converters for a property whose name appears on both sides.
 * [AUTO] correctly disambiguates when the source and target property types differ
 * (e.g. `id: Uuid` vs `id: String`); use [FORWARD] or [REVERSE] when the property
 * types are identical on both sides — or otherwise indistinguishable from the
 * converter parameter type alone — so AUTO has no signal to disambiguate.
 */
enum class ConverterDirection {
    /**
     * Auto-detect direction by matching the converter's parameter type against the
     * source property type of each direction. The default; works whenever the source
     * and target property types differ.
     */
    AUTO,

    /** Apply only when mapping `source → target` (forward direction). */
    FORWARD,

    /** Apply only when mapping `target → source` (reverse direction). */
    REVERSE
}
