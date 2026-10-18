package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.blu3berry.kraft.model.scan.MapNestedAnnotation
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.propertyresolver.LeafOperationResolver
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.RuleOutcome
import com.blu3berry.kraft.processor.util.ambiguousNestedDescriptors
import com.blu3berry.kraft.processor.util.ambiguousNestedSourceProperty
import com.blu3berry.kraft.processor.util.isMappableClass
import com.blu3berry.kraft.processor.util.isMappedContainerPair
import com.blu3berry.kraft.processor.util.nestedMappingSourceNotFound
import com.blu3berry.kraft.processor.util.nestedTypeNotMappable
import com.blu3berry.kraft.processor.util.nullableNestedSource
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.report

/**
 * Maps a single-object property through a nested mapper: `@MapNested`, an explicit
 * `@NestedMapping`, or an auto-detected pair of mappable classes. Container properties
 * (`List<A>` → `List<B>`) are handled by [ContainerRule], which runs first.
 */
class NestedRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? = (evaluate(target, ctx) as? RuleOutcome.Resolved)?.strategy

    /** [RuleOutcome.Reported] once a non-mappable `@MapNested` type (KRAFT-E015) is reported, so no KRAFT-E005 follows. */
    override fun evaluate(target: PropertyInfo, ctx: MappingContext): RuleOutcome {
        // 1. @MapNested path (highest priority)
        val mapNested = ctx.classNestedOverrides[target.name]
        if (mapNested != null) {
            return resolveMapNested(target, ctx, mapNested)
        }

        // 2. Explicit nestedMappings path (from @MapConfig)
        val targetCandidates = ctx.nestedMappings.filter { nm ->
            nm.targetType.qualifiedName == target.type.qualifiedName
        }
        if (targetCandidates.isNotEmpty()) {
            return resolveExplicitNested(target, ctx, targetCandidates).asOutcome()
        }

        // 3. Auto-detection fallback
        return resolveAutoDetected(target, ctx).asOutcome()
    }

    private fun PropertyMappingStrategy?.asOutcome(): RuleOutcome =
        this?.let { RuleOutcome.Resolved(it) } ?: RuleOutcome.Pass

    // ---------- 1. @MapNested path ----------

    @Suppress("ReturnCount")
    private fun resolveMapNested(
        target: PropertyInfo,
        ctx: MappingContext,
        mapNested: MapNestedAnnotation
    ): RuleOutcome {
        val sourceName = mapNested.sourceNameFor(target)
        val sourceProp = ctx.sourceProps[sourceName]
        // ContainerRule has already claimed (or reported) a same-kind container pair.
        if (sourceProp != null && isMappedContainerPair(sourceProp.type, target.type)) return RuleOutcome.Pass

        reportMapNestedWarnings(target, ctx, mapNested)
        if (sourceProp == null) {
            ctx.logger.report(
                KraftDiagnostic.MAP_NESTED_SOURCE_NOT_FOUND,
                "@MapNested on '${target.name}': source property " +
                    "'$sourceName' does not exist in " +
                    "${ctx.sourceTypeName}. Available: " +
                    "${ctx.sourceProps.keys.sorted()}",
                target.declaration
            )
            return RuleOutcome.Pass
        }

        val nonMappableType = listOf(sourceProp.type, target.type)
            .firstOrNull { !isMappableClass(it) }
        if (nonMappableType != null) {
            ctx.logger.nestedTypeNotMappable(
                propertyName = target.name,
                typeName = nonMappableType.simpleName,
                symbol = target.declaration
            )
            return RuleOutcome.Reported
        }

        if (!checkNullabilityForSingleObject(sourceProp, target, ctx)) {
            return RuleOutcome.Pass
        }

        return RuleOutcome.Resolved(
            PropertyMappingStrategy.NestedMapper(
                targetProperty = target,
                sourceProperty = sourceProp,
                nestedMappingDescriptor = LeafOperationResolver.nestedDescriptor(
                    sourceProp.type, target.type
                )
            )
        )
    }

    // ---------- 2. Explicit nestedMappings path ----------

    private fun resolveExplicitNested(
        target: PropertyInfo,
        ctx: MappingContext,
        targetCandidates: List<NestedMappingDescriptor>
    ): PropertyMappingStrategy? {
        val explicitSourceName = ctx.configRenames[target.name]

        if (explicitSourceName != null) {
            return resolveExplicitWithOverride(
                target, ctx, targetCandidates, explicitSourceName
            )
        }

        return resolveExplicitWithoutOverride(target, ctx, targetCandidates)
    }

    private fun resolveExplicitWithOverride(
        target: PropertyInfo,
        ctx: MappingContext,
        targetCandidates: List<NestedMappingDescriptor>,
        explicitSourceName: String
    ): PropertyMappingStrategy? {
        val overrideProp = ctx.sourceProps[explicitSourceName]
            ?: run {
                ctx.logger.nestedMappingSourceNotFound(
                    sourceTypeName = ctx.sourceTypeName,
                    nestedSourceType = targetCandidates.first().sourceType.simpleName,
                    nestedTargetType = target.type.simpleName,
                    symbol = target.declaration
                )
                return null
            }
        val nestedCandidates = targetCandidates.filter { nm ->
            nm.sourceType.qualifiedName == overrideProp.type.qualifiedName
        }

        return when {
            nestedCandidates.isEmpty() -> {
                ctx.logger.nestedMappingSourceNotFound(
                    sourceTypeName = ctx.sourceTypeName,
                    nestedSourceType = overrideProp.type.simpleName,
                    nestedTargetType = target.type.simpleName,
                    symbol = target.declaration
                )
                null
            }
            nestedCandidates.size > 1 -> {
                ctx.logger.ambiguousNestedDescriptors(
                    targetTypeName = target.type.simpleName,
                    matchCount = nestedCandidates.size,
                    symbol = target.declaration
                )
                null
            }
            else -> {
                if (!checkNullabilityForSingleObject(overrideProp, target, ctx)) return null
                PropertyMappingStrategy.NestedMapper(
                    targetProperty = target,
                    sourceProperty = overrideProp,
                    nestedMappingDescriptor = nestedCandidates.single()
                )
            }
        }
    }

    private fun resolveExplicitWithoutOverride(
        target: PropertyInfo,
        ctx: MappingContext,
        targetCandidates: List<NestedMappingDescriptor>
    ): PropertyMappingStrategy? {
        if (targetCandidates.size > 1) {
            ctx.logger.ambiguousNestedDescriptors(
                targetTypeName = target.type.simpleName,
                matchCount = targetCandidates.size,
                symbol = target.declaration
            )
            return null
        }
        val nested = targetCandidates.single()

        val sourcePropCandidates = ctx.sourceProps.values.filter { prop ->
            prop.type.qualifiedName == nested.sourceType.qualifiedName
        }

        return when (sourcePropCandidates.size) {
            0 -> {
                ctx.logger.nestedMappingSourceNotFound(
                    sourceTypeName = ctx.sourceTypeName,
                    nestedSourceType = nested.sourceType
                        .simpleName,
                    nestedTargetType = nested.targetType
                        .simpleName,
                    symbol = target.declaration
                )
                null
            }
            1 -> {
                val sourceProp = sourcePropCandidates.single()
                if (!checkNullabilityForSingleObject(
                        sourceProp, target, ctx
                    )
                ) return null
                PropertyMappingStrategy.NestedMapper(
                    targetProperty = target,
                    sourceProperty = sourceProp,
                    nestedMappingDescriptor = nested
                )
            }
            else -> {
                ctx.logger.ambiguousNestedSourceProperty(
                    sourceTypeName = ctx.sourceTypeName,
                    nestedSourceType = nested.sourceType
                        .simpleName,
                    matchingProps = sourcePropCandidates
                        .map { it.name }.sorted(),
                    symbol = target.declaration
                )
                null
            }
        }
    }

    // ---------- 3. Auto-detection fallback ----------

    private fun resolveAutoDetected(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        // Check class-level and config-level renames before falling back to target.name
        val sourceName = ctx.classRenames[target.name]
            ?: ctx.configRenames[target.name]
            ?: target.name
        val sourceProp = ctx.sourceProps[sourceName] ?: return null

        if (sourceProp.type.qualifiedName == target.type.qualifiedName) return null
        val descriptor = LeafOperationResolver.autoNested(sourceProp.type, target.type) ?: return null
        if (!checkNullabilityForSingleObject(sourceProp, target, ctx)) {
            return null
        }

        return PropertyMappingStrategy.NestedMapper(
            targetProperty = target,
            sourceProperty = sourceProp,
            nestedMappingDescriptor = descriptor
        )
    }

    // ---------- Shared helpers ----------

    private fun checkNullabilityForSingleObject(
        sourceProp: PropertyInfo,
        target: PropertyInfo,
        ctx: MappingContext
    ): Boolean {
        if (!sourceProp.type.isNullable || target.type.isNullable) return true
        ctx.logger.nullableNestedSource(
            propertyName = target.name,
            sourceTypeName = ctx.sourceTypeName,
            targetTypeName = ctx.targetTypeName,
            symbol = target.declaration
        )
        return false
    }

}
