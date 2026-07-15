package com.blu3berry.kraft


import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

/** Two-stage result for [TestKspRunner.compileWithUpstream]. */
@OptIn(ExperimentalCompilerApi::class)
data class TwoStageResult(
    val upstream: JvmCompilationResult,
    val consumer: JvmCompilationResult,
    val consumerGeneratedFiles: List<File>
)

object TestKspRunner {

    @OptIn(ExperimentalCompilerApi::class)
    private fun prepareCompilation(
        sources: List<SourceFile>,
        kspOptions: Map<String, String>,
        extraClasspath: List<File> = emptyList()
    ): KotlinCompilation = KotlinCompilation().apply {
        useKsp2()
        kspWithCompilation = true
        inheritClassPath = true
        symbolProcessorProviders = listOf(AutoMapperProcessorProvider()).toMutableList()
        this.sources = sources
        verbose = false
        if (extraClasspath.isNotEmpty()) classpaths = classpaths + extraClasspath
        if (kspOptions.isNotEmpty()) kspProcessorOptions.putAll(kspOptions)
    }

    @OptIn(ExperimentalCompilerApi::class)
    fun compile(
        vararg sources: SourceFile,
        kspOptions: Map<String, String> = emptyMap()
    ): JvmCompilationResult =
        prepareCompilation(sources.toList(), kspOptions).compile()

    @OptIn(ExperimentalCompilerApi::class)
    fun compileAndReturnGenerated(
        vararg sources: SourceFile,
        kspOptions: Map<String, String> = emptyMap()
    ): List<File> {
        val result = compile(*sources, kspOptions = kspOptions)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        return result.sourcesGeneratedBySymbolProcessor.filter { it.extension == "kt" }.toList()
    }

    /**
     * Runs two sequential compilations: an "upstream" module then a "consumer"
     * module that links against the upstream's classes directory. Both run the
     * Kraft KSP processor, so this exercises the real classpath-discovery flow
     * for `@KraftConverter` delegates.
     */
    @OptIn(ExperimentalCompilerApi::class)
    fun compileWithUpstream(
        upstreamSources: List<SourceFile>,
        consumerSources: List<SourceFile>,
        upstreamKspOptions: Map<String, String> = emptyMap(),
        consumerKspOptions: Map<String, String> = emptyMap()
    ): TwoStageResult = compileWithUpstreams(
        upstreamModules = listOf(UpstreamModule(upstreamSources, upstreamKspOptions)),
        consumerSources = consumerSources,
        consumerKspOptions = consumerKspOptions
    )

    /**
     * Like [compileWithUpstream] but with several independent upstream modules, each
     * compiled separately and all placed on the consumer's classpath. Used to exercise
     * upstream-vs-upstream converter ambiguity, which needs at least two modules
     * publishing a delegate for the same type pair.
     */
    @OptIn(ExperimentalCompilerApi::class)
    fun compileWithUpstreams(
        upstreamModules: List<UpstreamModule>,
        consumerSources: List<SourceFile>,
        consumerKspOptions: Map<String, String> = emptyMap()
    ): TwoStageResult {
        require(upstreamModules.isNotEmpty()) {
            "compileWithUpstreams needs at least one upstream module; use compile() for single-stage runs."
        }
        val upstreams = upstreamModules.map { module ->
            val result = prepareCompilation(module.sources, module.kspOptions).compile()
            require(result.exitCode == KotlinCompilation.ExitCode.OK) {
                "Upstream compilation failed:\n${result.messages}"
            }
            result
        }

        val consumer = prepareCompilation(
            sources = consumerSources,
            kspOptions = consumerKspOptions,
            extraClasspath = upstreams.map { it.outputDirectory }
        ).compile()

        return TwoStageResult(
            upstream = upstreams.first(),
            consumer = consumer,
            consumerGeneratedFiles = consumer.sourcesGeneratedBySymbolProcessor.filter { it.extension == "kt" }.toList()
        )
    }
}

/** One upstream module for [TestKspRunner.compileWithUpstreams]. */
data class UpstreamModule(
    val sources: List<SourceFile>,
    val kspOptions: Map<String, String> = emptyMap()
)

