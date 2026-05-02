package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class CrossModuleConverterTest {

    @Test
    fun `same-module compile produces a delegate registry file`() {
        val source = SourceFile.kotlin(
            "Converters.kt",
            """
            package converters

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabel(): String = "n=" + this
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val registry = generated.firstOrNull { it.name.startsWith("Converters_") }

        assertThat(registry).isNotNull()
        val text = registry!!.readText()
        assertThat(text).contains("package kraft.generated.registry")
        assertThat(text).contains("@KraftConverterDelegate")
        assertThat(text).contains("converters.toLabel")
    }

    @Test
    fun `consumer module picks up upstream @KraftConverter via classpath delegate`() {
        val upstream = SourceFile.kotlin(
            "Converters.kt",
            """
            package upstream

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabel(): String = "n=" + this
            """
        )
        val consumer = SourceFile.kotlin(
            "Models.kt",
            """
            package consumer

            data class Src(val count: Int)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstream),
            consumerSources = listOf(consumer),
            upstreamKspOptions = mapOf("kraft.moduleId" to "upstream"),
            consumerKspOptions = mapOf("kraft.moduleId" to "consumer")
        )

        require(result.consumer.exitCode == KotlinCompilation.ExitCode.OK) {
            "Consumer failed:\n${result.consumer.messages}"
        }
        val mapper = result.consumerGeneratedFiles.first { it.name.contains("Src") }
        val text = mapper.readText()
        assertThat(text).contains("count = this.count.")
        // The delegate lives in kraft.generated.registry; the import points there.
        assertThat(text).contains("import kraft.generated.registry.")
    }

    @Test
    fun `useGlobalConverters=false disables both same-module and classpath converters`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabel(): String = "n=" + this

            data class Src(val count: Int)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class,
                useGlobalConverters = false
            )
            object SrcMapper
            """
        )

        val result = TestKspRunner.compile(source)

        // Same-module @KraftConverter would normally resolve this; with the flag off,
        // we expect the same type-mismatch error path that already covers the no-converter case.
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `same-module @KraftConverter shadows classpath delegate silently`() {
        val upstream = SourceFile.kotlin(
            "Converters.kt",
            """
            package upstream

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabel(): String = "upstream"
            """
        )
        val consumer = SourceFile.kotlin(
            "Models.kt",
            """
            package consumer

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabel(): String = "local"

            data class Src(val count: Int)
            data class Dst(val count: String)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Dst::class)
            object SrcMapper
            """
        )

        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstream),
            consumerSources = listOf(consumer),
            upstreamKspOptions = mapOf("kraft.moduleId" to "upstream"),
            consumerKspOptions = mapOf("kraft.moduleId" to "consumer")
        )

        require(result.consumer.exitCode == KotlinCompilation.ExitCode.OK) {
            "Consumer failed:\n${result.consumer.messages}"
        }
        val mapper = result.consumerGeneratedFiles.first { it.name.contains("Src") }
        val text = mapper.readText()
        assertThat(text).contains("import consumer.toLabel")
        assertThat(text).doesNotContain("import upstream.toLabel")
    }
}
