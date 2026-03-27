package hu.nova.blu3berry.kraft.processor.codegen

/** ServiceLoader entry point for custom [EnumMapperGeneratorSpi] implementations. */
fun interface EnumMapperGeneratorProvider {
    fun create(environment: GeneratorEnvironment): EnumMapperGeneratorSpi
}
