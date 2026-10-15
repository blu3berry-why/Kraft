package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.detailedTypeMismatch
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.report

/** [MappingRule] for config-level property renames declared via `@FieldMapping` in `@MapConfig`. */
class ConfigOverrideRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        val sourceName = ctx.configRenames[target.name] ?: return null
        val sourceProp = ctx.sourceProps[sourceName] ?: run {
            ctx.logger.report(
                KraftDiagnostic.CONFIG_OVERRIDE_UNKNOWN_PROPERTY,
                "Config override refers to unknown property '$sourceName'. " +
                        "Available: ${ctx.sourceProps.keys}",
                target.declaration
            )
            return null
        }

        return if (sourceProp.type.ksType == target.type.ksType) {
            PropertyMappingStrategy.Renamed(
                targetProperty = target,
                sourceProperty = sourceProp
            )
        } else {
            ctx.logger.detailedTypeMismatch(
                ctx.sourceTypeName,
                ctx.targetTypeName,
                sourceProp,
                target,
                target.declaration
            )
            null
        }
    }
}
