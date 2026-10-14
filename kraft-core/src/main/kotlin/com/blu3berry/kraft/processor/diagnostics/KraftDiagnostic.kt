package com.blu3berry.kraft.processor.diagnostics

/**
 * The catalogue of every diagnostic Kraft's processor can emit.
 *
 * Each entry has a stable [code] (`KRAFT-E###` for errors, `KRAFT-W###` for warnings)
 * that users grep for, suppress with `@Suppress("<code>")`, and look up on the errors
 * reference page (`docs/user-guide/errors.md`, anchor [anchor]). A jvmTest fails when an
 * entry has no heading on that page, or the page has a heading with no entry here.
 *
 * Append only; never renumber; retired codes stay as @Deprecated entries.
 * A released code keeps meaning the same failure condition forever.
 */
enum class KraftDiagnostic(
    val code: String,
    val severity: Severity,
    val title: String,
) {
    /** A property-source `@MapUsing` converter duplicates a mapping Kraft would generate natively. */
    REDUNDANT_MAP_USING("KRAFT-W001", Severity.WARNING, "Redundant @MapUsing"),

    /** A same-module `S? -> T?` `@KraftConverter` duplicates the safe call Kraft derives from `S -> T`. */
    NULLABLE_TWIN_CONVERTER("KRAFT-W002", Severity.WARNING, "Nullable-twin @KraftConverter"),
    ;

    /** The heading anchor on the errors reference page, e.g. `kraft-w001`. */
    val anchor: String get() = code.lowercase()

    /** The full link to this diagnostic's entry on the errors reference page. */
    val docsUrl: String get() = "$ERRORS_PAGE_URL#$anchor"

    companion object {
        /** Published location of `docs/user-guide/errors.md`. */
        const val ERRORS_PAGE_URL = "https://blu3berry-why.github.io/Kraft/user-guide/errors/"
    }
}
