package com.blu3berry.kraft

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import com.blu3berry.kraft.processor.codegen.EnumMapperGeneratorProvider
import com.blu3berry.kraft.processor.codegen.EnumMapperGeneratorSpi
import com.blu3berry.kraft.processor.codegen.GenerationConfig
import com.blu3berry.kraft.processor.codegen.GeneratorEnvironment
import com.blu3berry.kraft.processor.codegen.MapperGenerator
import com.blu3berry.kraft.processor.codegen.MapperGeneratorProvider
import com.blu3berry.kraft.processor.codegen.generator.DelegateRegistryGenerator
import com.blu3berry.kraft.processor.codegen.generator.EnumMapperGenerator
import com.blu3berry.kraft.processor.codegen.generator.ExtensionMapperGenerator
import com.blu3berry.kraft.processor.codegen.generator.enumMappingsToConverterEntries
import com.blu3berry.kraft.processor.codegen.generator.mergeWithEnumAmbiguityCheck
import com.blu3berry.kraft.processor.descriptor.DescriptorBuilder
import com.blu3berry.kraft.processor.scanner.AutoEnumMappingDeriver
import com.blu3berry.kraft.processor.scanner.ClassAnnotationScanner
import com.blu3berry.kraft.processor.scanner.ClasspathConverterScanner
import com.blu3berry.kraft.processor.scanner.ConfigObjectScanner
import com.blu3berry.kraft.processor.scanner.EnumMapScanner
import com.blu3berry.kraft.processor.scanner.GlobalConverterScanner
import com.blu3berry.kraft.processor.sides.SideRegistry
import com.blu3berry.kraft.processor.util.KraftKspConstants
import java.util.ServiceLoader

/**
 * Main KSP [SymbolProcessor] that orchestrates the scan, build, and generate pipeline:
 * discovers mapping annotations, builds [MapperDescriptor]s, and delegates code generation
 * to [MapperGenerator] implementations resolved via [ServiceLoader].
 */
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
            KraftKspConstants.FQ_MAP_ENUM,
            KraftKspConstants.FQ_KRAFT_CONVERTER
        ).flatMap { fq ->
            resolver.getSymbolsWithAnnotation(fq).filter { !it.validate() }
        }

        val classMappings = ClassAnnotationScanner(resolver, logger).scan()
        val configMappings = ConfigObjectScanner(resolver, logger).scan()
        val declaredEnumMappings = EnumMapScanner(resolver, logger).scan()
        val handWrittenConverters = GlobalConverterScanner(resolver, logger).scan()
        val enumMappings = declaredEnumMappings + AutoEnumMappingDeriver().derive(
            classMappings = classMappings,
            configMappings = configMappings,
            existingEnumMappings = declaredEnumMappings,
            sameModuleConverters = handWrittenConverters,
        )

        val genConfig = GenerationConfig(
            functionNameTemplate = env.options[KraftKspConstants.OPTION_FUNCTION_NAME_FORMAT]
                ?: "to\${target}"
        )

        val sideRegistry = try {
            SideRegistry.parseFromOptions(env.options)
        } catch (e: IllegalArgumentException) {
            logger.error(e.message ?: "Kraft side configuration error.")
            return deferred
        } catch (e: IllegalStateException) {
            logger.error(e.message ?: "Kraft side configuration error.")
            return deferred
        }

        val generatorEnv = GeneratorEnvironment(
            logger = logger,
            options = env.options,
            config = genConfig,
            sideRegistry = sideRegistry,
        )

        // The enum generator is loaded eagerly (when there are @MapEnum
        // descriptors) so the synthetic-converter registry below derives its
        // call coordinates from the SAME SPI instance that performs the
        // codegen — a custom EnumMapperGeneratorSpi can change the generated
        // package/name and the trampolines must follow.
        val enumGenerator = if (enumMappings.isNotEmpty()) loadEnumGenerator(generatorEnv) else null

        // Auto-resolve @MapEnum mappers as global converters: each enum
        // descriptor becomes a synthetic registry entry, merged with the
        // hand-written @KraftConverter entries for this module. Same pair
        // declared via both → compile-time ambiguity (the merge reports it
        // and keeps the Real entry, so processing continues with at most one
        // entry per pair).
        val syntheticEnumConverters = enumGenerator
            ?.let { enumMappingsToConverterEntries(enumMappings, it, logger) }
            .orEmpty()
        val sameModuleConverters = mergeWithEnumAmbiguityCheck(
            handWrittenConverters, syntheticEnumConverters, logger
        )

        val classpathConverters = ClasspathConverterScanner(resolver, logger)
            .scan(sameModuleKeys = sameModuleConverters.entries.keys)
        val mergedConverters = sameModuleConverters.mergeAsFallback(classpathConverters)

        DelegateRegistryGenerator(
            logger = logger,
            moduleIdOption = env.options[KraftKspConstants.OPTION_MODULE_ID]
        ).generate(sameModuleConverters, codeGenerator)

        val descriptors = DescriptorBuilder(logger).build(
            classMappings = classMappings,
            configMappings = configMappings,
            enumMappings = enumMappings,
            globalConverters = mergedConverters
        )

        enumGenerator?.generate(enumMappings, codeGenerator)

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
                config = env.config,
                sideRegistry = env.sideRegistry,
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
