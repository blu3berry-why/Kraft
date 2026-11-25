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
import com.blu3berry.kraft.model.descriptor.EnumMappingDescriptor
import com.blu3berry.kraft.model.scan.ConverterEntry
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.model.scan.GlobalConverterRegistry
import com.blu3berry.kraft.processor.scanner.ClassAnnotationScanner
import com.blu3berry.kraft.processor.scanner.ClasspathConverterScanner
import com.blu3berry.kraft.processor.scanner.ConfigObjectScanner
import com.blu3berry.kraft.processor.scanner.EnumMapScanner
import com.blu3berry.kraft.processor.scanner.GlobalConverterScanner
import com.blu3berry.kraft.processor.sides.SideRegistry
import com.blu3berry.kraft.processor.util.KraftKspConstants
import java.util.ServiceLoader
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.NullableTwinConverterReporter
import com.blu3berry.kraft.processor.diagnostics.report
import com.blu3berry.kraft.processor.report.KraftReportCollector

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
    private val nullableTwinReporter = NullableTwinConverterReporter(logger)

    /** Non-null only when `kraft.report=true`; nothing else changes when it is off. */
    private val reportCollector: KraftReportCollector? =
        if (env.options[KraftKspConstants.OPTION_REPORT].equals("true", ignoreCase = true)) {
            KraftReportCollector(moduleId = env.options[KraftKspConstants.OPTION_MODULE_ID])
        } else {
            null
        }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = deferredSymbols(resolver)

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

        val sideRegistry = parseSideRegistryOrNull() ?: return deferred

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

        // Classpath delegates are resolved lazily by name (klib classpaths can't be
        // package-enumerated); eager same-module entries win, so a local
        // @KraftConverter shadows a classpath delegate by resolving first.
        val classpathScanner = ClasspathConverterScanner(resolver, logger)
        val mergedConverters = withClasspathFallback(sameModuleConverters, classpathScanner)
        nullableTwinReporter.report(sameModuleConverters, mergedConverters)

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

        // After generation, so every classpath delegate a mapper looked up is known.
        reportCollector?.collectRound(
            mergedConverters, classpathScanner, declaredEnumMappings, syntheticEnumConverters, sideRegistry
        )

        return deferred
    }

    /** Annotated symbols that do not validate yet; KSP hands them back next round. */
    private fun deferredSymbols(resolver: Resolver): List<KSAnnotated> = listOf(
        KraftKspConstants.FQ_MAP_FROM,
        KraftKspConstants.FQ_MAP_TO,
        KraftKspConstants.FQ_MAP_CONFIG,
        KraftKspConstants.FQ_MAP_ENUM,
        KraftKspConstants.FQ_KRAFT_CONVERTER
    ).flatMap { fq ->
        resolver.getSymbolsWithAnnotation(fq).filter { !it.validate() }
    }

    private fun KraftReportCollector.collectRound(
        converters: GlobalConverterRegistry,
        classpathScanner: ClasspathConverterScanner,
        declaredEnumMappings: List<EnumMappingDescriptor>,
        enumConverters: Map<ConverterTypeKey, ConverterEntry.Synthetic>,
        sideRegistry: SideRegistry,
    ) {
        addConverters(converters.knownEntries(), classpathScanner::moduleIdOf)
        addEnumPairs(declaredEnumMappings, enumConverters)
        addSides(sideRegistry.sides)
        addAliases(sideRegistry.emittedAliases())
    }

    override fun finish() {
        reportCollector?.writeOnce(codeGenerator)
    }

    /** Also written on failure: a failing build is when the report is most needed. */
    override fun onError() {
        reportCollector?.writeOnce(codeGenerator)
    }

    /**
     * Same-module [converters] backed by the lazy classpath-delegate lookup. The silent
     * variant serves the speculative `KRAFT-W001` probe, which must not report or memoize.
     */
    private fun withClasspathFallback(
        converters: GlobalConverterRegistry,
        classpathScanner: ClasspathConverterScanner
    ): GlobalConverterRegistry = GlobalConverterRegistry(
        entries = converters.entries,
        classpathFallback = classpathScanner::lookupByName,
        silentClasspathFallback = classpathScanner::lookupByNameSilently,
        resolvedClasspathEntries = classpathScanner::resolvedEntries
    )

    /**
     * Builds the [SideRegistry] from KSP options, or returns null after logging
     * a config error so the caller can short-circuit out of [process].
     */
    private fun parseSideRegistryOrNull(): SideRegistry? = try {
        SideRegistry.parseFromOptions(env.options)
    } catch (e: IllegalArgumentException) {
        logger.report(
            KraftDiagnostic.SIDE_CONFIGURATION_ERROR,
            e.message ?: "Kraft side configuration error.",
            null
        )
        null
    } catch (e: IllegalStateException) {
        logger.report(
            KraftDiagnostic.SIDE_CONFIGURATION_ERROR,
            e.message ?: "Kraft side configuration error.",
            null
        )
        null
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
                logger.report(
                    KraftDiagnostic.MULTIPLE_MAPPER_GENERATOR_PROVIDERS,
                    "Multiple MapperGeneratorProviders found, " +
                        "using first: ${providers.first()::class.qualifiedName}",
                    null
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
                codeGenerator, logger, env.config, env.sideRegistry
            )
            providers.size == 1 -> providers.single().create(env)
            else -> {
                logger.report(
                    KraftDiagnostic.MULTIPLE_ENUM_GENERATOR_PROVIDERS,
                    "Multiple EnumMapperGeneratorProviders found, " +
                        "using first: ${providers.first()::class.qualifiedName}",
                    null
                )
                providers.first().create(env)
            }
        }
    }
}
