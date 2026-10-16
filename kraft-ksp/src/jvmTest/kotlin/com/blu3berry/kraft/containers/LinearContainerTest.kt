package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.blu3berry.kraft.containers.ContainerTestSupport.compile
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec requirements "Supported collection types" and "Lossless conversions between collection
 * types" (openspec change `map-and-collection-mapping`): every generated line is asserted
 * verbatim, and every output is compiled.
 */
@OptIn(ExperimentalCompilerApi::class)
class LinearContainerTest {

    private fun assigned(sourceType: String, targetType: String): String =
        generatedText(models(sourceType, targetType))

    @Test
    fun `Collection to Collection`() {
        assertThat(assigned("Collection<ItemSource>", "Collection<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }")
    }

    @Test
    fun `Array to Array`() {
        assertThat(assigned("Array<ItemSource>", "Array<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toTypedArray()")
    }

    @Test
    fun `MutableList to MutableList`() {
        assertThat(assigned("MutableList<ItemSource>", "MutableList<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toMutableList()")
    }

    @Test
    fun `Existing List and Set output is unchanged`() {
        assertThat(assigned("List<ItemSource>", "List<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }")
        assertThat(assigned("Set<ItemSource>", "Set<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toSet()")
    }

    @Test
    fun `MutableSet and MutableCollection and Iterable targets`() {
        assertThat(assigned("MutableSet<ItemSource>", "MutableSet<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toMutableSet()")
        assertThat(assigned("MutableCollection<ItemSource>", "MutableCollection<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toMutableList()")
        assertThat(assigned("Iterable<ItemSource>", "Iterable<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }")
    }

    @Test
    fun `Set into MutableSet and back keeps every element`() {
        assertThat(assigned("Set<ItemSource>", "MutableSet<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toMutableSet()")
        assertThat(assigned("MutableSet<ItemSource>", "Set<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toSet()")
    }

    @Test
    fun `Array to List`() {
        assertThat(assigned("Array<ItemSource>", "List<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }")
    }

    @Test
    fun `Set to List`() {
        assertThat(assigned("Set<ItemSource>", "List<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }")
    }

    @Test
    fun `Iterable to MutableCollection`() {
        assertThat(assigned("Iterable<ItemSource>", "MutableCollection<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toMutableList()")
    }

    @Test
    fun `List into Array`() {
        assertThat(assigned("List<ItemSource>", "Array<ItemDto>"))
            .contains("items = this.items.map { it.toItemDto() }.toTypedArray()")
    }

    @Test
    fun `Same elements, different collection type`() {
        val text = generatedText(models("Array<String>", "List<String>", name = "tags"))
        assertThat(text).contains("tags = this.tags.toList()")
    }

    @Test
    fun `Same elements, copying conversions`() {
        assertThat(generatedText(models("List<String>", "MutableList<String>", name = "tags")))
            .contains("tags = this.tags.toMutableList()")
        assertThat(generatedText(models("List<String>", "Array<String>", name = "tags")))
            .contains("tags = this.tags.toTypedArray()")
        assertThat(generatedText(models("Set<String>", "MutableSet<String>", name = "tags")))
            .contains("tags = this.tags.toMutableSet()")
        assertThat(generatedText(models("Iterable<String>", "Array<String>", name = "tags")))
            .contains("tags = this.tags.toList().toTypedArray()")
    }

    @Test
    fun `Target already a supertype`() {
        val text = generatedText(models("List<String>", "Collection<String>", name = "tags"))
        assertThat(text).contains("tags = this.tags")
        assertThat(text).doesNotContain("this.tags.to")
    }

    @Test
    fun `Interface element type`() {
        val result = compile(
            """
            package models

            interface Shape
            data class ShapeDto(val id: Int)

            data class Src(val items: List<ShapeDto>)

            @com.blu3berry.kraft.mapping.MapFrom(Src::class)
            data class Tgt(
                @com.blu3berry.kraft.mapping.MapNested
                val items: List<Shape>
            )
            """.trimIndent()
        )

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E015]")
        assertThat(result.messages).contains("type 'Shape' is not a concrete class")
        assertThat(result.messages).doesNotContain("non-List collections are not supported")
    }

    @Test
    fun `Value class elements in an Array`() {
        val text = generatedText(
            models(
                "Array<String>", "List<UserId>", name = "ids",
                extraModels = "@JvmInline\nvalue class UserId(val value: String)"
            )
        )
        assertThat(text).contains("ids = this.ids.map { UserId(it) }")
    }

    @Test
    fun `Renamed Collection`() {
        val text = generatedText(
            """
            package models

            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(val rows: Array<ItemSource>)

            @com.blu3berry.kraft.mapping.MapFrom(Src::class)
            data class Tgt(
                @com.blu3berry.kraft.mapping.MapField(counterPartName = "rows")
                val items: Collection<ItemDto>
            )
            """.trimIndent()
        )
        assertThat(text).contains("items = this.rows.map { it.toItemDto() }")
    }

    @Test
    fun `No element operation`() {
        val result = compile(models("Collection<Int>", "Collection<String>", extraModels = ""))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
    }
}
