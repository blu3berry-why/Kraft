package hu.nova.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.KSPLogger

data class GeneratorEnvironment(
    val logger: KSPLogger,
    val options: Map<String, String>,
    val config: GenerationConfig
)

fun interface MapperGeneratorProvider {
    fun create(environment: GeneratorEnvironment): MapperGenerator
}
