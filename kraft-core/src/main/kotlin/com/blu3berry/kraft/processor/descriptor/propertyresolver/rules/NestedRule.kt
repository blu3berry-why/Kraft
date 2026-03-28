package com.blu3berry.kraft.processor.descriptor.propertyresolver.rules

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.blu3berry.kraft.model.scan.MapNestedAnnotation
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.descriptor.CollectionKind
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.util.ambiguousNestedDescriptors
import com.blu3berry.kraft.processor.util.ambiguousNestedSourceProperty
import com.blu3berry.kraft.processor.util.nestedMappingSourceNotFound
import com.blu3berry.kraft.processor.util.nestedTypeNotMappable
import com.blu3berry.kraft.processor.util.nullableNestedSource

class NestedRule : MappingRule {

    override fun tryResolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        // 1. @MapNested path (highest priority)
        val mapNested = ctx.classNestedOverrides[target.name]
        if (mapNested != null) {
            return resolveMapNested(target, ctx, mapNested)
        }

        // 2. Explicit nestedMappings path (from @MapConfig)
        val targetCollKind = collectionKindOf(target.type)
        val targetIsCollection = targetCollKind != null
        val targetElementType = if (targetIsCollection) {
            elementTypeInfo(target.type)
        } else null
        val targetCandidates = ctx.nestedMappings.filter { nm ->
            if (targetIsCollection && targetElementType != null)
                nm.targetType.qualifiedName == targetElementType.qualifiedName
            else
                nm.targetType.qualifiedName == target.type.qualifiedName
        }
        if (targetCandidates.isNotEmpty()) {
            return resolveExplicitNested(
                target, ctx, targetCandidates, targetCollKind
            )
        }

