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

/** Spec requirement "Maps nest with collections" (tasks 7.1 and 7.2). */
@OptIn(ExperimentalCompilerApi::class)
class MapNestingTest {

    private val enums = """
        data class ItemSource(val id: Int)
        data class ItemDto(val id: Int)
        enum class StatusDto { OPEN, CLOSED }
        enum class Status { OPEN, CLOSED }
    """

    @Test
    fun `Map of lists`() {
        val text = generatedText(models("Map<String, List<ItemSource>>", "Map<String, List<ItemDto>>", name = "groups"))
        assertThat(text).contains("groups = this.groups.mapValues { e0 -> e0.value.map { it.toItemDto() } }")
    }

    @Test
    fun `List of maps`() {
        val text = generatedText(
            models("List<Map<String, ItemSource>>", "List<Map<String, ItemDto>>", name = "pages")
        )
        assertThat(text).contains("pages = this.pages.map { e0 -> e0.mapValues { it.value.toItemDto() } }")
    }

    @Test
    fun `Lossy conversion inside a map value`() {
        val result = compile(models("Map<String, List<ItemSource>>", "Map<String, Set<ItemDto>>", name = "groups"))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E063] Cannot map property 'groups': converting List<ItemSource> to Set<ItemDto> " +
                "would drop duplicate elements."
        )
        assertThat(result.messages).doesNotContain("[KRAFT-E005]")
    }

    @Test
    fun `Enum map keys and list values discover both pairs`() {
        val source = models(
            "Map<StatusDto, List<ItemSource>>", "Map<Status, List<ItemDto>>", name = "groups", extraModels = enums
        )
        val text = generatedText(source)
        // KotlinPoet wraps the long line; compare with whitespace collapsed.
        assertThat(text.replace(Regex("""\s+"""), " ")).contains(
            "groups = this.groups.entries.associate { e0 -> e0.key.toStatus() to e0.value.map { it.toItemDto() } }"
        )
        assertThat(text).contains("fun StatusDto.toStatus()")
        assertThat(text).contains("fun ItemSource.toItemDto()")

        // No lambda parameter is shadowed.
        val strict = TestKspRunner.compile(SourceFile.kotlin("Models.kt", source), warningsAsErrors = true)
        assertThat(strict.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }
}
