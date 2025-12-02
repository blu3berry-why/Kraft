package hu.nova.blu3berry.kraft
import com.google.devtools.ksp.processing.*

class AutoMapperProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return AutoMapperProcessor(environment)
    }
}