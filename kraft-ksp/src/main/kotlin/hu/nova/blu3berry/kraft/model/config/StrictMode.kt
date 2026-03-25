package hu.nova.blu3berry.kraft.model.config

/**
 * Controls how strictly the mapper enforces full coverage
 * of target and source properties.
 */
enum class StrictMode {

    /**
     * Every target property must be mapped.
     * Every source property must either map or be ignored.
     *
     * Missing properties cause a compile-time error.
     */
    STRICT,

    /**
     * Unmapped source or target properties are allowed.
     *
     * Useful for large DTOs or evolving APIs.
     */
    LOOSE,

    /**
     * Missing mappings do not fail the build,
     * but produce compile-time warnings.
     */
    WARN
}