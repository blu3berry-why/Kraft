package hu.nova.blu3berry.kraft

import DescriptorBuilder
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import hu.nova.blu3berry.kraft.model.MapperDescriptor
import hu.nova.blu3berry.kraft.processor.codegen.GenerationConfig
import hu.nova.blu3berry.kraft.processor.codegen.MapperGenerator
import hu.nova.blu3berry.kraft.processor.codegen.generator.ExtensionMapperGenerator
import hu.nova.blu3berry.kraft.processor.scanner.ClassAnnotationScanner
import hu.nova.blu3berry.kraft.processor.scanner.ConfigObjectScanner
import hu.nova.blu3berry.kraft.processor.scanner.EnumMapScanner

class AutoMapperProcessor(
    private val env: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val codeGenerator = env.codeGenerator
    private val logger = env.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {

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

        val template = env.options["kraft.functionNameFormat"]
            ?: "to\${target}" // default

        val genConfig = GenerationConfig(
            functionNameTemplate = template
        )

        // 5) CHOOSE GENERATOR (extension for now)
        val generator: MapperGenerator = ExtensionMapperGenerator(
            logger = logger,
            config = genConfig
        )

        // 6) GENERATE FILES FOR EACH DESCRIPTOR
        for (descriptor in descriptors) {
            generator.generate(descriptor, codeGenerator)
        }



        return emptyList()
    }


    private fun dumpDescriptor(desc: MapperDescriptor) {
        logger.warn("=== Mapper: ${desc.id.fromQualifiedName} → ${desc.id.toQualifiedName} ===")

        for (strategy in desc.propertyMappings) {
            logger.warn("  * ${strategy}")
        }

        logger.warn("=== END ===")
    }
}
