package hu.nova.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import hu.nova.blu3berry.kraft.model.descriptor.MapperDescriptor

fun interface MapperGenerator {
    fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator)
}
