package com.blu3berry.kraft.processor.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.blu3berry.kraft.model.descriptor.CollectionKind
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.ConverterSource
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.codegen.GenerationConfig
import com.blu3berry.kraft.processor.codegen.className
import com.blu3berry.kraft.processor.codegen.functionNameForNested

/**
 * Assembles the `TargetClass(param = value, ...)` constructor invocation [CodeBlock]
 * from the resolved [PropertyMappingStrategy] entries in a [MapperDescriptor].
 *
 * Handles all strategy variants: direct, renamed, constant, converter function
 * (property-source and whole-object, plain and member-extension), and nested mapper.
 * Ignored properties are filtered out before the block is built.
 */
internal class CtorCallBuilder(private val config: GenerationConfig) {

    /**
     * Returns a [CodeBlock] of the form:
     * ```
     * TargetClass(
     *     a = this.a,
     *     b = this.x,
     * )
     * ```
     */
    fun build(descriptor: MapperDescriptor): CodeBlock {
        val toClass = descriptor.targetType.className
        val receiverLabel = config.functionNameFor(descriptor)

        val block = CodeBlock.builder()
        block.add("%T(\n", toClass)
        block.indent()

        val props = descriptor.propertyMappings
            .filterNot { it is PropertyMappingStrategy.Ignored }

        props.forEachIndexed { i, strategy ->
            val isLast = i == props.lastIndex
            addMappingLine(block, strategy, receiverLabel)
            if (!isLast) block.add(",\n") else block.add("\n")
        }

        block.unindent()
        block.add(")")

        return block.build()
    }

    @Suppress("kotlin:S1871")
    private fun addMappingLine(
        block: CodeBlock.Builder,
        strategy: PropertyMappingStrategy,
        receiverLabel: String
    ) {
        when (strategy) {
            is PropertyMappingStrategy.Direct -> {
                block.add("%N = this.%N", strategy.targetProperty.name, strategy.sourceProperty.name)
            }
            is PropertyMappingStrategy.Renamed -> {
                block.add("%N = this.%N", strategy.targetProperty.name, strategy.sourceProperty.name)
            }
            is PropertyMappingStrategy.Constant -> {
                block.add("%N = %L", strategy.targetProperty.name, strategy.expression)
            }
            is PropertyMappingStrategy.ConverterFunction -> {
                addConverterLine(block, strategy, receiverLabel)
            }
            is PropertyMappingStrategy.NestedMapper -> {
                addNestedMapperLine(block, strategy)
            }
            is PropertyMappingStrategy.Ignored -> {
                // Already filtered out before this method is called
            }
        }
    }

    private fun addConverterLine(
        block: CodeBlock.Builder,
        strategy: PropertyMappingStrategy.ConverterFunction,
        receiverLabel: String
    ) {
        val t = strategy.targetProperty.name
        val converter = strategy.converter

        when (val source = strategy.source) {
            is ConverterSource.Property -> {
                addPropertyConverterLine(block, t, source.info.name, converter, receiverLabel)
            }
            is ConverterSource.WholeObject -> {
                addWholeObjectConverterLine(block, t, converter, receiverLabel)
            }
        }
    }

    private fun addPropertyConverterLine(
        block: CodeBlock.Builder,
        targetName: String,
        sourceName: String,
        converter: ConverterDescriptor,
        receiverLabel: String
    ) {
        when {
            converter.enclosingObject != null && converter.isExtension -> {
                block.add(
                    "%N = with(%T) { this@%N.%N.%N() }",
                    targetName, enclosingClassName(converter),
                    receiverLabel, sourceName, converter.functionName
                )
            }
            converter.enclosingObject != null -> {
                block.add(
                    "%N = %T.%N(this.%N)",
                    targetName, enclosingClassName(converter),
                    converter.functionName, sourceName
                )
            }
            converter.isExtension -> {
                block.add(
                    "%N = this.%N.%M()",
                    targetName, sourceName, topLevelMemberName(converter)
                )
            }
            else -> {
                block.add(
                    "%N = %M(this.%N)",
                    targetName, topLevelMemberName(converter), sourceName
                )
            }
        }
    }

    private fun addWholeObjectConverterLine(
        block: CodeBlock.Builder,
        targetName: String,
        converter: ConverterDescriptor,
        receiverLabel: String
    ) {
        when {
            converter.enclosingObject != null && converter.isExtension -> {
                block.add(
                    "%N = with(%T) { this@%N.%N() }",
                    targetName, enclosingClassName(converter),
                    receiverLabel, converter.functionName
                )
            }
            converter.enclosingObject != null -> {
                block.add(
                    "%N = %T.%N(this)",
                    targetName, enclosingClassName(converter),
                    converter.functionName
                )
            }
            converter.isExtension -> {
                block.add("%N = this.%M()", targetName, topLevelMemberName(converter))
            }
            else -> {
                block.add("%N = %M(this)", targetName, topLevelMemberName(converter))
            }
        }
    }

    private fun addNestedMapperLine(
        block: CodeBlock.Builder,
        strategy: PropertyMappingStrategy.NestedMapper
    ) {
        val t = strategy.targetProperty.name
        val s = strategy.sourceProperty.name
        val fnName = config.functionNameForNested(strategy.nestedMappingDescriptor)
        val collKind = strategy.nestedMappingDescriptor.collectionKind

        if (collKind != null) {
            addCollectionNestedLine(block, strategy, t, s, fnName, collKind)
        } else {
            val dot = if (strategy.sourceProperty.type.isNullable) "?." else "."
            block.add("%N = this.%N%L%N()", t, s, dot, fnName)
        }
    }

    private fun addCollectionNestedLine(
        block: CodeBlock.Builder,
        strategy: PropertyMappingStrategy.NestedMapper,
        t: String,
        s: String,
        fnName: String,
        collKind: CollectionKind
    ) {
        val sourceIsNullable = strategy.sourceProperty.type.isNullable
        val targetIsNullable = strategy.targetProperty.type.isNullable
        val srcElemIsNullable = strategy.nestedMappingDescriptor.sourceType.isNullable
        val tgtElemIsNullable = strategy.nestedMappingDescriptor.targetType.isNullable
        val mapFn = if (srcElemIsNullable && !tgtElemIsNullable) "mapNotNull" else "map"
        val toSuffix = when (collKind) {
            CollectionKind.LIST -> ""
            CollectionKind.SET -> if (sourceIsNullable) "?.toSet()" else ".toSet()"
        }
        val itRef = if (srcElemIsNullable) "it?" else "it"

        if (sourceIsNullable) {
            val emptyFallback = when (collKind) {
                CollectionKind.LIST -> "emptyList()"
                CollectionKind.SET -> "emptySet()"
            }
            val fallback = if (!targetIsNullable) " ?: $emptyFallback" else ""
            block.add("%N = this.%N?.%L { %L.%N() }%L%L", t, s, mapFn, itRef, fnName, toSuffix, fallback)
        } else {
            block.add("%N = this.%N.%L { %L.%N() }%L", t, s, mapFn, itRef, fnName, toSuffix)
        }
    }

    private fun enclosingClassName(converter: ConverterDescriptor): ClassName {
        val obj = converter.enclosingObject!!
        return ClassName(obj.packageName.asString(), obj.simpleName.asString())
    }

    private fun topLevelMemberName(converter: ConverterDescriptor): MemberName =
        MemberName(converter.callPackageName, converter.callFunctionName)
}
