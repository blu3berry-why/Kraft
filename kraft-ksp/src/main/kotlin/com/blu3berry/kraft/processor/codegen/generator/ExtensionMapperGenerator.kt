package com.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.MappingSource
import com.blu3berry.kraft.processor.codegen.GenerationConfig
import com.blu3berry.kraft.processor.codegen.MapperGenerator
import com.blu3berry.kraft.processor.codegen.OptInMarker
import com.blu3berry.kraft.processor.codegen.OptInMarkerCollector
import com.blu3berry.kraft.processor.codegen.className
import com.blu3berry.kraft.processor.codegen.generatedMapperPackage
import com.blu3berry.kraft.processor.sides.SideRegistry
import com.blu3berry.kraft.processor.util.CodeGenUtils

/**
 * Built-in [MapperGenerator] that produces Kotlin extension functions
 * (`fun Source.toTarget(): Target`) using KotlinPoet.
 *
 * Also emits a short side-alias delegate if the target's package matches a
 * registered side in [sideRegistry] and the descriptor's `aliasEmitMode`
 * resolves to [AliasEmitMode.BOTH].
 */
class ExtensionMapperGenerator(
    private val logger: KSPLogger,
    private val config: GenerationConfig,
    private val sideRegistry: SideRegistry = SideRegistry.parseFromOptions(emptyMap()),
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

        val verboseFn = FunSpec.builder(functionName)
            .receiver(fromClass)
            .returns(toClass)
            .addCode("return %L\n", ctorCallBuilder.build(descriptor))

        optInAnnotation(OptInMarkerCollector.collect(descriptor))?.let(verboseFn::addAnnotation)

        val fileBuilder = FileSpec.builder(packageName, "$fileName.kt")
            .addFileComment(CodeGenUtils.generatedBanner())
            .addFunction(verboseFn.build())

        // ----- Side alias (if any) -----
        val aliasFn = buildAliasFunSpec(
            descriptor = descriptor,
            verboseFunctionName = functionName,
            fromClass = fromClass,
            toClass = toClass,
        )
        if (aliasFn != null) fileBuilder.addFunction(aliasFn)

        @Suppress("SpreadOperator")
        val deps = Dependencies(
            aggregating = false,
            *originatingFiles.toTypedArray()
        )
        fileBuilder.build().writeTo(codeGenerator = codeGenerator, dependencies = deps)
        logger.info("Generated extension mapper function: $packageName.$functionName")
    }

    private fun buildAliasFunSpec(
        descriptor: MapperDescriptor,
        verboseFunctionName: String,
        fromClass: ClassName,
        toClass: ClassName,
    ): FunSpec? {
        val targetFqn = toClass.canonicalName
        val side = try {
            sideRegistry.resolveSide(targetFqn) ?: return null
        } catch (e: IllegalStateException) {
            logger.error(e.message ?: "Kraft side configuration error.")
            return null
        }

        val effectiveMode = when (descriptor.aliasEmitMode) {
            AliasEmitMode.INHERIT -> side.emitMode
            else -> descriptor.aliasEmitMode
        }
        if (effectiveMode == AliasEmitMode.FULL_NAME_ONLY) return null

        val aliasName = side.template.render(
            side = side.name,
            source = fromClass.simpleName,
            target = toClass.simpleName,
        )

        val originSymbol = when (val src = descriptor.source) {
            is MappingSource.ClassAnnotation -> src.annotatedClass
            is MappingSource.ConfigObject -> src.configObject
        }
        val mapperOrigin = originSymbol.qualifiedName?.asString() ?: "<unknown>"
        try {
            sideRegistry.recordAlias(fromClass.canonicalName, aliasName, mapperOrigin)
        } catch (e: IllegalStateException) {
            logger.error(e.message ?: "Alias collision.", originSymbol)
            return null
        }

        return FunSpec.builder(aliasName)
            .receiver(fromClass)
            .returns(toClass)
            .addKdoc("Alias generated for side ${side.name} (template = ${side.template.raw})")
            .addCode("return %N()\n", verboseFunctionName)
            .build()
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
