package com.blu3berry.kraft.processor.codegen.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.blu3berry.kraft.config.AliasEmitMode
import com.blu3berry.kraft.model.MapperId
import com.blu3berry.kraft.model.descriptor.MapperDescriptor
import com.blu3berry.kraft.model.descriptor.MappingSource
import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.codegen.GenerationConfig
import com.blu3berry.kraft.processor.codegen.MapperGenerator
import com.blu3berry.kraft.processor.codegen.OptInMarker
import com.blu3berry.kraft.processor.codegen.OptInMarkerCollector
import com.blu3berry.kraft.processor.codegen.className
import com.blu3berry.kraft.processor.codegen.generatedMapperPackage
import com.blu3berry.kraft.processor.sides.SideRegistry
import com.blu3berry.kraft.processor.util.CodeGenUtils
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.isKraftSuppressed
import com.blu3berry.kraft.processor.diagnostics.report

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

    /** (converter function, target property, mapper) triples already reported as `KRAFT-W001`. */
    private val reportedRedundantConverters = mutableSetOf<Triple<KSFunctionDeclaration, String, MapperId>>()

    override fun generate(descriptor: MapperDescriptor, codeGenerator: CodeGenerator) {
        val fromClass = descriptor.sourceType.className
        val toClass = descriptor.targetType.className

        val packageName = generatedMapperPackage(fromClass.packageName)
        val functionName = config.functionNameFor(descriptor)
        val fileName = "${CodeGenUtils.qualifiedSegments(descriptor.sourceType.declaration)}" +
            "To${CodeGenUtils.qualifiedSegments(descriptor.targetType.declaration)}Mapper"

        val originatingFiles = listOfNotNull(
            when (val src = descriptor.source) {
                is MappingSource.ClassAnnotation -> src.annotatedClass.containingFile
                is MappingSource.ConfigObject -> src.configObject.containingFile
            },
            descriptor.sourceType.declaration.containingFile,
            descriptor.targetType.declaration.containingFile
        ).distinct()

        if (originatingFiles.isEmpty()) {
            logger.report(
                KraftDiagnostic.MAPPER_GENERATION_SKIPPED,
                "Skipping mapper generation for $fromClass → $toClass: no originating file found.",
                null
            )
            return
        }

        reportRedundantConverters(descriptor, packageName, functionName)

        val verboseFn = FunSpec.builder(functionName)
            .receiver(fromClass)
            .returns(toClass)
            .addCode("return %L\n", ctorCallBuilder.build(descriptor))

        optInAnnotation(OptInMarkerCollector.collect(descriptor))?.let(verboseFn::addAnnotation)

        val fileBuilder = FileSpec.builder(packageName, fileName)
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
            logger.report(
                KraftDiagnostic.SIDE_CONFIGURATION_ERROR,
                e.message ?: "Kraft side configuration error.",
                null
            )
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
            logger.report(KraftDiagnostic.ALIAS_COLLISION, e.message ?: "Alias collision.", originSymbol)
            return null
        }

        return FunSpec.builder(aliasName)
            .receiver(fromClass)
            .returns(toClass)
            .addKdoc("Alias generated for side ${side.name} (template = ${side.template.raw})")
            .addCode("return %N()\n", verboseFunctionName)
            .build()
    }

    /**
     * Emits `KRAFT-W001` for each property-source `@MapUsing` that claims a property Kraft
     * would map natively ([PropertyMappingStrategy.ConverterFunction.shadowedNative]), unless
     * the converter function carries `@Suppress("KRAFT-W001")`. The shown assignment is
     * rendered by the same [CtorCallBuilder] path as the generated mapper. Generated code is
     * not affected: the user's converter keeps priority.
     */
    private fun reportRedundantConverters(
        descriptor: MapperDescriptor,
        packageName: String,
        functionName: String,
    ) {
        descriptor.propertyMappings
            .filterIsInstance<PropertyMappingStrategy.ConverterFunction>()
            .forEach { strategy ->
                val native = strategy.shadowedNative ?: return@forEach
                val function = strategy.converter.function ?: return@forEach
                if (function.isKraftSuppressed(KraftDiagnostic.REDUNDANT_MAP_USING)) return@forEach
                val key = Triple(function, strategy.targetProperty.name, descriptor.id)
                if (!reportedRedundantConverters.add(key)) return@forEach

                val assignment = renderStandalone(
                    ctorCallBuilder.renderAssignment(native, functionName),
                    packageName
                )
                logger.report(
                    KraftDiagnostic.REDUNDANT_MAP_USING,
                    redundantMapUsingMessage(strategy.converter.functionName, strategy.targetProperty.name, assignment),
                    function
                )
            }
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

private const val RENDER_PROBE_FUNCTION = "kraftRenderProbe"

/**
 * Renders [assignment] the way it appears in a generated file in [packageName]: member
 * references become imports, so `it.toRow()` reads as in the real mapper rather than
 * fully qualified. KotlinPoet line wrapping is undone, so the result is one line.
 */
internal fun renderStandalone(assignment: CodeBlock, packageName: String): String {
    val probe = FunSpec.builder(RENDER_PROBE_FUNCTION).addCode("%L\n", assignment).build()
    val text = FileSpec.builder(packageName, RENDER_PROBE_FUNCTION).addFunction(probe).build().toString()
    return text
        .substringAfter("fun $RENDER_PROBE_FUNCTION() {")
        .substringBeforeLast("}")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

/** The `KRAFT-W001` message body; [report] adds the code prefix, banner and docs link. */
internal fun redundantMapUsingMessage(functionName: String, propertyName: String, assignment: String): String =
    "@MapUsing '$functionName' on target property '$propertyName' duplicates a mapping Kraft already generates.\n" +
        "\n" +
        "Kraft would generate:\n" +
        "    $assignment\n" +
        "\n" +
        "Remove '$functionName', or add @Suppress(\"KRAFT-W001\") to it if its body intentionally differs " +
        "(filtering, sorting, defaults)."
