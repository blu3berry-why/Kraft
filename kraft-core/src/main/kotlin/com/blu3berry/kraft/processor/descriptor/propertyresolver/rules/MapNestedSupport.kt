package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.scan.MapNestedAnnotation
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.report

// `@MapNested` handling shared by NestedRule (scalar properties) and ContainerRule
// (container properties), so the two report the same warnings in the same order.

/** The source property name a `@MapNested` on [target] reads from. Reports nothing. */
internal fun MapNestedAnnotation.sourceNameFor(target: PropertyInfo): String = when (this) {
    is MapNestedAnnotation.SameName -> target.name
    is MapNestedAnnotation.Renamed -> sourceName
    is MapNestedAnnotation.NotAnnotated -> error(
        "NotAnnotated entry in classNestedOverrides " +
            "for '${target.name}' " +
            "— this is a bug in extractClassNestedOverrides"
    )
}

/**
 * Reports the `@MapNested` warnings for [target]: a `@MapField` on the same property that
 * `@MapNested` overrides, then a `sourceName` equal to the property name.
 */
internal fun reportMapNestedWarnings(
    target: PropertyInfo,
    ctx: MappingContext,
    mapNested: MapNestedAnnotation
) {
    if (target.name in ctx.classRenames) {
        ctx.logger.report(
            KraftDiagnostic.MAP_NESTED_OVERRIDES_MAP_FIELD,
            "@MapNested and @MapField both present on " +
                "'${target.name}' — @MapNested takes precedence.",
            target.declaration
        )
    }
    if (mapNested is MapNestedAnnotation.Renamed && mapNested.sourceName == target.name) {
        ctx.logger.report(
            KraftDiagnostic.REDUNDANT_MAP_NESTED_SOURCE_NAME,
            "@MapNested sourceName '${mapNested.sourceName}'" +
                " equals the property name" +
                " — sourceName is redundant here.",
            target.declaration
        )
    }
}
