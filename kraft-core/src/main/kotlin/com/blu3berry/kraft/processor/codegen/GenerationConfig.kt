package com.blu3berry.kraft.processor.codegen

import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor

/**
 * Configuration for naming generated mapper functions, supporting `${source}` and `${target}` template placeholders.
 *
 * @param functionNameTemplate template string where `${source}` and `${target}` are replaced with the respective type simple names.
 */
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
