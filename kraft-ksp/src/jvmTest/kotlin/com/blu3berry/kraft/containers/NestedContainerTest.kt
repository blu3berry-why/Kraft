package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import com.blu3berry.kraft.containers.ContainerTestSupport.compile
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec requirement "Nested collections": recursion to any depth, `e<depth>` lambda names. */
@OptIn(ExperimentalCompilerApi::class)
class NestedContainerTest {

    @Test
    fun `List of lists`() {
        val text = generatedText(models("List<List<ItemSource>>", "List<List<ItemDto>>", name = "grid"))
        assertThat(text).contains("grid = this.grid.map { e0 -> e0.map { it.toItemDto() } }")
        // The element mapper for the inner class is generated.
        assertThat(text).contains("fun ItemSource.toItemDto()")
    }

    @Test
    fun `Inner nullable list into non-null list`() {
        val text = generatedText(models("List<List<ItemSource>?>", "List<List<ItemDto>>", name = "grid"))
        assertThat(text).contains("grid = this.grid.map { e0 -> e0?.map { it.toItemDto() } ?: emptyList() }")
    }

    @Test
    fun `Three levels use e0 and e1 and keep it for the innermost`() {
        val text = generatedText(
            models("List<List<List<ItemSource>>>", "List<List<List<ItemDto>>>", name = "cube")
        )
        assertThat(text).contains("cube = this.cube.map { e0 -> e0.map { e1 -> e1.map { it.toItemDto() } } }")
    }

    @Test
    fun `Inner container kind conversion`() {
        val text = generatedText(models("List<Array<ItemSource>>", "List<List<ItemDto>>", name = "grid"))
        assertThat(text).contains("grid = this.grid.map { e0 -> e0.map { it.toItemDto() } }")
    }

    @Test
    fun `Inner identity elements need only the conversion`() {
        val text = generatedText(models("List<Set<String>>", "List<List<String>>", name = "grid"))
        assertThat(text).contains("grid = this.grid.map { e0 -> e0.toList() }")
    }

    @Test
    fun `Enums inside nested lists are derived`() {
        val text = generatedText(
            models(
                "List<List<StatusDto>>",
                "List<List<Status>>",
                name = "grid",
                extraModels = """
                    enum class StatusDto { ACTIVE, DONE }
                    enum class Status { ACTIVE, DONE }
                """
            )
        )
        assertThat(text).contains("grid = this.grid.map { e0 -> e0.map { it.toStatus() } }")
        assertThat(text).contains("fun StatusDto.toStatus()")
    }

    @Test
    fun `Lossy conversion at an inner level`() {
        val result = compile(models("List<List<ItemSource>>", "List<Set<ItemDto>>", name = "grid"))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E063] Cannot map property 'grid': converting List<ItemSource> to Set<ItemDto> " +
                "would drop duplicate elements."
        )
        assertThat(result.messages).doesNotContain("[KRAFT-E005]")
    }

    @Test
    fun `No shadowing warnings`() {
        val result = TestKspRunner.compile(
            SourceFile.kotlin("Models.kt", models("List<List<ItemSource>>", "List<List<ItemDto>>", name = "grid")),
            warningsAsErrors = true
        )
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }
}
