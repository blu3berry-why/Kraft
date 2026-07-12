package com.blu3berry.kraft.processor.codegen

import com.blu3berry.kraft.ExperimentalKraftApi

/** ServiceLoader entry point for custom [EnumMapperGeneratorSpi] implementations. */
@ExperimentalKraftApi
fun interface EnumMapperGeneratorProvider {
    fun create(environment: GeneratorEnvironment): EnumMapperGeneratorSpi
}
