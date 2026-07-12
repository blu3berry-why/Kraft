package com.blu3berry.kraft

/**
 * Marks the Kraft plugin SPI as experimental.
 *
 * Types carrying this annotation are excluded from the semantic-versioning
 * compatibility promise: they may change or be removed in any release
 * without a major version bump. Opt in only if you accept keeping your
 * code up to date with SPI changes.
 */
@RequiresOptIn(
    message = "This Kraft plugin SPI is experimental and may change in any release " +
        "without a major version bump.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class ExperimentalKraftApi
