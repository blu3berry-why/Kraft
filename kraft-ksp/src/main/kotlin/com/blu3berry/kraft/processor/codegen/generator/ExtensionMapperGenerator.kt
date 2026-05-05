package com.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.MappingSource
import com.blu3berry.kraft.processor.codegen.GenerationConfig
import com.blu3berry.kraft.processor.codegen.OptInMarker
import com.blu3berry.kraft.processor.codegen.OptInMarkerCollector
import com.blu3berry.kraft.processor.codegen.className
import com.blu3berry.kraft.processor.codegen.generatedMapperPackage
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

        val packageName = generatedMapperPackage(fromClass.packageName)
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

        optInAnnotation(OptInMarkerCollector.collect(descriptor))?.let(funBuilder::addAnnotation)

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

    @Suppress("SpreadOperator")
    private fun optInAnnotation(markers: List<OptInMarker>): AnnotationSpec? {
        if (markers.isEmpty()) return null
        val format = markers.joinToString(", ") { "%T::class" }
        val markerTypes = markers.map { ClassName(it.packageName, it.simpleName) }.toTypedArray()
        return AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
            .addMember(format, *markerTypes)
            .build()
    }
}
