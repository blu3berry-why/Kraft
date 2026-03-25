package hu.nova.blu3berry.kraft.model.config
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