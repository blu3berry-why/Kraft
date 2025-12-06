package hu.nova.blu3berry.kraft


import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

object TestKspRunner {

    @OptIn(ExperimentalCompilerApi::class)
    fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true
            inheritClassPath = true
            symbolProcessorProviders = listOf(AutoMapperProcessorProvider()).toMutableList()
            this.sources = sources.toList()
            verbose = false
        }.compile()
    }

    @OptIn(ExperimentalCompilerApi::class)
    fun compileAndReturnGenerated(vararg sources: SourceFile): List<File> {
        val result = compile(*sources)
        require(result.exitCode == KotlinCompilation.ExitCode.OK) {
            "Compilation failed:\n${result.messages}"
        }

        return result.sourcesGeneratedBySymbolProcessor.filter { it.extension == "kt" }.toList()
    }
}

