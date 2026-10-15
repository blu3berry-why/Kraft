package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Pins the `List` / `Set` assignment lines Kraft generated before the recursive container
 * model (openspec change `map-and-collection-mapping`, spec scenario "Existing List and Set
 * output is unchanged"). Every line here was captured from the pre-refactor generator, so a
 * failure means generated output changed, not that the test is stale.
 */
@OptIn(ExperimentalCompilerApi::class)
class ContainerRegressionPinTest {

    private fun generatedText(models: String): String {
        val source = SourceFile.kotlin("Models.kt", "package models\n\n$models")
        return TestKspRunner.compileAndReturnGenerated(source).joinToString("\n") { it.readText() }
    }

    @Test
    fun `nested element mappers in List and Set, with container and element nullability`() {
        val text = generatedText(
            """
            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(
                val list: List<ItemSource>,
                val itemSet: Set<ItemSource>,
                val nullableList: List<ItemSource>?,
                val nullableSet: Set<ItemSource>?,
                val nullableListKept: List<ItemSource>?,
                val nullableSetKept: Set<ItemSource>?,
                val listOfNullable: List<ItemSource?>,
                val setOfNullable: Set<ItemSource?>,
                val listOfNullableKept: List<ItemSource?>,
            )
            data class Tgt(
                val list: List<ItemDto>,
                val itemSet: Set<ItemDto>,
                val nullableList: List<ItemDto>,
                val nullableSet: Set<ItemDto>,
                val nullableListKept: List<ItemDto>?,
                val nullableSetKept: Set<ItemDto>?,
                val listOfNullable: List<ItemDto>,
                val setOfNullable: Set<ItemDto>,
                val listOfNullableKept: List<ItemDto?>,
            )

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Tgt::class)
            object M
            """.trimIndent()
        )

        assertThat(text).contains("list = this.list.map { it.toItemDto() },")
        assertThat(text).contains("itemSet = this.itemSet.map { it.toItemDto() }.toSet(),")
        assertThat(text).contains("nullableList = this.nullableList?.map { it.toItemDto() } ?: emptyList(),")
        assertThat(text).contains("nullableSet = this.nullableSet?.map { it.toItemDto() }?.toSet() ?: emptySet(),")
        assertThat(text).contains("nullableListKept = this.nullableListKept?.map { it.toItemDto() },")
        assertThat(text).contains("nullableSetKept = this.nullableSetKept?.map { it.toItemDto() }?.toSet(),")
        assertThat(text).contains("listOfNullable = this.listOfNullable.mapNotNull { it?.toItemDto() },")
        assertThat(text).contains("setOfNullable = this.setOfNullable.mapNotNull { it?.toItemDto() }.toSet(),")
        assertThat(text).contains("listOfNullableKept = this.listOfNullableKept.map { it?.toItemDto() }")
    }

    @Test
    fun `auto-derived enum mapper applied element-wise in List and Set`() {
        val text = generatedText(
            """
            enum class StatusDto { OPEN, CLOSED }
            enum class Status { OPEN, CLOSED }

            data class Src(
                val list: List<StatusDto>,
                val itemSet: Set<StatusDto>,
                val nullableList: List<StatusDto>?,
                val nullableSet: Set<StatusDto>?,
            )
            data class Tgt(
                val list: List<Status>,
                val itemSet: Set<Status>,
                val nullableList: List<Status>,
                val nullableSet: Set<Status>?,
            )

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Tgt::class)
            object M
            """.trimIndent()
        )

        assertThat(text).contains("list = this.list.map { it.toStatus() },")
        assertThat(text).contains("itemSet = this.itemSet.map { it.toStatus() }.toSet(),")
        assertThat(text).contains("nullableList = this.nullableList?.map { it.toStatus() } ?: emptyList(),")
        assertThat(text).contains("nullableSet = this.nullableSet?.map { it.toStatus() }?.toSet()")
    }

    @Test
    fun `value class wrap and unwrap applied element-wise in List and Set`() {
        val text = generatedText(
            """
            @JvmInline
            value class UserId(val value: String)

            data class Src(
                val wrapList: List<String>,
                val wrapSet: Set<String>,
                val wrapNullable: Set<String>?,
                val unwrapList: List<UserId>,
                val unwrapSet: Set<UserId>,
                val unwrapNullable: List<UserId>?,
            )
            data class Tgt(
                val wrapList: List<UserId>,
                val wrapSet: Set<UserId>,
                val wrapNullable: Set<UserId>?,
                val unwrapList: List<String>,
                val unwrapSet: Set<String>,
                val unwrapNullable: List<String>?,
            )

            @com.blu3berry.kraft.config.MapConfig(source = Src::class, target = Tgt::class)
            object M
            """.trimIndent()
        )

        assertThat(text).contains("wrapList = this.wrapList.map { UserId(it) },")
        assertThat(text).contains("wrapSet = this.wrapSet.map { UserId(it) }.toSet(),")
        assertThat(text).contains("wrapNullable = this.wrapNullable?.map { UserId(it) }?.toSet(),")
        assertThat(text).contains("unwrapList = this.unwrapList.map { it.value },")
        assertThat(text).contains("unwrapSet = this.unwrapSet.map { it.value }.toSet(),")
        assertThat(text).contains("unwrapNullable = this.unwrapNullable?.map { it.value }")
    }

    @Test
    fun `explicit NestedMapping with collection targets, with and without a field override`() {
        val text = generatedText(
            """
            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(val items: Set<ItemSource>, val legacyRows: List<ItemSource>?)
            data class Tgt(val items: Set<ItemDto>, val rows: List<ItemDto>)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Tgt::class,
                fieldMappings = [com.blu3berry.kraft.config.FieldMapping(source = "legacyRows", target = "rows")],
                nestedMappings = [
                    com.blu3berry.kraft.config.NestedMapping(source = ItemSource::class, target = ItemDto::class)
                ]
            )
            object M
            """.trimIndent()
        )

        assertThat(text).contains("items = this.items.map { it.toItemDto() }.toSet(),")
        assertThat(text).contains("rows = this.legacyRows?.map { it.toItemDto() } ?: emptyList()")
    }

    /**
     * The explicit `@NestedMapping` + field-override path takes the collection kind from the
     * target alone, so a `List` source into a `Set` target compiles today. Spec requirement
     * "Conversions into a Set from a non-Set are refused" (KRAFT-E063) is meant to change
     * this; until then, the pin keeps the refactor from changing it silently.
     */
    @Test
    fun `explicit NestedMapping with a field override converts List into Set`() {
        val text = generatedText(
            """
            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(val legacyRows: List<ItemSource>)
            data class Tgt(val rows: Set<ItemDto>)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Tgt::class,
                fieldMappings = [com.blu3berry.kraft.config.FieldMapping(source = "legacyRows", target = "rows")],
                nestedMappings = [
                    com.blu3berry.kraft.config.NestedMapping(source = ItemSource::class, target = ItemDto::class)
                ]
            )
            object M
            """.trimIndent()
        )

        assertThat(text).contains("rows = this.legacyRows.map { it.toItemDto() }.toSet()")
    }

    @Test
    fun `MapNested on List and Set properties`() {
        val text = generatedText(
            """
            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(val rows: List<ItemSource>, val tags: Set<ItemSource>?)

            @com.blu3berry.kraft.mapping.MapFrom(Src::class)
            data class Tgt(
                @com.blu3berry.kraft.mapping.MapNested(sourceName = "rows")
                val items: List<ItemDto>,
                @com.blu3berry.kraft.mapping.MapNested
                val tags: Set<ItemDto>,
            )
            """.trimIndent()
        )

        assertThat(text).contains("items = this.rows.map { it.toItemDto() },")
        assertThat(text).contains("tags = this.tags?.map { it.toItemDto() }?.toSet() ?: emptySet()")
    }
}
