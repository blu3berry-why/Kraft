package com.blu3berry.kraft.config

/**
 * Controls which mapping direction a [@MapUsing][MapUsing] converter applies to.
 *
 * When a config object is annotated with [@MapReverse][MapReverse] and both source
 * and target classes share a property name with different types (e.g. `id: Uuid?` vs
 * `id: String?`), [AUTO] may not be able to disambiguate. Use [FORWARD] or [REVERSE]
 * to specify the intended direction explicitly.
 */
enum class ConverterDirection {
    /**
     * Auto-detect direction by matching the converter's parameter type against
     * the source property types of each direction. This is the default and works
     * when the property types are different between source and target.
     */
    AUTO,

    /** Apply only when mapping `source → target` (forward direction). */
    FORWARD,

    /** Apply only when mapping `target → source` (reverse direction). */
    REVERSE
}
