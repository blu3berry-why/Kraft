package com.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.toClassName
import com.blu3berry.kraft.model.descriptor.ConverterDescriptor
import com.blu3berry.kraft.model.descriptor.ConverterSource
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.NestedMappingDescriptor
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.codegen.GenerationConfig
import com.blu3berry.kraft.processor.codegen.className
import com.blu3berry.kraft.processor.codegen.functionNameForNested
import com.blu3berry.kraft.processor.codegen.generatedMapperPackage

/**
 * Assembles the `TargetClass(param = value, ...)` constructor invocation [CodeBlock]
 * from the resolved [PropertyMappingStrategy] entries in a [MapperDescriptor].
 *
 * Handles all strategy variants: direct, renamed, constant, `@MapConstant` member, converter function
 * (property-source and whole-object, plain and member-extension), nested mapper,
 * value-class wrap/unwrap, and container mappings (rendered by [ContainerShapeRenderer]).
 * Ignored properties are filtered out before the block is built.
 */
internal class CtorCallBuilder(private val config: GenerationConfig) {

    private val containerRenderer = ContainerShapeRenderer(
        nestedMapperMember = ::nestedMapperMemberName,
        converterMember = ::topLevelMemberName
    )

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

    /**
     * Renders one `name = expression` assignment exactly as [build] emits it inside the
     * constructor call. Used to show the native assignment in `KRAFT-W001`.
     */
    fun renderAssignment(strategy: PropertyMappingStrategy, receiverLabel: String): CodeBlock =
        CodeBlock.builder().also { addMappingLine(it, strategy, receiverLabel) }.build()

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
            is PropertyMappingStrategy.ConstantMember -> {
                val constant = strategy.constant
                val format = if (constant.isFunction) "%N = %T.%N()" else "%N = %T.%N"
                block.add(
                    format, strategy.targetProperty.name,
                    enclosingClassName(constant.enclosingObject), constant.memberName
                )
            }
            is PropertyMappingStrategy.ConverterFunction -> {
                addConverterLine(block, strategy, receiverLabel)
            }
            is PropertyMappingStrategy.NestedMapper -> {
                addNestedMapperLine(block, strategy)
            }
            is PropertyMappingStrategy.ValueClassWrap -> {
                addValueClassWrapLine(block, strategy)
            }
            is PropertyMappingStrategy.ValueClassUnwrap -> {
                addValueClassUnwrapLine(block, strategy)
            }
            is PropertyMappingStrategy.ContainerMapping -> {
                val receiver = CodeBlock.of("this.%N", strategy.sourceProperty.name)
                block.add("%N = %L", strategy.targetProperty.name, containerRenderer.render(receiver, strategy.shape))
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
                val dot = if (converter.useSafeCall) "?." else "."
                block.add(
                    "%N = this.%N%L%M()",
                    targetName, sourceName, dot, topLevelMemberName(converter)
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
        val fnMember = nestedMapperMemberName(strategy.nestedMappingDescriptor)
        val dot = if (strategy.sourceProperty.type.isNullable) "?." else "."
        block.add("%N = this.%N%L%M()", t, s, dot, fnMember)
    }

    private fun addValueClassWrapLine(
        block: CodeBlock.Builder,
        strategy: PropertyMappingStrategy.ValueClassWrap
    ) {
        val t = strategy.targetProperty.name
        val s = strategy.sourceProperty.name
        val valueClass = strategy.valueClass.className
        if (strategy.safeCall) {
            block.add("%N = this.%N?.let { %T(it) }", t, s, valueClass)
        } else {
            block.add("%N = %T(this.%N)", t, valueClass, s)
        }
    }

    private fun addValueClassUnwrapLine(
        block: CodeBlock.Builder,
        strategy: PropertyMappingStrategy.ValueClassUnwrap
    ) {
        val t = strategy.targetProperty.name
        val s = strategy.sourceProperty.name
        // %L, not %N: KotlinPoet backtick-escapes the soft keyword `value`, the
        // conventional underlying name, which is legal but noisy in generated code.
        val underlying = strategy.underlyingPropertyName
        val dot = if (strategy.safeCall) "?." else "."
        block.add("%N = this.%N%L%L", t, s, dot, underlying)
    }

    private fun nestedMapperMemberName(nested: NestedMappingDescriptor): MemberName {
        val pkg = generatedMapperPackage(nested.sourceType.className.packageName)
        return MemberName(pkg, config.functionNameForNested(nested))
    }

    private fun enclosingClassName(converter: ConverterDescriptor): ClassName =
        enclosingClassName(converter.enclosingObject!!)

    // toClassName() keeps enclosing declarations, so a nested object renders as Outer.Mapper.
    private fun enclosingClassName(obj: KSClassDeclaration): ClassName = obj.toClassName()

    private fun topLevelMemberName(converter: ConverterDescriptor): MemberName =
        MemberName(converter.callPackageName, converter.callFunctionName)
}
