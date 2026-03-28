package com.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor

/** SPI interface for generating enum-to-enum mapper functions from [EnumMappingDescriptor] lists. */
fun interface EnumMapperGeneratorSpi {
    fun generate(descriptors: List<EnumMappingDescriptor>, codeGenerator: CodeGenerator)
}
