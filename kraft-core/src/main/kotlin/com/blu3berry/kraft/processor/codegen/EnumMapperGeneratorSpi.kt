package com.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.blu3berry.kraft.ExperimentalKraftApi
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor

/**
 * SPI for generating enum-to-enum mapper functions from [EnumMappingDescriptor]
 * lists, plus the naming convention used to address the generated functions.
 *
 * Implementations must keep [generatedPackage] / [generatedFunctionName] in sync
 * with the actual output of [generate]: the synthetic-converter registry built
 * by `enumMappingsToConverterEntries` and the `@KraftConverterDelegate`
 * trampolines emitted by `DelegateRegistryGenerator` use these methods to
 * compute the call coordinates, so a divergence makes the trampolines call
 * non-existent functions.
 */
@ExperimentalKraftApi
interface EnumMapperGeneratorSpi {
    fun generate(descriptors: List<EnumMappingDescriptor>, codeGenerator: CodeGenerator)

    /** Package the generated enum-mapper extension lands in for [descriptor]. */
    fun generatedPackage(descriptor: EnumMappingDescriptor): String

    /** Simple name of the generated enum-mapper extension for [descriptor]. */
    fun generatedFunctionName(descriptor: EnumMappingDescriptor): String
}
