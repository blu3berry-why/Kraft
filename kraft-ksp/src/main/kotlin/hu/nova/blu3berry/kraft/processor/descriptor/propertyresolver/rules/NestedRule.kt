package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
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

            // Check for List<T> → List<T> collection case first
            if (isListType(sourceProp.type) && isListType(target.type)) {
                val srcElement = elementTypeInfo(sourceProp.type)
                val tgtElement = elementTypeInfo(target.type)
                if (srcElement == null || tgtElement == null || !isMappableClass(srcElement) || !isMappableClass(tgtElement)) {
                    ctx.logger.nestedTypeNotMappable(
                        propertyName = target.name,
                        typeName = if (srcElement == null || !isMappableClass(srcElement))
                            sourceProp.type.ksType.toString() else target.type.ksType.toString(),
                        symbol = target.declaration
                    )
                    return null
                }
                return PropertyMappingStrategy.NestedMapper(
                    targetProperty = target,
                    sourceProperty = sourceProp,
                    nestedMappingDescriptor = synthesiseDescriptor(srcElement, tgtElement, isCollection = true)
                )
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
        // Resolve any source override first so it can narrow the nested candidates and
        // verify the source type matches before ambiguity is decided.
        val explicitSourceName = ctx.configOverrides[target.name]
        val targetCandidates = ctx.nestedMappings.filter { nm ->
            nm.targetType.className == target.type.className
        }

        if (targetCandidates.isNotEmpty()) {
            if (explicitSourceName != null) {
                // Override path: resolve the source property, then filter by both target and
                // source type so the descriptor match is verified before creating the mapper.
                val overrideProp = ctx.sourceProps[explicitSourceName] ?: run {
                    ctx.logger.nestedMappingSourceNotFound(
                        sourceTypeName = ctx.sourceTypeName,
                        nestedSourceType = targetCandidates.first().sourceType.className.simpleName,
                        nestedTargetType = target.type.className.simpleName,
                        symbol = target.declaration
                    )
                    return null
                }

                val nestedCandidates = targetCandidates.filter { nm ->
                    nm.sourceType.className == overrideProp.type.className
                }
                return when {
                    nestedCandidates.isEmpty() -> {
                        ctx.logger.nestedMappingSourceNotFound(
                            sourceTypeName = ctx.sourceTypeName,
                            nestedSourceType = overrideProp.type.className.simpleName,
                            nestedTargetType = target.type.className.simpleName,
                            symbol = target.declaration
                        )
                        null
                    }
                    nestedCandidates.size > 1 -> {
                        ctx.logger.ambiguousNestedDescriptors(
                            targetTypeName = target.type.className.simpleName,
                            matchCount = nestedCandidates.size,
                            symbol = target.declaration
                        )
                        null
                    }
                    else -> PropertyMappingStrategy.NestedMapper(
                        targetProperty = target,
                        sourceProperty = overrideProp,
                        nestedMappingDescriptor = nestedCandidates.single()
                    )
                }
            }

            // No override: disambiguate by target type, then find source property by type.
            if (targetCandidates.size > 1) {
                ctx.logger.ambiguousNestedDescriptors(
                    targetTypeName = target.type.className.simpleName,
                    matchCount = targetCandidates.size,
                    symbol = target.declaration
                )
                return null
            }
            val nested = targetCandidates.single()

            val sourcePropCandidates = ctx.sourceProps.values.filter { prop ->
                prop.type.className == nested.sourceType.className
            }
            return when (sourcePropCandidates.size) {
                0 -> {
                    ctx.logger.nestedMappingSourceNotFound(
                        sourceTypeName = ctx.sourceTypeName,
                        nestedSourceType = nested.sourceType.className.simpleName,
                        nestedTargetType = nested.targetType.className.simpleName,
                        symbol = target.declaration
                    )
                    null
                }
                1 -> PropertyMappingStrategy.NestedMapper(
                    targetProperty = target,
                    sourceProperty = sourcePropCandidates.single(),
                    nestedMappingDescriptor = nested
                )
                else -> {
                    ctx.logger.ambiguousNestedSourceProperty(
                        sourceTypeName = ctx.sourceTypeName,
                        nestedSourceType = nested.sourceType.className.simpleName,
                        matchingProps = sourcePropCandidates.map { it.name }.sorted(),
                        symbol = target.declaration
                    )
                    null
                }
            }
        }

        // 3. Auto-detection fallback: same name, different types, both mappable classes
        val sourceProp = ctx.sourceProps[target.name] ?: return null

        // Auto-detect List<Source> → List<Target> — checked before the className equality guard
        // because List<A> and List<B> share the same raw className (kotlin.collections.List).
        if (isListType(sourceProp.type) && isListType(target.type)) {
            val srcElement = elementTypeInfo(sourceProp.type) ?: return null
            val tgtElement = elementTypeInfo(target.type) ?: return null
            if (srcElement.className == tgtElement.className) return null
            if (!isMappableClass(srcElement) || !isMappableClass(tgtElement)) return null
            return PropertyMappingStrategy.NestedMapper(
                targetProperty = target,
                sourceProperty = sourceProp,
                nestedMappingDescriptor = synthesiseDescriptor(srcElement, tgtElement, isCollection = true)
            )
        }

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
    // For collection mappings, sourceType/targetType are the element types, not the List type.
    private fun synthesiseDescriptor(
        sourceType: TypeInfo,
        targetType: TypeInfo,
        isCollection: Boolean = false
    ): NestedMappingDescriptor =
        NestedMappingDescriptor(
            nestedMapperId = MapperId(
                sourceQualifiedName = sourceType.declaration.qualifiedName?.asString()
                    ?: sourceType.declaration.simpleName.asString(),
                targetQualifiedName = targetType.declaration.qualifiedName?.asString()
                    ?: targetType.declaration.simpleName.asString()
            ),
            sourceType = sourceType,
            targetType = targetType,
            isCollection = isCollection
        )

    // Guards auto-detection: only trigger for concrete, non-stdlib classes that
    // Kraft can structurally map (has a primary constructor, not an interface/enum/object,
    // not a Kotlin or Java stdlib type).
    private fun isMappableClass(type: TypeInfo): Boolean {
        val decl = type.declaration
        val fqn = decl.qualifiedName?.asString() ?: return false
        return decl.classKind == ClassKind.CLASS
            && decl.primaryConstructor != null
            && Modifier.ABSTRACT !in decl.modifiers
            && Modifier.SEALED !in decl.modifiers
            && !fqn.startsWith("kotlin.")
            && !fqn.startsWith("java.")
    }

    private fun isListType(type: TypeInfo): Boolean =
        type.declaration.qualifiedName?.asString() == "kotlin.collections.List"

    private fun elementTypeInfo(type: TypeInfo): TypeInfo? {
        val arg = type.ksType.arguments.firstOrNull() ?: return null
        val argType = arg.type?.resolve() ?: return null
        if (argType.declaration !is KSClassDeclaration) return null
        return TypeInfo.fromKSType(argType)
    }
}
