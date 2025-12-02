package hu.nova.blu3berry.kraft.processor.codegen

import hu.nova.blu3berry.kraft.model.MapperDescriptor

data class GenerationConfig(
    val functionNameTemplate: String = "to\${target}"
) {

    fun functionNameFor(descriptor: MapperDescriptor): String {
        val sourceSimple = descriptor.fromType.className.simpleName
        val targetSimple = descriptor.toType.className.simpleName

        return functionNameTemplate
            .replace("\${source}", sourceSimple)
            .replace("\${target}", targetSimple)
    }
}