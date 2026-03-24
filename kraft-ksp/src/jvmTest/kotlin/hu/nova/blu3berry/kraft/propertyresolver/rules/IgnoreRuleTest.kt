package hu.nova.blu3berry.kraft.propertyresolver.rules

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class IgnoreRuleTest {

    @Test
    fun `match - @MapIgnore on property with default omits it from generated code`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ProductSource(val name: String, val internalCode: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(ProductSource::class)
            data class ProductDto(
                val name: String,
                @hu.nova.blu3berry.kraft.mapping.MapIgnore
                val internalCode: String = "n/a"
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("name = this.name")
        assertThat(content).doesNotContain("internalCode")
    }

    @Test
    fun `no-match - property without @MapIgnore is mapped normally`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val title: String, val price: Double)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(ItemSource::class)
            data class ItemDto(val title: String, val price: Double)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("title = this.title")
        assertThat(content).contains("price = this.price")
    }

    @Test
    fun `error - @MapIgnore on non-null property with no default emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val title: String, val quantity: Int)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(ItemSource::class)
            data class ItemDto(
                val title: String,
                @hu.nova.blu3berry.kraft.mapping.MapIgnore
                val quantity: Int
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("has no default value")
    }
}
