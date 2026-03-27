package hu.nova.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.KSPLogger

/** Provides the KSP logger, processor options, and [GenerationConfig] to generator implementations. */
data class GeneratorEnvironment(
    val logger: KSPLogger,
    val options: Map<String, String>,
    val config: GenerationConfig
)

/** ServiceLoader entry point for custom [MapperGenerator] implementations. */
fun interface MapperGeneratorProvider {
    fun create(environment: GeneratorEnvironment): MapperGenerator
}
