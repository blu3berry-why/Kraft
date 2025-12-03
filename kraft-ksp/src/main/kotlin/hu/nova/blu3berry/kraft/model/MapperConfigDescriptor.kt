package hu.nova.blu3berry.kraft.model

data class MapperConfigDescriptor(
    val strictMode: StrictMode = StrictMode.STRICT,
    val nullHandling: NullHandlingStrategy = NullHandlingStrategy.SOURCE_NULL_PASSTHROUGH,
    val namingStrategy: NamingStrategy = NamingStrategy.IDENTITY,
    val allowUnmappedSourceProps: Boolean = false,
    val allowUnmappedTargetProps: Boolean = false
)

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

/**
 * Controls how null source values are handled when mapping.
 */
enum class NullHandlingStrategy {

    /**
     * If the source value is null, the result is also null.
     * (Default behavior in Kotlin.)
     */
    SOURCE_NULL_PASSTHROUGH,

    /**
     * If the source is null, the target default value is used
     * (constructor default or field initializer).
     */
    USE_TARGET_DEFAULT,

    /**
     * Null source values are disallowed and result in
     * a compile-time error.
     */
    FAIL
}

/**
 * Defines how source property names are interpreted
 * when matching them to target properties.
 */
enum class NamingStrategy {

    /**
     * No renaming.
     * Property names must match exactly unless overridden.
     */
    IDENTITY,

    /**
     * Converts snake_case to camelCase when searching for
     * matching target properties.
     *
     * Example:
     *  first_name -> firstName
     */
    SNAKE_TO_CAMEL,

    /**
     * Converts camelCase to snake_case when searching for
     * source properties.
     *
     * Example:
     *  firstName -> first_name
     */
    CAMEL_TO_SNAKE,

    /**
     * Example:
     *  FIRST_NAME -> firstName
     */
    UPPER_SNAKE_TO_CAMEL
}
