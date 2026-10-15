package com.blu3berry.kraft.diagnostics

import com.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import com.blu3berry.kraft.processor.descriptor.DescriptorBuilder
import com.blu3berry.kraft.processor.scanner.ConfigObjectScanner
import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Unit-level check of the `KRAFT-W001` shadow probe in `PropertyResolver`: runs the config
 * scanner and descriptor builder inside a KSP probe processor (no code generation) and
 * inspects the resolved `ConverterFunction` strategies directly.
 */
@OptIn(ExperimentalCompilerApi::class)
class ShadowedNativeProbeTest {

    private class ProbeProvider : SymbolProcessorProvider {
        val converterStrategies = mutableMapOf<String, PropertyMappingStrategy.ConverterFunction>()
        val logger = RecordingKspLogger()

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    if (converterStrategies.isNotEmpty()) return emptyList()
                    val configs = ConfigObjectScanner(resolver, logger).scan()
                    DescriptorBuilder(logger)
                        .build(classMappings = emptyList(), configMappings = configs, enumMappings = emptyList())
                        .flatMap { it.propertyMappings }
                        .filterIsInstance<PropertyMappingStrategy.ConverterFunction>()
                        .forEach { converterStrategies[it.targetProperty.name] = it }
                    return emptyList()
                }
            }
    }

    private fun resolve(source: SourceFile): ProbeProvider {
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
        return provider
    }

    @Test
    fun `direct-match property claimed by MapUsing has a Direct shadowedNative`() {
        val provider = resolve(
            SourceFile.kotlin(
                "Models.kt",
                """
                data class Src(val name: String, val birthDate: Long)
                data class Dst(val name: String, val age: Int)

                @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
                object SrcMapper {
                    @com.blu3berry.kraft.config.MapUsing(source = "name", target = "name")
                    fun keep(name: String): String = name

                    @com.blu3berry.kraft.config.MapUsing(source = "birthDate", target = "age")
                    fun ageOf(birthDate: Long): Int = 0
                }
                """
            )
        )

        val name = provider.converterStrategies.getValue("name")
        assertThat(name.shadowedNative).isInstanceOf(PropertyMappingStrategy.Direct::class.java)
        assertThat((name.shadowedNative as PropertyMappingStrategy.Direct).sourceProperty.name).isEqualTo("name")

        // A real type gap has no native strategy.
        assertThat(provider.converterStrategies.getValue("age").shadowedNative).isNull()
        // The probe runs with a muted logger: nothing it evaluated reached the user.
        assertThat(provider.logger.entries.filter { it.severity == "error" || it.severity == "warn" }).isEmpty()
    }

    @Test
    fun `whole-source converter is never probed`() {
        val provider = resolve(
            SourceFile.kotlin(
                "Models.kt",
                """
                data class User(val firstName: String, val lastName: String, val fullName: String)
                data class UserDto(val fullName: String)

                @com.blu3berry.kraft.config.MapConfig(source = User::class, target = UserDto::class)
                object UserMapper {
                    @com.blu3berry.kraft.config.MapUsing(target = "fullName")
                    fun User.toFullName(): String = "${'$'}firstName ${'$'}lastName"
                }
                """
            )
        )

        assertThat(provider.converterStrategies.getValue("fullName").shadowedNative).isNull()
    }
}
