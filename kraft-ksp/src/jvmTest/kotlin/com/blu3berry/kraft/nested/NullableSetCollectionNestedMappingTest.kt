package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class NullableSetCollectionNestedMappingTest {

    @Test
    fun `nullable Set source generates safe chained call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val label: String)
            data class ItemDto(val label: String)

            data class OrderSource(val ref: String, val items: Set<ItemSource>?)

            @com.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(val ref: String, val items: Set<ItemDto>? = null)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        assertThat(generated).isNotEmpty()
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun OrderSource.toOrderDto()")
        // Must use ?.map{...}?.toSet() — not ?.map{...}.toSet()
        assertThat(content).contains("?.toSet()")
        assertThat(content).doesNotContain("}.toSet()")
    }
}
