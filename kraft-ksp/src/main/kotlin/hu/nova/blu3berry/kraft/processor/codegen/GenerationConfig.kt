package hu.nova.blu3berry.kraft.processor.codegen

import com.squareup.kotlinpoet.ClassName
import hu.nova.blu3berry.kraft.model.MapperDescriptor
import hu.nova.blu3berry.kraft.model.MappingSource
import hu.nova.blu3berry.kraft.model.NestedMappingDescriptor

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

    fun functionNameFor(from: ClassName, to: ClassName): String {
        return functionNameTemplate
            .replace("\${source}", from.simpleName)
            .replace("\${target}", to.simpleName)
    }
}

fun GenerationConfig.functionNameForNested(nested: NestedMappingDescriptor): String {
    return functionNameFor(
        nested.sourceType.className,
        nested.targetType.className
    )
}
