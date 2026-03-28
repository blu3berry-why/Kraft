package com.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.MappingSource
import com.blu3berry.kraft.processor.codegen.GenerationConfig
import com.blu3berry.kraft.processor.codegen.className
import com.blu3berry.kraft.processor.codegen.MapperGenerator
import com.blu3berry.kraft.processor.util.CodeGenUtils

/**
 * Built-in [MapperGenerator] that produces Kotlin extension functions
 * (`fun Source.toTarget(): Target`) using KotlinPoet.
 */
class ExtensionMapperGenerator(
    private val logger: KSPLogger,
    private val config: GenerationConfig,
) : MapperGenerator {

    private val ctorCallBuilder = CtorCallBuilder(config)

    override fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator) {
        val fromClass = descriptor.sourceType.className
        val toClass = descriptor.targetType.className

        val basePackage = fromClass.packageName.ifBlank { "generated" }
        val packageName = "$basePackage.generated"
        val functionName = config.functionNameFor(descriptor)
        val fileName = "${fromClass.simpleName}To${toClass.simpleName}Mapper"

        val originatingFiles = listOfNotNull(
            when (val src = descriptor.source) {
                is MappingSource.ClassAnnotation -> src.annotatedClass.containingFile
                is MappingSource.ConfigObject -> src.configObject.containingFile
            },
            descriptor.sourceType.declaration.containingFile,
            descriptor.targetType.declaration.containingFile
        ).distinct()

        if (originatingFiles.isEmpty()) {
            logger.warn("Skipping mapper generation for $fromClass → $toClass: no originating file found.")
            return
        }

        val funBuilder = FunSpec.builder(functionName)
            .receiver(fromClass)
            .returns(toClass)
            .addCode("return %L\n", ctorCallBuilder.build(descriptor))

        val file = FileSpec.builder(packageName, "$fileName.kt")
            .addFileComment(CodeGenUtils.generatedBanner())
            .addFunction(funBuilder.build())
            .build()

        @Suppress("SpreadOperator")
        val deps = Dependencies(
            aggregating = false,
            *originatingFiles.toTypedArray()
        )
        file.writeTo(codeGenerator = codeGenerator, dependencies = deps)
        logger.info("Generated extension mapper function: $packageName.$functionName")
    }
}
