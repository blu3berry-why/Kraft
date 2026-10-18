package com.blu3berry.kraft.missingsource

import com.blu3berry.kraft.TestKspRunner
import com.blu3berry.kraft.processor.diagnostics.KraftDiagnostic
import com.blu3berry.kraft.processor.diagnostics.isKraftSuppressed
import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.getDeclaredProperties
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/missing-source-properties`, requirement "KRAFT-W009 is suppressible".
 */
@OptIn(ExperimentalCompilerApi::class)
class W009SuppressionTest {

    private fun configModels(mapperAnnotation: String = "") = SourceFile.kotlin(
        "Models.kt",
        """
        data class PersonSource(val name: String)

        data class PersonDto(val name: String, val nickname: String?)

        $mapperAnnotation
        @com.blu3berry.kraft.config.MapConfig(source = PersonSource::class, target = PersonDto::class)
        object PersonMapper
        """
    )

    private fun mapFromModels(propertyAnnotation: String = "") = SourceFile.kotlin(
        "Models.kt",
        """
        data class PersonSource(val name: String)

        @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
        data class PersonDto(val name: String, $propertyAnnotation val nickname: String?)
        """
    )

    @Test
    fun `Suppressed on the mapping declaration`() {
        val suppressed = TestKspRunner.compile(configModels("@Suppress(\"KRAFT-W009\")"))

        assertThat(suppressed.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(suppressed.messages).doesNotContain("KRAFT-W009")
        assertThat(suppressed.mapperText()).contains("nickname = null")

        val unsuppressed = TestKspRunner.compile(configModels())
        assertThat(unsuppressed.messages).contains("KRAFT-W009")
        assertThat(suppressed.generatedSources()).isEqualTo(unsuppressed.generatedSources())
    }

    @Test
    fun `Suppressed on the target property`() {
        val suppressed = TestKspRunner.compile(mapFromModels("@Suppress(\"KRAFT-W009\")"))

        assertThat(suppressed.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(suppressed.messages).doesNotContain("KRAFT-W009")
        assertThat(suppressed.mapperText()).contains("nickname = null")

        val unsuppressed = TestKspRunner.compile(mapFromModels())
        assertThat(unsuppressed.messages).contains("KRAFT-W009")
        assertThat(suppressed.generatedSources()).isEqualTo(unsuppressed.generatedSources())
    }

    @Test
    fun `Suppressed on the @MapFrom class`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class PersonSource(val name: String)

            @Suppress("KRAFT-W009")
            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val nickname: String?)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-W009")
        assertThat(result.mapperText()).contains("nickname = null")
    }

    @Test
    fun `Wrong code does not suppress`() {
        val result = TestKspRunner.compile(configModels("@Suppress(\"KRAFT-W001\")"))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("[KRAFT-W009]")
    }

    /**
     * Pins where KSP2 puts `@Suppress` written on a constructor `val` without a use-site target,
     * the reason the resolver checks the parameter as well as the property (design D4).
     */
    @Test
    fun `Suppress on a constructor val is visible to KSP`() {
        val provider = ProbeProvider()
        val result = KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(provider)
            sources = listOf(
                SourceFile.kotlin(
                    "Dto.kt",
                    """
                    data class PersonDto(val name: String, @Suppress("KRAFT-W009") val nickname: String?)
                    """
                )
            )
            verbose = false
        }.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        // KSP 2.3 reports it on both; the resolver checks both so either one suffices.
        assertThat(provider.onProperty).isTrue()
        assertThat(provider.onParameter).isTrue()
    }

    private class ProbeProvider : SymbolProcessorProvider {
        var onProperty = false
        var onParameter = false

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    val w009 = KraftDiagnostic.NULLABLE_TARGET_DEFAULTED_TO_NULL
                    resolver.getNewFiles()
                        .flatMap { it.declarations }
                        .filterIsInstance<KSClassDeclaration>()
                        .forEach { cls ->
                            cls.getDeclaredProperties()
                                .filter { it.simpleName.asString() == "nickname" }
                                .forEach { onProperty = onProperty || it.isKraftSuppressed(w009) }
                            cls.primaryConstructor?.parameters
                                ?.filter { it.name?.asString() == "nickname" }
                                ?.forEach { onParameter = onParameter || it.isKraftSuppressed(w009) }
                        }
                    return emptyList()
                }
            }
    }

    private fun JvmCompilationResult.mapperText() =
        sourcesGeneratedBySymbolProcessor.single { it.name == "PersonSourceToPersonDtoMapper.kt" }.readText()

    private fun JvmCompilationResult.generatedSources(): Map<String, String> =
        sourcesGeneratedBySymbolProcessor.filter { it.extension == "kt" }.associate { it.name to it.readText() }
}
