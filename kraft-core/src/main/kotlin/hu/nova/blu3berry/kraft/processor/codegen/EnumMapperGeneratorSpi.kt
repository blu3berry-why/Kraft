package hu.nova.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import hu.nova.blu3berry.kraft.model.descriptor.EnumMappingDescriptor

fun interface EnumMapperGeneratorSpi {
    fun generate(descriptors: List<EnumMappingDescriptor>, codeGenerator: CodeGenerator)
}
