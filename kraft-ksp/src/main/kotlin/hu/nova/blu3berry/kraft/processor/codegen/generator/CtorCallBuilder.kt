package hu.nova.blu3berry.kraft.processor.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import hu.nova.blu3berry.kraft.model.descriptor.CollectionKind
import hu.nova.blu3berry.kraft.model.descriptor.ConverterDescriptor
import hu.nova.blu3berry.kraft.model.descriptor.ConverterSource
import hu.nova.blu3berry.kraft.model.descriptor.MapperDescriptor
import hu.nova.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.codegen.GenerationConfig
import hu.nova.blu3berry.kraft.processor.codegen.functionNameForNested

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
                val t = strategy.targetProperty.name
                val s = strategy.sourceProperty.name
                block.add("%N = this.%N", t, s)
            }

            is PropertyMappingStrategy.Renamed -> {
                val t = strategy.targetProperty.name
                val s = strategy.sourceProperty.name
                block.add("%N = this.%N", t, s)
            }

            is PropertyMappingStrategy.Constant -> {
                val t = strategy.targetProperty.name
                block.add("%N = %L", t, strategy.expression)
            }

            is PropertyMappingStrategy.ConverterFunction -> {
                val t = strategy.targetProperty.name
                val converter = strategy.converter

                when (val source = strategy.source) {
                    is ConverterSource.Property -> {
                        val s = source.info.name
                        when {
                            converter.enclosingObject != null && converter.isExtension -> {
                                val enclosingClassName = enclosingClassName(converter)
                                block.add(
                                    "%N = with(%T) { this@%N.%N.%N() }",
                                    t, enclosingClassName, receiverLabel, s, converter.functionName
                                )
                            }
                            converter.enclosingObject != null -> {
                                val enclosingClassName = enclosingClassName(converter)
                                block.add(
                                    "%N = %T.%N(this.%N)",
                                    t, enclosingClassName, converter.functionName, s
                                )
                            }
                            converter.isExtension -> {
                                block.add("%N = this.%N.%N()", t, s, converter.functionName)
                            }
                            else -> {
                                block.add("%N = %N(this.%N)", t, converter.functionName, s)
                            }
                        }
                    }
                    is ConverterSource.WholeObject -> {
                        when {
                            converter.enclosingObject != null && converter.isExtension -> {
                                val enclosingClassName = enclosingClassName(converter)
                                block.add(
                                    "%N = with(%T) { this@%N.%N() }",
                                    t, enclosingClassName, receiverLabel, converter.functionName
                                )
                            }
                            converter.enclosingObject != null -> {
                                val enclosingClassName = enclosingClassName(converter)
                                block.add(
                                    "%N = %T.%N(this)",
                                    t, enclosingClassName, converter.functionName
                                )
                            }
                            converter.isExtension -> {
                                block.add("%N = this.%N()", t, converter.functionName)
                            }
                            else -> {
                                block.add("%N = %N(this)", t, converter.functionName)
                            }
                        }
                    }
                }
            }

            is PropertyMappingStrategy.NestedMapper -> {
                val t = strategy.targetProperty.name
                val s = strategy.sourceProperty.name
                val fnName = config.functionNameForNested(strategy.nestedMappingDescriptor)
                val sourceIsNullable = strategy.sourceProperty.type.isNullable
                val targetIsNullable = strategy.targetProperty.type.isNullable
                val collKind = strategy.nestedMappingDescriptor.collectionKind

                if (collKind != null) {
                    val srcElemIsNullable = strategy.nestedMappingDescriptor.sourceType.isNullable
                    val tgtElemIsNullable = strategy.nestedMappingDescriptor.targetType.isNullable
                    val mapFn = if (srcElemIsNullable && !tgtElemIsNullable) "mapNotNull" else "map"
                    val toSuffix = when (collKind) {
                        CollectionKind.LIST -> ""
                        CollectionKind.SET -> ".toSet()"
                    }
                    val emptyFallback = when (collKind) {
                        CollectionKind.LIST -> "emptyList()"
                        CollectionKind.SET -> "emptySet()"
                    }
                    val itRef = if (srcElemIsNullable) "it?" else "it"

                    if (sourceIsNullable) {
                        val fallback = if (!targetIsNullable) " ?: $emptyFallback" else ""
                        block.add("%N = this.%N?.%L { %L.%N() }%L%L", t, s, mapFn, itRef, fnName, toSuffix, fallback)
                    } else {
                        block.add("%N = this.%N.%L { %L.%N() }%L", t, s, mapFn, itRef, fnName, toSuffix)
                    }
                } else {
                    if (sourceIsNullable) {
                        // Target must be nullable (non-null case is rejected by NestedRule)
                        block.add("%N = this.%N?.%N()", t, s, fnName)
                    } else {
                        block.add("%N = this.%N.%N()", t, s, fnName)
                    }
                }
            }

            is PropertyMappingStrategy.Ignored -> {
                // Already filtered out before this method is called
            }
        }
    }

    private fun enclosingClassName(converter: ConverterDescriptor): ClassName =
        ClassName(
            converter.enclosingObject!!.packageName.asString(),
            converter.enclosingObject.simpleName.asString()
        )
}
