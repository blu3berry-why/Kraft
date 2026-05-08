package com.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.KSPLogger
import com.blu3berry.kraft.processor.sides.SideRegistry

/** Provides the KSP logger, processor options, [GenerationConfig], and [SideRegistry] to generator implementations. */
data class GeneratorEnvironment(
    val logger: KSPLogger,
    val options: Map<String, String>,
    val config: GenerationConfig,
    val sideRegistry: SideRegistry,
)

/** ServiceLoader entry point for custom [MapperGenerator] implementations. */
fun interface MapperGeneratorProvider {
    fun create(environment: GeneratorEnvironment): MapperGenerator
}
