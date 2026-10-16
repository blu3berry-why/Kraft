package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.blu3berry.kraft.containers.ContainerTestSupport.compile
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec requirement "Map values are mapped element-wise" (task 5.1). */
@OptIn(ExperimentalCompilerApi::class)
class MapValueTest {

    // Interfaces are not mappable classes, so no nested mapper competes with the converter.
    private val fooBar = """
        interface Foo { val v: Int }
        interface Bar { val v: Int }

        @com.blu3berry.kraft.config.KraftConverter
        fun Foo.intoBar(): Bar = object : Bar { override val v = this@intoBar.v }
    """

    @Test
    fun `Nested values`() {
        val text = generatedText(models("Map<String, ItemSource>", "Map<String, ItemDto>", name = "byId"))
        assertThat(text).contains("byId = this.byId.mapValues { it.value.toItemDto() }")
        // The nested mapper is discovered from the map value.
        assertThat(text).contains("fun ItemSource.toItemDto()")
    }

    @Test
    fun `Named KraftConverter on values`() {
        val text = generatedText(models("Map<String, Foo>", "Map<String, Bar>", name = "m", extraModels = fooBar))
        assertThat(text).contains("m = this.m.mapValues { it.value.intoBar() }")
    }

    @Test
    fun `MutableMap target`() {
        val text = generatedText(models("Map<String, ItemSource>", "MutableMap<String, ItemDto>", name = "byId"))
        assertThat(text).contains("byId = this.byId.mapValues { it.value.toItemDto() }.toMutableMap()")
    }

    @Test
    fun `Same types, MutableMap target`() {
        val text = generatedText(models("Map<String, Int>", "MutableMap<String, Int>", name = "m", extraModels = ""))
        assertThat(text).contains("m = this.m.toMutableMap()")
    }

    @Test
    fun `Map and non-map`() {
        val result = compile(models("Map<String, ItemSource>", "List<ItemDto>"))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).contains("items")
    }

    @Test
    fun `Non-map and map`() {
        val result = compile(models("List<ItemSource>", "Map<String, ItemDto>"))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
    }

    @Test
    fun `Enum and value-class values`() {
        val enums = """
            enum class Color { RED }
            enum class ColorDto { RED }
            @JvmInline value class UserId(val value: String)
        """
        assertThat(generatedText(models("Map<String, Color>", "Map<String, ColorDto>", name = "m", extraModels = enums)))
            .contains("m = this.m.mapValues { it.value.toColorDto() }")
        assertThat(generatedText(models("Map<String, String>", "Map<String, UserId>", name = "m", extraModels = enums)))
            .contains("m = this.m.mapValues { UserId(it.value) }")
        assertThat(generatedText(models("Map<String, UserId>", "Map<String, String>", name = "m", extraModels = enums)))
            .contains("m = this.m.mapValues { it.value.value }")
    }

    @Test
    fun `MutableMap source into Map target is a plain assignment`() {
        val text = generatedText(models("MutableMap<String, Int>", "Map<String, Int>", name = "m", extraModels = ""))
        assertThat(text).contains("m = this.m")
        assertThat(text).doesNotContain("toMutableMap")
    }

    @Test
    fun `String to Int keys have no key operation and are E005`() {
        val result = compile(
            models(
                "Map<String, ItemSource>", "Map<Int, ItemDto>", name = "m"
            )
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
    }

    @Test
    fun `Hand-written MapUsing that duplicates a map-values mapping gets W001`() {
        val result = compile(
            """
            package models

            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(val byId: Map<String, ItemSource>)
            data class Tgt(val byId: Map<String, ItemDto>)

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Tgt::class)
            object M {
                @com.blu3berry.kraft.config.MapUsing(source = "byId", target = "byId")
                fun convert(byId: Map<String, ItemSource>): Map<String, ItemDto> =
                    byId.mapValues { ItemDto(it.value.id) }
            }
            """.trimIndent()
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains(
            "[KRAFT-W001] @MapUsing 'convert' on target property 'byId' duplicates a mapping Kraft already generates."
        )
    }
}
