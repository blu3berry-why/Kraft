package hu.nova.blu3berry.kraft.processor.enummapping

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment

/**
 * Provider for creating instances of EnumMapperProcessor.
 *
 * This class is registered in the META-INF/services directory to be discovered
 * by the KSP framework.
 */
class EnumMapperProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return EnumMapperProcessor(environment.codeGenerator, environment.logger)
    }
}