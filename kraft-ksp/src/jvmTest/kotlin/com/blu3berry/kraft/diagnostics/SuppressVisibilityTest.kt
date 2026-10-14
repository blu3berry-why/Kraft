package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.isKraftSuppressed
import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Design D5 relies on `kotlin.Suppress` (SOURCE retention) being visible to KSP on
 * same-module declarations. This compiles real source through the same KSP2 setup as
 * `TestKspRunner`, but with a probe processor that asks [isKraftSuppressed] about each
 * top-level function. KSP 2.3 has no KSP1 mode, so KSP2 is the only runner to cover.
 */
@OptIn(ExperimentalCompilerApi::class)
class SuppressVisibilityTest {

    private class ProbeProvider : SymbolProcessorProvider {
        val results = mutableMapOf<String, Map<KraftDiagnostic, Boolean>>()

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    resolver.getNewFiles()
                        .flatMap { it.declarations }
                        .filterIsInstance<KSFunctionDeclaration>()
                        .forEach { fn ->
                            results[fn.simpleName.asString()] =
                                KraftDiagnostic.entries.associateWith { fn.isKraftSuppressed(it) }
                        }
                    return emptyList()
                }
            }
    }

    private fun probe(source: SourceFile): Map<String, Map<KraftDiagnostic, Boolean>> {
        val provider = ProbeProvider()
        val result = KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(provider)
            sources = listOf(source)
            verbose = false
        }.compile()
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        return provider.results
    }

    @Test
    fun `Suppress on a same-module function is visible to KSP and matched exactly`() {
        val source = SourceFile.kotlin(
            "Converters.kt",
            """
            package models

            @Suppress("KRAFT-W001")
            fun suppressedW001(value: String): String = value

            @Suppress("KRAFT-W002")
            fun suppressedW002(value: String): String = value

            @Suppress("UNUSED_PARAMETER", "KRAFT-W001")
            fun suppressedAmongOthers(value: String): String = ""

            @Suppress("kraft-w001")
            fun wrongCase(value: String): String = value

            fun notSuppressed(value: String): String = value
            """
        )

        val results = probe(source)
        val w001 = KraftDiagnostic.REDUNDANT_MAP_USING
        val w002 = KraftDiagnostic.NULLABLE_TWIN_CONVERTER

        assertThat(results.getValue("suppressedW001")).containsExactly(w001, true, w002, false)
        assertThat(results.getValue("suppressedW002")).containsExactly(w001, false, w002, true)
        assertThat(results.getValue("suppressedAmongOthers")).containsExactly(w001, true, w002, false)
        assertThat(results.getValue("wrongCase")).containsExactly(w001, false, w002, false)
        assertThat(results.getValue("notSuppressed")).containsExactly(w001, false, w002, false)
    }
}
