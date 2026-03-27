package hu.nova.blu3berry.kraft
import com.google.devtools.ksp.processing.*

/**
 * KSP [SymbolProcessorProvider] entry point registered via `META-INF/services`.
 */
class AutoMapperProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return AutoMapperProcessor(environment)
    }
}