        // 3. Auto-detection fallback
        return resolveAutoDetected(target, ctx)
    }

    // ---------- 1. @MapNested path ----------

    @Suppress("ReturnCount")
    private fun resolveMapNested(
        target: PropertyInfo,
        ctx: MappingContext,
        mapNested: MapNestedAnnotation
    ): PropertyMappingStrategy? {
        if (target.name in ctx.classRenames) {
            ctx.logger.warn(
                "@MapNested and @MapField both present on " +
                    "'${target.name}' — @MapNested takes precedence.",
                target.declaration
            )
        }

        val sourceName = resolveSourceName(target, ctx, mapNested)
        val sourceProp = ctx.sourceProps[sourceName] ?: run {
            ctx.logger.error(
                "@MapNested on '${target.name}': source property " +
                    "'$sourceName' does not exist in " +
                    "${ctx.sourceTypeName}. Available: " +
                    "${ctx.sourceProps.keys.sorted()}",
                target.declaration
            )
            return null
        }

        val srcCollKind = collectionKindOf(sourceProp.type)
        val tgtCollKind = collectionKindOf(target.type)
        if (srcCollKind != null && srcCollKind == tgtCollKind) {
            return resolveCollectionNested(
                target, ctx, sourceProp, srcCollKind
            )
        }

        val nonMappableType = listOf(sourceProp.type, target.type)
            .firstOrNull { !isMappableClass(it) }
        if (nonMappableType != null) {
            ctx.logger.nestedTypeNotMappable(
                propertyName = target.name,
                typeName = nonMappableType.simpleName,
                symbol = target.declaration
            )
            return null
        }

        if (!checkNullabilityForSingleObject(sourceProp, target, ctx)) {
            return null
        }

        return PropertyMappingStrategy.NestedMapper(
            targetProperty = target,
            sourceProperty = sourceProp,
            nestedMappingDescriptor = synthesiseDescriptor(
                sourceProp.type, target.type
            )
        )
    }

    private fun resolveSourceName(
        target: PropertyInfo,
        ctx: MappingContext,
        mapNested: MapNestedAnnotation
    ): String = when (mapNested) {
        is MapNestedAnnotation.SameName -> target.name
        is MapNestedAnnotation.Renamed -> {
            if (mapNested.sourceName == target.name) {
                ctx.logger.warn(
                    "@MapNested sourceName '${mapNested.sourceName}'" +
                        " equals the property name" +
                        " — sourceName is redundant here.",
                    target.declaration
                )
            }
            mapNested.sourceName
        }
        is MapNestedAnnotation.NotAnnotated -> error(
            "NotAnnotated entry in classNestedOverrides " +
                "for '${target.name}' " +
                "— this is a bug in extractClassNestedOverrides"
        )
    }

    private fun resolveCollectionNested(
        target: PropertyInfo,
        ctx: MappingContext,
        sourceProp: PropertyInfo,
        collectionKind: CollectionKind
    ): PropertyMappingStrategy? {
        val srcElement = elementTypeInfo(sourceProp.type)
        val tgtElement = elementTypeInfo(target.type)
        if (srcElement == null || tgtElement == null ||
            !isMappableClass(srcElement) || !isMappableClass(tgtElement)
        ) {
            val typeName = if (srcElement == null || !isMappableClass(srcElement))
                sourceProp.type.ksType.toString()
            else
                target.type.ksType.toString()
            ctx.logger.nestedTypeNotMappable(
                propertyName = target.name,
                typeName = typeName,
                symbol = target.declaration
            )
            return null
        }
        return PropertyMappingStrategy.NestedMapper(
            targetProperty = target,
            sourceProperty = sourceProp,
            nestedMappingDescriptor = synthesiseDescriptor(
                srcElement, tgtElement, collectionKind = collectionKind
            )
        )
    }

    // ---------- 2. Explicit nestedMappings path ----------

    private fun resolveExplicitNested(
        target: PropertyInfo,
        ctx: MappingContext,
        targetCandidates: List<NestedMappingDescriptor>,
        targetCollKind: CollectionKind?
    ): PropertyMappingStrategy? {
        val explicitSourceName = ctx.configRenames[target.name]
        val targetIsCollection = targetCollKind != null

        if (explicitSourceName != null) {
            return resolveExplicitWithOverride(
                target, ctx, targetCandidates, targetCollKind,
                targetIsCollection, explicitSourceName
            )
        }

        return resolveExplicitWithoutOverride(
            target, ctx, targetCandidates,
            targetCollKind, targetIsCollection
        )
    }

    private fun resolveExplicitWithOverride(
        target: PropertyInfo,
        ctx: MappingContext,
        targetCandidates: List<NestedMappingDescriptor>,
        targetCollKind: CollectionKind?,
        targetIsCollection: Boolean,
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
        val overrideCollKind = collectionKindOf(overrideProp.type)
        val overrideElementType = overrideCollKind?.let {
            elementTypeInfo(overrideProp.type)
        }
        val nestedCandidates = targetCandidates.filter { nm ->
            if (overrideElementType != null)
                nm.sourceType.qualifiedName == overrideElementType.qualifiedName
            else
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
                if (!targetIsCollection &&
                    !checkNullabilityForSingleObject(overrideProp, target, ctx)
                ) return null
                val desc = nestedCandidates.single()
                val finalDesc = if (targetCollKind != null) desc.copy(collectionKind = targetCollKind) else desc
                PropertyMappingStrategy.NestedMapper(
                    targetProperty = target,
                    sourceProperty = overrideProp,
                    nestedMappingDescriptor = finalDesc
                )
            }
        }
    }

    private fun resolveExplicitWithoutOverride(
        target: PropertyInfo,
        ctx: MappingContext,
        targetCandidates: List<NestedMappingDescriptor>,
        targetCollKind: CollectionKind?,
        targetIsCollection: Boolean
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

        val sourcePropCandidates = findSourceCandidates(
            ctx, nested, targetCollKind, targetIsCollection
        )

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
                if (!targetIsCollection &&
                    !checkNullabilityForSingleObject(
                        sourceProp, target, ctx
                    )
                ) return null
                val descriptor = if (targetCollKind != null)
                    nested.copy(collectionKind = targetCollKind)
                else
                    nested
                PropertyMappingStrategy.NestedMapper(
                    targetProperty = target,
                    sourceProperty = sourceProp,
                    nestedMappingDescriptor = descriptor
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

    private fun findSourceCandidates(
        ctx: MappingContext,
        nested: NestedMappingDescriptor,
        targetCollKind: CollectionKind?,
        targetIsCollection: Boolean
    ): List<PropertyInfo> = if (targetIsCollection) {
        ctx.sourceProps.values.filter { prop ->
            collectionKindOf(prop.type) == targetCollKind &&
                elementTypeInfo(prop.type)?.qualifiedName ==
                nested.sourceType.qualifiedName
        }
    } else {
        ctx.sourceProps.values.filter { prop ->
            prop.type.qualifiedName == nested.sourceType.qualifiedName
        }
    }

    // ---------- 3. Auto-detection fallback ----------

    @Suppress("ReturnCount")
    private fun resolveAutoDetected(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {
        val sourceProp = ctx.sourceProps[target.name] ?: return null

        // Collection auto-detection (List/Set)
        val autoSrcCollKind = collectionKindOf(sourceProp.type)
        if (autoSrcCollKind != null &&
            autoSrcCollKind == collectionKindOf(target.type)
        ) {
            val srcElement = elementTypeInfo(sourceProp.type) ?: return null
            val tgtElement = elementTypeInfo(target.type) ?: return null
            if (srcElement.qualifiedName == tgtElement.qualifiedName) return null
            if (!isMappableClass(srcElement) ||
                !isMappableClass(tgtElement)
            ) return null
            return PropertyMappingStrategy.NestedMapper(
                targetProperty = target,
                sourceProperty = sourceProp,
                nestedMappingDescriptor = synthesiseDescriptor(
                    srcElement, tgtElement,
                    collectionKind = autoSrcCollKind
                )
            )
        }

        // Single-object auto-detection
        if (sourceProp.type.qualifiedName == target.type.qualifiedName) return null
        if (!isMappableClass(sourceProp.type) ||
            !isMappableClass(target.type)
        ) return null
        if (!checkNullabilityForSingleObject(sourceProp, target, ctx)) {
            return null
        }

        return PropertyMappingStrategy.NestedMapper(
            targetProperty = target,
            sourceProperty = sourceProp,
            nestedMappingDescriptor = synthesiseDescriptor(
                sourceProp.type, target.type
            )
        )
    }

    // ---------- Shared helpers ----------

    private fun synthesiseDescriptor(
        sourceType: TypeInfo,
        targetType: TypeInfo,
        collectionKind: CollectionKind? = null
    ): NestedMappingDescriptor =
        NestedMappingDescriptor(
            nestedMapperId = MapperId(
                sourceQualifiedName = sourceType.declaration
                    .qualifiedName?.asString()
                    ?: sourceType.declaration.simpleName.asString(),
                targetQualifiedName = targetType.declaration
                    .qualifiedName?.asString()
                    ?: targetType.declaration.simpleName.asString()
            ),
            sourceType = sourceType,
            targetType = targetType,
            collectionKind = collectionKind
        )

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

    private fun collectionKindOf(type: TypeInfo): CollectionKind? =
        when (type.declaration.qualifiedName?.asString()) {
            "kotlin.collections.List" -> CollectionKind.LIST
            "kotlin.collections.Set" -> CollectionKind.SET
            else -> null
        }

    private fun elementTypeInfo(type: TypeInfo): TypeInfo? {
        val arg = type.ksType.arguments.firstOrNull() ?: return null
        val argType = arg.type?.resolve() ?: return null
        if (argType.declaration !is KSClassDeclaration) return null
        return TypeInfo.fromKSType(argType)
    }
}
