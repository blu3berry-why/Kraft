package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.blu3berry.kraft.containers.ContainerTestSupport.compile
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec requirement "Deprecated nested-mapping annotations keep 0.13 behaviour": `@MapNested` and
 * `nestedMappings` stay frozen to same-kind `List` / `Set`, with the old output.
 */
@OptIn(ExperimentalCompilerApi::class)
class DeprecatedNestedFreezeTest {

    @Test
    fun `Legacy explicit NestedMapping List to Set is unchanged`() {
        val result = compile(
            """
            package models

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

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).doesNotContain("KRAFT-E063")
        val text = generatedText(
            """
            package models

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
    fun `MapNested does not reach new collection types`() {
        val annotated = generatedText(
            """
            package models

            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(val items: Collection<ItemSource>)

            @com.blu3berry.kraft.mapping.MapFrom(Src::class)
            data class Tgt(
                @com.blu3berry.kraft.mapping.MapNested
                val items: Collection<ItemDto>
            )
            """.trimIndent()
        )
        val plain = generatedText(
            """
            package models

            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(val items: Collection<ItemSource>)

            @com.blu3berry.kraft.mapping.MapFrom(Src::class)
            data class Tgt(
                val items: Collection<ItemDto>
            )
            """.trimIndent()
        )

        assertThat(annotated).contains("items = this.items.map { it.toItemDto() }")
        assertThat(annotated).isEqualTo(plain)
    }

    @Test
    fun `Explicit NestedMapping does not reach a Collection source`() {
        val text = generatedText(
            """
            package models

            data class ItemSource(val id: Int)
            data class ItemDto(val id: Int)

            data class Src(val items: Array<ItemSource>)
            data class Tgt(val items: List<ItemDto>)

            @com.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Tgt::class,
                nestedMappings = [
                    com.blu3berry.kraft.config.NestedMapping(source = ItemSource::class, target = ItemDto::class)
                ]
            )
            object M
            """.trimIndent()
        )
        assertThat(text).contains("items = this.items.map { it.toItemDto() }")
    }
}
