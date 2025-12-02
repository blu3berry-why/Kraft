package hu.nova.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import hu.nova.blu3berry.kraft.model.*
import hu.nova.blu3berry.kraft.processor.codegen.GenerationConfig
import hu.nova.blu3berry.kraft.processor.codegen.MapperGenerator
import hu.nova.blu3berry.kraft.processor.util.CodeGenUtils

class ExtensionMapperGenerator(
    private val logger: KSPLogger,
    private val config: GenerationConfig
) : MapperGenerator {

    override fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator) {
        val fromClass = descriptor.fromType.className
        val toClass = descriptor.toType.className

        val packageName = fromClass.packageName + ".generated"
        val functionName = config.functionNameFor(descriptor)
        val fileName = "${fromClass.simpleName}To${toClass.simpleName}Mapper"

        val funBuilder = FunSpec.builder(functionName)
            .receiver(fromClass)
            .returns(toClass)

        // Build constructor call
        val ctorCall = buildConstructorCall(descriptor)

        funBuilder.addCode("return %L\n", ctorCall)

        val file = FileSpec.builder(packageName, fileName)
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

        codeGenerator.createNewFile(
            dependencies,
            packageName,
            fileName
        ).writer().use { out ->
            file.writeTo(out)
        }

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
                logger.error(
                    "ConverterFunction codegen not implemented yet for '${strategy.targetProperty.name}'"
                )
            }

            is PropertyMappingStrategy.NestedMapper -> {
                logger.error(
                    "NestedMapper codegen not implemented yet for '${strategy.targetProperty.name}'"
                )
            }

            is PropertyMappingStrategy.Ignored -> {
                // Already filtered out
            }
        }
    }
}
