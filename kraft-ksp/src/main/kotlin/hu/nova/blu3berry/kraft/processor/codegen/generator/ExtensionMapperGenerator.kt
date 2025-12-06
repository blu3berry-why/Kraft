package hu.nova.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo
import hu.nova.blu3berry.kraft.model.*
import hu.nova.blu3berry.kraft.processor.codegen.GenerationConfig
import hu.nova.blu3berry.kraft.processor.codegen.MapperGenerator
import hu.nova.blu3berry.kraft.processor.codegen.functionNameForNested
import hu.nova.blu3berry.kraft.processor.util.CodeGenUtils

class ExtensionMapperGenerator(
    private val logger: KSPLogger,
    private val config: GenerationConfig,
) : MapperGenerator {

    override fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator) {
        val fromClass = descriptor.fromType.className
        val toClass = descriptor.toType.className

        val basePackage = fromClass.packageName.ifBlank { "generated" }
        val packageName = "$basePackage.generated"
        val functionName = config.functionNameFor(descriptor)
        val fileName = "${fromClass.simpleName}To${toClass.simpleName}Mapper"

        val funBuilder = FunSpec.builder(functionName)
            .receiver(fromClass)
            .returns(toClass)

        // Build constructor call
        val ctorCall = buildConstructorCall(descriptor)

        funBuilder.addCode("return %L\n", ctorCall)

        val file = FileSpec.builder(packageName, "$fileName.kt")
            .addFileComment(CodeGenUtils.generatedBanner())
            .addFunction(funBuilder.build())
            .build()

        // Determine source file for incremental builds
        val originatingFile: KSFile? = when (val src = descriptor.source) {
            is MappingSource.ClassAnnotation -> src.annotatedClass.containingFile
            is MappingSource.ConfigObject -> src.configObject.containingFile
        }

        val dependencies = Dependencies(
            aggregating = false,
            originatingFile ?: return
        )

        file.writeTo(
            codeGenerator = codeGenerator,
            dependencies = dependencies
        )
        logger.info("Generated extension mapper function: $packageName.$functionName")
    }

    /**
     * Builds:
     *     Target(
     *         a = this.a,
     *         b = this.x,
     *         c = 42,
     *     )
     */
    private fun buildConstructorCall(descriptor: MapperDescriptor): CodeBlock {
        val toClass = descriptor.toType.className

        val block = CodeBlock.builder()
        block.add("%T(\n", toClass)
        block.indent()

        val props = descriptor.propertyMappings
            .filterNot { it is PropertyMappingStrategy.Ignored }

        props.forEachIndexed { i, strategy ->
            val isLast = i == props.lastIndex
            addMappingLine(block, strategy)
            if (!isLast) block.add(",\n") else block.add("\n")
        }

        block.unindent()
        block.add(")")

        return block.build()
    }

    private fun addMappingLine(block: CodeBlock.Builder, strategy: PropertyMappingStrategy) {
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
                val s = strategy.sourceProperty.name
                val converter = strategy.converter

                if (converter.enclosingObject != null) {
                    // For converter in an object (e.g., MapConfig object)
                    val enclosingClassName = ClassName(
                        converter.enclosingObject.packageName.asString(),
                        converter.enclosingObject.simpleName.asString()
                    )
                    block.add(
                        "%N = %T.%N(this.%N)",
                        t,
                        enclosingClassName,
                        converter.functionName,
                        s
                    )
                } else if (converter.isExtension) {
                    // For extension function
                    block.add(
                        "%N = this.%N.%N()",
                        t,
                        s,
                        converter.functionName
                    )
                } else {
                    // For top-level function
                    block.add(
                        "%N = %N(this.%N)",
                        t,
                        converter.functionName,
                        s
                    )
                }
            }


            is PropertyMappingStrategy.NestedMapper -> {

                val t = strategy.targetProperty.name
                val s = strategy.sourceProperty.name

                val fnName = config.functionNameForNested(strategy.nestedMappingDescriptor)

                block.add("%N = this.%N.%N()", t, s, fnName)
            }


            is PropertyMappingStrategy.Ignored -> {
                // Already filtered out
            }
        }
    }
}
