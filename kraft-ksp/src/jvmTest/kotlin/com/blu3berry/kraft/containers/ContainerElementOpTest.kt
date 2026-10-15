package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import com.blu3berry.kraft.containers.ContainerTestSupport.compile
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import com.blu3berry.kraft.model.scan.ConverterTypeKey
import com.blu3berry.kraft.processor.util.DelegateNaming
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec requirement "Element operations reuse the scalar rules": converters on elements (task 3.1). */
@OptIn(ExperimentalCompilerApi::class)
class ContainerElementOpTest {

    // Interfaces are not mappable classes, so no nested mapper competes with the converter
    // (as for a scalar property: an auto-detected nested mapper outranks a converter).
    private val fooBar = """
        interface Foo { val v: Int }
        interface Bar { val v: Int }

        @com.blu3berry.kraft.config.KraftConverter
        fun Foo.intoBar(): Bar = object : Bar { override val v = this@intoBar.v }
    """

    @Test
    fun `Named KraftConverter on elements`() {
        val text = generatedText(models("List<Foo>", "List<Bar>", extraModels = fooBar))
        assertThat(text).contains("items = this.items.map { it.intoBar() }")
    }

    @Test
    fun `Named KraftConverter on elements of other collection kinds`() {
        assertThat(generatedText(models("Array<Foo>", "Collection<Bar>", extraModels = fooBar)))
            .contains("items = this.items.map { it.intoBar() }")
        assertThat(generatedText(models("Set<Foo>", "Set<Bar>", extraModels = fooBar)))
            .contains("items = this.items.map { it.intoBar() }.toSet()")
    }

    @Test
    fun `Non-null converter serves nullable elements`() {
        assertThat(generatedText(models("List<Foo?>", "List<Bar?>", extraModels = fooBar)))
            .contains("items = this.items.map { it?.intoBar() }")
        assertThat(generatedText(models("List<Foo?>", "List<Bar>", extraModels = fooBar)))
            .contains("items = this.items.mapNotNull { it?.intoBar() }")
    }

    @Test
    fun `Converter declared in another package is imported`() {
        val converters = SourceFile.kotlin(
            "Converters.kt",
            """
            package conv

            import models.Foo
            import models.Bar

            @com.blu3berry.kraft.config.KraftConverter
            fun Foo.intoBar(): Bar = object : Bar { override val v = this@intoBar.v }
            """.trimIndent()
        )
        val modelsFile = SourceFile.kotlin(
            "Models.kt",
            """
            package models

            interface Foo { val v: Int }
            interface Bar { val v: Int }

            data class Src(val items: List<Foo>)
            data class Tgt(val items: List<Bar>)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Tgt::class)
            object M
            """.trimIndent()
        )
        val generated = TestKspRunner.compileAndReturnGenerated(converters, modelsFile)
        val text = generated.first { "ToTgtMapper" in it.name }.readText()
        assertThat(text).contains("import conv.intoBar")
        assertThat(text).contains("items = this.items.map { it.intoBar() }")
    }

    @Test
    fun `Converter from an upstream module is applied under its delegate name`() {
        val upstream = SourceFile.kotlin(
            "Converters.kt",
            """
            package upstream

            interface Foo { val v: Int }
            interface Bar { val v: Int }

            @com.blu3berry.kraft.config.KraftConverter
            fun Foo.intoBar(): Bar = object : Bar { override val v = this@intoBar.v }
            """.trimIndent()
        )
        val consumer = SourceFile.kotlin(
            "Models.kt",
            """
            package consumer

            import upstream.Foo
            import upstream.Bar

            data class Src(val items: List<Foo>)
            data class Tgt(val items: List<Bar>)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Tgt::class)
            object M
            """.trimIndent()
        )
        val result = TestKspRunner.compileWithUpstream(
            upstreamSources = listOf(upstream),
            consumerSources = listOf(consumer),
            upstreamKspOptions = mapOf("kraft.moduleId" to "upstream"),
            consumerKspOptions = mapOf("kraft.moduleId" to "consumer")
        )
        require(result.consumer.exitCode == KotlinCompilation.ExitCode.OK) { result.consumer.messages }
        val delegate = DelegateNaming.delegateNameFor(
            ConverterTypeKey("upstream.Foo", false, "upstream.Bar", false)
        )
        val text = result.consumerGeneratedFiles.single { "ToTgtMapper" in it.name }.readText()
        assertThat(text).contains("items = this.items.map { it.$delegate() }")
        assertThat(text).contains("import kraft.generated.registry.$delegate")
    }

    @Test
    fun `Hand-written MapUsing that duplicates an element-wise converter gets W001`() {
        val result = compile(
            """
            package models

            interface Foo { val v: Int }
            interface Bar { val v: Int }

            @com.blu3berry.kraft.config.KraftConverter
            fun Foo.intoBar(): Bar = object : Bar { override val v = this@intoBar.v }

            data class Src(val items: List<Foo>)
            data class Tgt(val items: List<Bar>)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Tgt::class)
            object M {
                @com.blu3berry.kraft.config.MapUsing(source = "items", target = "items")
                fun convert(items: List<Foo>): List<Bar> = items.map { it.intoBar() }
            }
            """.trimIndent()
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(
            "[KRAFT-W001] @MapUsing 'convert' on target property 'items' duplicates a mapping Kraft already generates."
        )
        assertThat(result.messages).contains("items = this.items.map { it.intoBar() }")
    }

    @Test
    fun `Synthetic enum converters still render as nested-style calls`() {
        val text = generatedText(
            models(
                "List<Color>", "List<ColorDto>",
                extraModels = """
                    enum class Color { RED }
                    enum class ColorDto { RED }
                    @com.blu3berry.kraft.config.MapEnum(source = Color::class, target = ColorDto::class)
                    object ColorMapping
                """
            )
        )
        assertThat(text).contains("items = this.items.map { it.toColorDto() }")
    }
}
