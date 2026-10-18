package com.blu3berry.kraft.missingsource

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.descriptor.MappingContext
import com.blu3berry.kraft.model.toTypeInfo
import com.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import com.blu3berry.kraft.processor.descriptor.propertyresolver.PropertyResolver
import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec `mapping/missing-source-properties`, requirement "A mapper is never skipped without a
 * diagnostic".
 */
@OptIn(ExperimentalCompilerApi::class)
class NoSilentDropTest {

    private class RecordingLogger : KSPLogger {
        val errors = mutableListOf<String>()
        override fun error(message: String, symbol: KSNode?) { errors += message }
        override fun logging(message: String, symbol: KSNode?) = Unit
        override fun info(message: String, symbol: KSNode?) = Unit
        override fun warn(message: String, symbol: KSNode?) = Unit
        override fun exception(e: Throwable) = throw e
    }

    private class ProbeProvider : SymbolProcessorProvider {
        val logger = RecordingLogger()
        var resolved: Any? = "not run"

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    val cls = resolver.getNewFiles()
                        .flatMap { it.declarations }
                        .filterIsInstance<KSClassDeclaration>()
                        .single { it.simpleName.asString() == "PersonDto" }
                    val prop = cls.getDeclaredProperties().single { it.simpleName.asString() == "score" }
                    val ksType = prop.type.resolve()
                    val target = PropertyInfo(
                        name = "score",
                        type = (ksType.declaration as KSClassDeclaration).toTypeInfo(ksType),
                        declaration = prop,
                        hasDefault = false
                    )
                    val ctx = MappingContext(
                        logger = logger,
                        sourceProps = emptyMap(),
                        classRenames = emptyMap(),
                        configRenames = emptyMap(),
                        converters = emptyList(),
                        sourceTypeName = "PersonSource",
                        targetTypeName = "PersonDto"
                    )
                    resolved = PropertyResolver(rules = listOf(MappingRule { _, _ -> null }))
                        .resolve(target, ctx)
                    return emptyList()
                }
            }
    }

    @Test
    fun `Resolution yields nothing and nothing was reported`() {
        val provider = ProbeProvider()
        val result = KotlinCompilation().apply {
            useKsp2()
            kspWithCompilation = true
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(provider)
            sources = listOf(SourceFile.kotlin("Dto.kt", "data class PersonDto(val score: Int)"))
            verbose = false
        }.compile()

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(provider.resolved).isNull()
        val message = provider.logger.errors.single()
        assertThat(message.lines().first { it.startsWith("[KRAFT-") })
            .isEqualTo(
                "[KRAFT-E065] Kraft could not map property 'score' in target type 'PersonDto' " +
                    "and reported no other error, so the mapper was not generated."
            )
        assertThat(message.trimEnd().lines().last())
            .isEqualTo("See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#kraft-e065")
    }
}
