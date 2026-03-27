package hu.nova.blu3berry.kraft.processor.codegen

fun interface EnumMapperGeneratorProvider {
    fun create(environment: GeneratorEnvironment): EnumMapperGeneratorSpi
}
