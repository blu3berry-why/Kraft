package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)

class SetCollectionNestedMappingTest {

    @Test
    fun `auto-detection generates child mapper for Set property with different element types`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val id: Int, val label: String)
            data class OrderSource(val ref: String, val items: Set<ItemSource>)

            data class ItemDto(val id: Int, val label: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(val ref: String, val items: Set<ItemDto>)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun OrderSource.toOrderDto()")
        assertThat(content).contains("fun ItemSource.toItemDto()")
        assertThat(content).contains("items = this.items.map { it.toItemDto() }.toSet()")
    }

    @Test
    fun `@MapNested explicit annotation generates Set collection mapper`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class TagSource(val value: String)
            data class ArticleSource(val title: String, val tags: Set<TagSource>)

            data class TagDto(val value: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(ArticleSource::class)
            data class ArticleDto(
                val title: String,
                @hu.nova.blu3berry.kraft.mapping.MapNested
                val tags: Set<TagDto>
            )
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun ArticleSource.toArticleDto()")
        assertThat(content).contains("fun TagSource.toTagDto()")
        assertThat(content).contains("tags = this.tags.map { it.toTagDto() }.toSet()")
    }

    @Test
    fun `mismatched collection kinds cause compilation error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Item(val id: Int)
            data class OrderSource(val ref: String, val items: List<Item>)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(val ref: String, val items: Set<Item>)
            """
        )

        val result = TestKspRunner.compile(source)

        // List<Item> → Set<Item> is a type mismatch — generated code won't compile
        assertThat(result.exitCode).isNotEqualTo(KotlinCompilation.ExitCode.OK)
    }
}
