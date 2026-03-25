package hu.nova.blu3berry.kraft.model.config

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
