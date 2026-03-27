package hu.nova.blu3berry.kraft.processor.codegen

import hu.nova.blu3berry.kraft.model.descriptor.MapperDescriptor
import hu.nova.blu3berry.kraft.model.descriptor.NestedMappingDescriptor

data class GenerationConfig(
    val functionNameTemplate: String = "to\${target}"
) {

    fun functionNameFor(descriptor: MapperDescriptor): String {
        return functionNameTemplate
            .replace("\${source}", descriptor.sourceType.simpleName)
            .replace("\${target}", descriptor.targetType.simpleName)
    }

    fun functionNameFor(sourceSimple: String, targetSimple: String): String {
        return functionNameTemplate
            .replace("\${source}", sourceSimple)
            .replace("\${target}", targetSimple)
    }
}

fun GenerationConfig.functionNameForNested(nested: NestedMappingDescriptor): String {
    return functionNameFor(
        nested.sourceType.simpleName,
        nested.targetType.simpleName
    )
}
