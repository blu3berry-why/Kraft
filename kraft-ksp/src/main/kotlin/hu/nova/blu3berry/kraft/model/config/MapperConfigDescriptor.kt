package hu.nova.blu3berry.kraft.model.config

/**
 * Defines global mapper configuration options.
 *
 * **Not yet wired into the processor.** This class and its supporting enums
 * ([StrictMode], [NullHandlingStrategy], [NamingStrategy]) are reserved for a
 * future global-config feature and are currently never instantiated.
 */
data class MapperConfigDescriptor(
    val strictMode: StrictMode = StrictMode.STRICT,
    val nullHandling: NullHandlingStrategy = NullHandlingStrategy.SOURCE_NULL_PASSTHROUGH,
    val namingStrategy: NamingStrategy = NamingStrategy.IDENTITY,
    val allowUnmappedSourceProps: Boolean = false,
    val allowUnmappedTargetProps: Boolean = false
)




