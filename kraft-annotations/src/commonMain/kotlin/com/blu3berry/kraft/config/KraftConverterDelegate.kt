package com.blu3berry.kraft.config

/**
 * Internal marker for generated cross-module converter delegates.
 *
 * For every `@KraftConverter`-annotated function the user writes, the Kraft KSP
 * processor emits a `public` thin extension wrapper in package
 * `kraft.generated.registry`. The wrapper is annotated with `@KraftConverterDelegate`
 * so the consuming compilation can discover it via
 * `Resolver.getDeclarationsFromPackage("kraft.generated.registry")` and re-build the
 * (sourceType → targetType) registry without having to scan the entire classpath.
 *
 * Users should not apply this annotation manually — declare your converters with
 * `@KraftConverter` and let the processor generate the delegates.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class KraftConverterDelegate
