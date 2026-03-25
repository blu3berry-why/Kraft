package hu.nova.blu3berry.kraft.model.scan

/**
 * Captures the `@MapNested` annotation state for a single property during scanning.
 *
 * - [NotAnnotated]: the property has no `@MapNested` annotation.
 * - [SameName]: `@MapNested` is present with no source-name override.
 * - [Renamed]: `@MapNested(sourceName = "…")` is present.
 */
sealed interface MapNestedAnnotation {
    data object NotAnnotated : MapNestedAnnotation
    data object SameName : MapNestedAnnotation
    data class Renamed(val sourceName: String) : MapNestedAnnotation
}