package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ConfigNestedCollectionMappingTest {

    @Test
    fun `explicit @NestedMapping with List collection property generates map call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val label: String)
            data class OrderSource(val ref: String, val items: List<ItemSource>)

            data class ItemDto(val label: String)
            data class OrderDto(val ref: String, val items: List<ItemDto>)

            @com.blu3berry.kraft.config.MapConfig(
                source = OrderSource::class,
                target = OrderDto::class,
                nestedMappings = [
                    com.blu3berry.kraft.config.NestedMapping(
                        source = ItemSource::class,
                        target = ItemDto::class
                    )
                ]
            )
            object OrderMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        assertThat(generated).isNotEmpty()
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun OrderSource.toOrderDto()")
        assertThat(content).contains("fun ItemSource.toItemDto()")
        assertThat(content).contains("items = this.items.map { it.toItemDto() }")
    }
}
