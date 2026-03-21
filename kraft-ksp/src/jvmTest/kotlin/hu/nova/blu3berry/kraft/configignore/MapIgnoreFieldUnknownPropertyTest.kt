package hu.nova.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapIgnoreFieldUnknownPropertyTest {

    @Test
    fun `MapIgnoreField with TARGET direction and unknown property name emits a KSP error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Item(val id: Int, val name: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = Item::class,
                to   = ItemDto::class,
                ignoredMappings = [
                    hu.nova.blu3berry.kraft.config.MapIgnoreField(
                        "nonExistent",
                        direction = hu.nova.blu3berry.kraft.config.IgnoreSide.TARGET
                    )
                ]
            )
            object ItemMapper

            data class ItemDto(val id: Int, val name: String)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("nonExistent")
        assertThat(result.messages).contains("not found")
    }

    @Test
    fun `MapIgnoreField with BOTH direction and name absent from forward target is silently skipped`() {
        // BOTH is designed for bidirectional use. A name not present in the forward target
        // is not an error — it may be valid for the reverse target once that is added.
        // The forward mapper must compile cleanly and map all resolvable properties.
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Item(val id: Int, val name: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = Item::class,
                to   = ItemDto::class,
                ignoredMappings = [
                    hu.nova.blu3berry.kraft.config.MapIgnoreField("futureReverseOnly")
                ]
            )
            object ItemMapper

            data class ItemDto(val id: Int, val name: String)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun Item.toItemDto()")
        assertThat(content).contains("id = this.id")
        assertThat(content).contains("name = this.name")
    }
}
