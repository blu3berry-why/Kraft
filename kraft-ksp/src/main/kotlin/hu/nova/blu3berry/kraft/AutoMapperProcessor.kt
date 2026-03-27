package hu.nova.blu3berry.kraft

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import hu.nova.blu3berry.kraft.processor.codegen.EnumMapperGeneratorProvider
import hu.nova.blu3berry.kraft.processor.codegen.EnumMapperGeneratorSpi
import hu.nova.blu3berry.kraft.processor.codegen.GenerationConfig
import hu.nova.blu3berry.kraft.processor.codegen.GeneratorEnvironment
import hu.nova.blu3berry.kraft.processor.codegen.MapperGenerator
import hu.nova.blu3berry.kraft.processor.codegen.MapperGeneratorProvider
import hu.nova.blu3berry.kraft.processor.codegen.generator.EnumMapperGenerator
import hu.nova.blu3berry.kraft.processor.codegen.generator.ExtensionMapperGenerator
import hu.nova.blu3berry.kraft.processor.descriptor.DescriptorBuilder
import hu.nova.blu3berry.kraft.processor.scanner.ClassAnnotationScanner
import hu.nova.blu3berry.kraft.processor.scanner.ConfigObjectScanner
import hu.nova.blu3berry.kraft.processor.scanner.EnumMapScanner
import hu.nova.blu3berry.kraft.processor.util.KraftKspConstants
import java.util.ServiceLoader

class AutoMapperProcessor(
    private val env: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val codeGenerator = env.codeGenerator
    private val logger = env.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = listOf(
            KraftKspConstants.FQ_MAP_FROM,
            KraftKspConstants.FQ_MAP_TO,
            KraftKspConstants.FQ_MAP_CONFIG,
            KraftKspConstants.FQ_MAP_ENUM
        ).flatMap { fq ->
            resolver.getSymbolsWithAnnotation(fq).filter { !it.validate() }
        }

        val classMappings = ClassAnnotationScanner(resolver, logger).scan()
        val configMappings = ConfigObjectScanner(resolver, logger).scan()
        val enumMappings = EnumMapScanner(resolver, logger).scan()

        val descriptors = DescriptorBuilder(logger).build(
            classMappings = classMappings,
            configMappings = configMappings,
            enumMappings = enumMappings
        )

        val genConfig = GenerationConfig(
            functionNameTemplate = env.options[KraftKspConstants.OPTION_FUNCTION_NAME_FORMAT]
                ?: "to\${target}"
        )

        val generatorEnv = GeneratorEnvironment(
            logger = logger,
            options = env.options,
            config = genConfig
        )

        if (enumMappings.isNotEmpty()) {
            val enumGenerator = loadEnumGenerator(generatorEnv)
            enumGenerator.generate(enumMappings, codeGenerator)
        }

        val generator = loadMapperGenerator(generatorEnv)
        for (descriptor in descriptors) {
            generator.generate(descriptor, codeGenerator)
        }

        return deferred
    }

    private fun loadMapperGenerator(
        env: GeneratorEnvironment
    ): MapperGenerator {
        val providers = ServiceLoader.load(
            MapperGeneratorProvider::class.java,
            this::class.java.classLoader
        ).toList()

        return when {
            providers.isEmpty() -> ExtensionMapperGenerator(
                logger = env.logger,
                config = env.config
            )
            providers.size == 1 -> providers.single().create(env)
            else -> {
                logger.warn(
                    "Multiple MapperGeneratorProviders found, " +
                        "using first: ${providers.first()::class.qualifiedName}"
                )
                providers.first().create(env)
            }
        }
    }

    private fun loadEnumGenerator(
        env: GeneratorEnvironment
    ): EnumMapperGeneratorSpi {
        val providers = ServiceLoader.load(
            EnumMapperGeneratorProvider::class.java,
            this::class.java.classLoader
        ).toList()

        return when {
            providers.isEmpty() -> EnumMapperGenerator(
                codeGenerator, logger, env.config
            )
            providers.size == 1 -> providers.single().create(env)
            else -> {
                logger.warn(
                    "Multiple EnumMapperGeneratorProviders found, " +
                        "using first: ${providers.first()::class.qualifiedName}"
                )
                providers.first().create(env)
            }
        }
    }
}
