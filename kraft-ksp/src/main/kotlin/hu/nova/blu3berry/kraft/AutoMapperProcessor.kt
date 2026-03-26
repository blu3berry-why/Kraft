package hu.nova.blu3berry.kraft

import hu.nova.blu3berry.kraft.processor.descriptor.DescriptorBuilder
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import hu.nova.blu3berry.kraft.model.descriptor.MapperDescriptor
import hu.nova.blu3berry.kraft.processor.codegen.GenerationConfig
import hu.nova.blu3berry.kraft.processor.codegen.MapperGenerator
import hu.nova.blu3berry.kraft.processor.codegen.generator.EnumMapperGenerator
import hu.nova.blu3berry.kraft.processor.codegen.generator.ExtensionMapperGenerator
import hu.nova.blu3berry.kraft.processor.scanner.ClassAnnotationScanner
import hu.nova.blu3berry.kraft.processor.scanner.ConfigObjectScanner
import hu.nova.blu3berry.kraft.processor.scanner.EnumMapScanner
import hu.nova.blu3berry.kraft.processor.util.KraftKspConstants

class AutoMapperProcessor(
    private val env: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val codeGenerator = env.codeGenerator
    private val logger = env.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {

        // Collect symbols whose types aren't fully resolved yet and defer them to the next round.
        // This is the standard KSP multi-round pattern; without it, symbols produced by other
        // annotation processors in the same compilation would be silently dropped.
        val deferred = listOf(
            KraftKspConstants.FQ_MAP_FROM,
            KraftKspConstants.FQ_MAP_TO,
            KraftKspConstants.FQ_MAP_CONFIG,
            KraftKspConstants.FQ_MAP_ENUM
        ).flatMap { fq ->
            resolver.getSymbolsWithAnnotation(fq).filter { !it.validate() }
        }

        val classMappingScanResult =
            ClassAnnotationScanner(resolver = resolver, logger = logger).scan()
        val objectMappingScanResult =
            ConfigObjectScanner(resolver = resolver, logger = logger).scan()

        val enumMappingScanResult = EnumMapScanner(resolver, logger).scan()

        val descriptors = DescriptorBuilder(logger).build(
            classMappings = classMappingScanResult,
            configMappings = objectMappingScanResult,
            enumMappings = enumMappingScanResult
        )
        for (descriptor in descriptors) {
            dumpDescriptor(descriptor)
        }

        val template = env.options[KraftKspConstants.OPTION_FUNCTION_NAME_FORMAT]
            ?: "to\${target}" // default

        val genConfig = GenerationConfig(
            functionNameTemplate = template
        )

        // Generate pure enum mappers (respects functionNameFormat)
        if (enumMappingScanResult.isNotEmpty()) {
            EnumMapperGenerator(codeGenerator, logger, genConfig).generate(enumMappingScanResult)
        }

        // 5) CHOOSE GENERATOR (extension for now)
        val generator: MapperGenerator = ExtensionMapperGenerator(
            logger = logger,
            config = genConfig
        )

        // 6) GENERATE FILES FOR EACH DESCRIPTOR
        for (descriptor in descriptors) {
            generator.generate(descriptor, codeGenerator)
        }

        return deferred
    }


    private fun dumpDescriptor(desc: MapperDescriptor) {
        logger.warn("=== Mapper: ${desc.id.sourceQualifiedName} → ${desc.id.targetQualifiedName} ===")

        for (strategy in desc.propertyMappings) {
            logger.warn("  * ${strategy}")
        }

        logger.warn("=== END ===")
    }
}
