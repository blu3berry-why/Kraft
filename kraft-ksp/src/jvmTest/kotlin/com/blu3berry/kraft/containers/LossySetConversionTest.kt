package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.blu3berry.kraft.containers.ContainerTestSupport.compile
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec requirement "Conversions into a Set from a non-Set are refused" (KRAFT-E063). */
@OptIn(ExperimentalCompilerApi::class)
class LossySetConversionTest {

    @Test
    fun `List to Set`() {
        val result = compile(models("List<ItemSource>", "Set<ItemDto>"))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E063] Cannot map property 'items': converting List<ItemSource> to Set<ItemDto> " +
                "would drop duplicate elements."
        )
        assertThat(result.messages).contains(
            "Change the target to List<ItemDto>, or map it with @MapUsing and call .toSet() explicitly."
        )
    }

    @Test
    fun `Reverse of Set to List`() {
        val result = compile(models("Set<ItemSource>", "List<ItemDto>", configAnnotations = "@com.blu3berry.kraft.config.MapReverse"))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E063] Cannot map property 'items': converting List<ItemDto> to Set<ItemSource> " +
                "would drop duplicate elements."
        )
        // The forward mapper is fine: no E063 names it.
        assertThat(result.messages).doesNotContain("converting Set<ItemSource> to List<ItemDto>")
    }

    @Test
    fun `Set to List alone compiles`() {
        assertThat(generatedText(models("Set<ItemSource>", "List<ItemDto>")))
            .contains("items = this.items.map { it.toItemDto() }")
    }

    @Test
    fun `Other non-Set sources into MutableSet are refused too`() {
        for (source in listOf("Array<ItemSource>", "Collection<ItemSource>", "Iterable<ItemSource>", "MutableList<ItemSource>")) {
            val result = compile(models(source, "MutableSet<ItemDto>"))
            assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
            assertThat(result.messages).contains("[KRAFT-E063]")
        }
    }

    @Test
    fun `Same elements into a Set from a List is refused`() {
        val result = compile(models("List<String>", "Set<String>", name = "tags"))

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(
            "[KRAFT-E063] Cannot map property 'tags': converting List<String> to Set<String> " +
                "would drop duplicate elements."
        )
    }
}
