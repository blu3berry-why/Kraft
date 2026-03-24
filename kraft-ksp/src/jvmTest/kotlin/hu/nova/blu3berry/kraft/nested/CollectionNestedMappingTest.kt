package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class CollectionNestedMappingTest {

    @Test
    fun `auto-detection generates child mapper for List property with different element types`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val id: Int, val label: String)
            data class OrderSource(val ref: String, val items: List<ItemSource>)

            data class ItemDto(val id: Int, val label: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(val ref: String, val items: List<ItemDto>)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun OrderSource.toOrderDto()")
        assertThat(content).contains("fun ItemSource.toItemDto()")
        assertThat(content).contains("items = this.items.map { it.toItemDto() }")
    }

    @Test
    fun `@MapNested explicit annotation generates collection mapper`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class TagSource(val value: String)
            data class ArticleSource(val title: String, val tags: List<TagSource>)

            data class TagDto(val value: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(ArticleSource::class)
            data class ArticleDto(
                val title: String,
                @hu.nova.blu3berry.kraft.mapping.MapNested
                val tags: List<TagDto>
            )
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun ArticleSource.toArticleDto()")
        assertThat(content).contains("fun TagSource.toTagDto()")
        assertThat(content).contains("tags = this.tags.map { it.toTagDto() }")
    }

    @Test
    fun `auto-detection does not generate child mapper for List property with identical element types`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Item(val id: Int)
            data class OrderSource(val ref: String, val items: List<Item>)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(val ref: String, val items: List<Item>)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun OrderSource.toOrderDto()")
        assertThat(content).doesNotContain("fun Item.toItem()")
        assertThat(content).doesNotContain("this.items.map { it.toItem() }")
        assertThat(content).contains("items = this.items")
    }

    @Test
    fun `auto-detection handles multiple List properties independently`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AuthorSource(val name: String)
            data class CommentSource(val text: String)
            data class PostSource(val title: String, val authors: List<AuthorSource>, val comments: List<CommentSource>)

            data class AuthorDto(val name: String)
            data class CommentDto(val text: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PostSource::class)
            data class PostDto(val title: String, val authors: List<AuthorDto>, val comments: List<CommentDto>)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun PostSource.toPostDto()")
        assertThat(content).contains("fun AuthorSource.toAuthorDto()")
        assertThat(content).contains("fun CommentSource.toCommentDto()")
        assertThat(content).contains("authors = this.authors.map { it.toAuthorDto() }")
        assertThat(content).contains("comments = this.comments.map { it.toCommentDto() }")
    }
}
