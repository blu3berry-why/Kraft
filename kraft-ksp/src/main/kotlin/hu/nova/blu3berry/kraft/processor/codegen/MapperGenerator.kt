package hu.nova.blu3berry.kraft.processor.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import hu.nova.blu3berry.kraft.model.MapperDescriptor

interface MapperGenerator {
    fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator)
}