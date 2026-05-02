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
    ): TwoStageResult {
        val upstream = prepareCompilation(upstreamSources, upstreamKspOptions).compile()

        require(upstream.exitCode == KotlinCompilation.ExitCode.OK) {
            "Upstream compilation failed:\n${upstream.messages}"
        }

        val consumer = prepareCompilation(
            sources = consumerSources,
            kspOptions = consumerKspOptions,
            extraClasspath = listOf(upstream.outputDirectory)
        ).compile()

        return TwoStageResult(
            upstream = upstream,
            consumer = consumer,
            consumerGeneratedFiles = consumer.sourcesGeneratedBySymbolProcessor.filter { it.extension == "kt" }.toList()
        )
    }
}

