package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.google.devtools.ksp.symbol.ClassKind
import hu.nova.blu3berry.kraft.model.MapNestedAnnotation
import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.MapperId
import hu.nova.blu3berry.kraft.model.NestedMappingDescriptor
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.model.TypeInfo
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import hu.nova.blu3berry.kraft.processor.util.ambiguousNestedDescriptors
import hu.nova.blu3berry.kraft.processor.util.ambiguousNestedSourceProperty
import hu.nova.blu3berry.kraft.processor.util.nestedMappingSourceNotFound
import hu.nova.blu3berry.kraft.processor.util.nestedTypeNotMappable

class NestedRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        // 1. @MapNested path (highest priority)
        val mapNested = ctx.classNestedOverrides[target.name]
        if (mapNested != null) {
            if (target.name in ctx.classOverrides) {
                ctx.logger.warn(
                    "@MapNested and @MapField both present on '${target.name}' — @MapNested takes precedence.",
                    target.declaration
                )
            }

            val sourceName = when (mapNested) {
                is MapNestedAnnotation.SameName -> target.name
                is MapNestedAnnotation.Renamed -> {
                    if (mapNested.sourceName == target.name) {
                        ctx.logger.warn(
                            "@MapNested sourceName '${mapNested.sourceName}' equals the property name — sourceName is redundant here.",
                            target.declaration
                        )
                    }
                    mapNested.sourceName
                }
                is MapNestedAnnotation.NotAnnotated ->
                    error("NotAnnotated entry in classNestedOverrides for '${target.name}' — this is a bug in extractClassNestedOverrides")
            }

            val sourceProp = ctx.sourceProps[sourceName] ?: run {
                ctx.logger.error(
                    "@MapNested on '${target.name}': source property '$sourceName' does not exist in " +
                        "${ctx.sourceTypeName}. Available: ${ctx.sourceProps.keys.sorted()}",
                    target.declaration
                )
                return null
            }

            val nonMappableType = listOf(sourceProp.type, target.type).firstOrNull { !isMappableClass(it) }
            if (nonMappableType != null) {
                ctx.logger.nestedTypeNotMappable(
                    propertyName = target.name,
                    typeName = nonMappableType.className.simpleName,
                    symbol = target.declaration
                )
                return null
            }

            return PropertyMappingStrategy.NestedMapper(
                targetProperty = target,
                sourceProperty = sourceProp,
                nestedMappingDescriptor = synthesiseDescriptor(sourceProp.type, target.type)
            )
        }

        // 2. Explicit nestedMappings path (from @MapConfig)
        val nestedCandidates = ctx.nestedMappings.filter { nm ->
            nm.targetType.className == target.type.className
        }
        if (nestedCandidates.isNotEmpty()) {
            if (nestedCandidates.size > 1) {
                ctx.logger.ambiguousNestedDescriptors(
                    targetTypeName = target.type.className.simpleName,
                    matchCount = nestedCandidates.size,
                    symbol = target.declaration
                )
                return null
            }
            val nested = nestedCandidates.single()

            // Prefer an explicit source property name from configOverrides before type-matching.
            val explicitSourceName = ctx.configOverrides[target.name]
            val sourceProp = if (explicitSourceName != null) {
                ctx.sourceProps[explicitSourceName] ?: run {
                    ctx.logger.nestedMappingSourceNotFound(
                        sourceTypeName = ctx.sourceTypeName,
                        nestedSourceType = nested.sourceType.className.simpleName,
                        nestedTargetType = nested.targetType.className.simpleName,
                        symbol = target.declaration
                    )
                    return null
                }
            } else {
                val sourcePropCandidates = ctx.sourceProps.values.filter { prop ->
                    prop.type.className == nested.sourceType.className
                }
                when (sourcePropCandidates.size) {
                    0 -> {
                        ctx.logger.nestedMappingSourceNotFound(
                            sourceTypeName = ctx.sourceTypeName,
                            nestedSourceType = nested.sourceType.className.simpleName,
                            nestedTargetType = nested.targetType.className.simpleName,
                            symbol = target.declaration
                        )
                        return null
                    }
                    1 -> sourcePropCandidates.single()
                    else -> {
                        ctx.logger.ambiguousNestedSourceProperty(
                            sourceTypeName = ctx.sourceTypeName,
                            nestedSourceType = nested.sourceType.className.simpleName,
                            matchingProps = sourcePropCandidates.map { it.name }.sorted(),
                            symbol = target.declaration
                        )
                        return null
                    }
                }
            }

            return PropertyMappingStrategy.NestedMapper(
                targetProperty = target,
                sourceProperty = sourceProp,
                nestedMappingDescriptor = nested
            )
        }

        // 3. Auto-detection fallback: same name, different types, both mappable classes
        val sourceProp = ctx.sourceProps[target.name] ?: return null
        if (sourceProp.type.className == target.type.className) return null
        if (!isMappableClass(sourceProp.type) || !isMappableClass(target.type)) return null

        return PropertyMappingStrategy.NestedMapper(
            targetProperty = target,
            sourceProperty = sourceProp,
            nestedMappingDescriptor = synthesiseDescriptor(sourceProp.type, target.type)
        )
    }

    // Produces a NestedMappingDescriptor (declaration of intent only).
    // The actual child MapperDescriptor is built later by DescriptorBuilder.resolveImplicit().
    private fun synthesiseDescriptor(sourceType: TypeInfo, targetType: TypeInfo): NestedMappingDescriptor =
        NestedMappingDescriptor(
            nestedMapperId = MapperId(
                fromQualifiedName = sourceType.declaration.qualifiedName?.asString()
                    ?: sourceType.declaration.simpleName.asString(),
                toQualifiedName = targetType.declaration.qualifiedName?.asString()
                    ?: targetType.declaration.simpleName.asString()
            ),
            sourceType = sourceType,
            targetType = targetType
        )

    // Guards auto-detection: only trigger for concrete, non-stdlib classes that
    // Kraft can structurally map (has a primary constructor, not an interface/enum/object,
    // not a Kotlin or Java stdlib type).
    private fun isMappableClass(type: TypeInfo): Boolean {
        val decl = type.declaration
        val fqn = decl.qualifiedName?.asString() ?: return false
        return decl.classKind == ClassKind.CLASS
            && decl.primaryConstructor != null
            && !fqn.startsWith("kotlin.")
            && !fqn.startsWith("java.")
    }
}
