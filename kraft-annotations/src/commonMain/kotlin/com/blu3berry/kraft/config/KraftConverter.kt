package com.blu3berry.kraft.config

/**
 * Marks a top-level extension function as a globally discoverable converter.
 *
 * When a generated mapper encounters two properties whose types differ but whose
 * (sourceType → targetType) pair matches a `@KraftConverter` extension function in
 * the same module, the converter is invoked automatically — no per-`@MapConfig`
 * `@MapUsing` declaration is required.
 *
 * **Discovery rules**
 * - Annotated symbol must be a top-level extension function (the receiver is the
 *   source type; no value parameters).
 * - Source type is taken from the receiver, target type from the return type.
 * - Lookup matches the source/target qualified names *and* nullability exactly.
 *
 * **Resolution order**
 * 1. Per-property `@MapUsing` on a `@MapConfig` object — wins.
 * 2. Same-module `@KraftConverter` — second.
 * 3. Built-in primitives / no converter → existing type-mismatch error.
 *
 * Two `@KraftConverter` functions registering the same `(source → target)` pair
 * within one module produce a KSP error listing both candidates.
 *
 * Example:
 * ```
 * @OptIn(ExperimentalUuidApi::class)
 * @KraftConverter
 * fun Uuid.toStringValue(): String = toString()
 *
 * @OptIn(ExperimentalUuidApi::class)
 * @KraftConverter
 * fun String.toUuidValue(): Uuid = Uuid.parse(this)
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KraftConverter
