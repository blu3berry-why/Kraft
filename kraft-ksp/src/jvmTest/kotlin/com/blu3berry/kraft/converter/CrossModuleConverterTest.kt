package com.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import com.blu3berry.kraft.UpstreamModule
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.processor.util.DelegateNaming
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
        val mapper = result.consumerGeneratedFiles.single { "ToDstMapper" in it.name }
        val text = mapper.readText()
        // The mapper must invoke the upstream delegate by its generated FQN so a
        // looser substring (e.g. "count = this.count.") can't pass while still
        // pointing at, say, a same-module fallback.
        val delegateName = DelegateNaming.delegateNameFor(
            ConverterTypeKey(
                sourceFqName = "kotlin.Int",
                sourceNullable = false,
                targetFqName = "kotlin.String",
                targetNullable = false
            )
        )
        assertThat(text).contains("count = this.count.$delegateName()")
        assertThat(text).contains("import kraft.generated.registry.$delegateName")
    }

    @Test
    fun `useGlobalConverters=false disables same-module converter`() {
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
        // we expect the same RequiredFieldErrorRule diagnostic that fires when no
        // converter matches a mismatched-type pair.
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("Required property 'count'")
    }

    @Test
    fun `useGlobalConverters=false disables classpath converter`() {
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

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class,
                useGlobalConverters = false
            )
            object SrcMapper
            """
        )

        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstream),
            consumerSources = listOf(consumer),
            upstreamKspOptions = mapOf("kraft.moduleId" to "upstream"),
            consumerKspOptions = mapOf("kraft.moduleId" to "consumer")
        )

        // Upstream compiles fine (it has no mapper, just the converter).
        // Consumer must fail with the same missing-converter diagnostic; the flag
        // prevents it from picking up the classpath delegate.
        assertThat(result.consumer.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.consumer.messages).contains("Required property 'count'")
    }

    @Test
    fun `delegate carries @OptIn from the original converter`() {
        val source = SourceFile.kotlin(
            "Converters.kt",
            """
            package converters

            @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
            annotation class ExperimentalThing

            @com.blu3berry.kraft.config.KraftConverter
            @OptIn(ExperimentalThing::class)
            fun Int.toLabel(): String = "n=" + this
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val registry = generated.first { it.name.startsWith("Converters_") }
        val text = registry.readText()

        assertThat(text).contains("@OptIn(ExperimentalThing::class)")
    }

    @Test
    fun `two upstream modules registering the same pair is a classpath ambiguity error`() {
        val upstreamA = SourceFile.kotlin(
            "ConvertersA.kt",
            """
            package upstreama

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabelA(): String = "a=" + this
            """
        )
        val upstreamB = SourceFile.kotlin(
            "ConvertersB.kt",
            """
            package upstreamb

            @com.blu3berry.kraft.config.KraftConverter
            fun Int.toLabelB(): String = "b=" + this
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

        val result = TestKspRunner.compileWithUpstreams(
            upstreamModules = listOf(
                UpstreamModule(listOf(upstreamA), mapOf("kraft.moduleId" to "upstreamA")),
                UpstreamModule(listOf(upstreamB), mapOf("kraft.moduleId" to "upstreamB"))
            ),
            consumerSources = listOf(consumer),
            consumerKspOptions = mapOf("kraft.moduleId" to "consumer")
        )

        assertThat(result.consumer.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
        // The error must name the conflicting pair, identify BOTH producing modules
        // (delegates for the same pair share a function name, so the module ids are
        // the only distinguishing information), and tell the user how to resolve it.
        assertThat(result.consumer.messages)
            .contains("Ambiguous @KraftConverter on the classpath for (kotlin.Int → kotlin.String)")
        assertThat(result.consumer.messages).contains("Kraft module 'upstreamA'")
        assertThat(result.consumer.messages).contains("Kraft module 'upstreamB'")
        assertThat(result.consumer.messages)
            .contains("Disambiguate by adding a same-module @KraftConverter or a per-property @MapUsing.")
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
