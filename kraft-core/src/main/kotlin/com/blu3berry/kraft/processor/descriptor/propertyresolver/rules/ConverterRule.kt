package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.descriptor.ConverterSource
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.report

/** [MappingRule] that resolves properties targeted by `@MapUsing` converter functions. */
class ConverterRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        val matching = ctx.converters.filter { conv -> conv.targetPropertyName == target.name }
        if (matching.size > 1) {
            ctx.logger.report(
                KraftDiagnostic.DUPLICATE_MAP_USING_TARGET,
                "Multiple @MapUsing converters target property '${target.name}' — only one is allowed. " +
                "Found: ${matching.map { it.functionName }}",
                target.declaration
            )
            return null
        }
        val converter = matching.firstOrNull() ?: return null

        val converterSource: ConverterSource = if (converter.sourcePropertyName == null) {
            ConverterSource.WholeObject(converter.sourceType)
        } else {
            val name = converter.sourcePropertyName
            val sourceProp = ctx.sourceProps[name] ?: run {
                ctx.logger.report(
                    KraftDiagnostic.MAP_USING_UNKNOWN_PROPERTY,
                    "Unknown source property '$name' in @MapUsing. " +
                    "Available: ${ctx.sourceProps.keys}",
                    converter.function ?: target.declaration
                )
                return null
            }
            ConverterSource.Property(sourceProp)
        }

        return PropertyMappingStrategy.ConverterFunction(
            targetProperty = target,
            source = converterSource,
            converter = converter
        )
    }
}
