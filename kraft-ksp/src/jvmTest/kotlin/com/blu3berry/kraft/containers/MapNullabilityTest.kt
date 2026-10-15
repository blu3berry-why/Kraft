package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.blu3berry.kraft.containers.ContainerTestSupport.compile
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec requirement "Map nullability" (task 5.2). */
@OptIn(ExperimentalCompilerApi::class)
class MapNullabilityTest {

    private fun assigned(sourceType: String, targetType: String): String =
        generatedText(models(sourceType, targetType, name = "byId"))

    @Test
    fun `Nullable map into non-null map`() {
        assertThat(assigned("Map<String, ItemSource>?", "Map<String, ItemDto>"))
            .contains("byId = this.byId?.mapValues { it.value.toItemDto() } ?: emptyMap()")
    }

    @Test
    fun `Nullable map into non-null MutableMap`() {
        assertThat(assigned("Map<String, ItemSource>?", "MutableMap<String, ItemDto>"))
            .contains("byId = this.byId?.mapValues { it.value.toItemDto() }?.toMutableMap() ?: mutableMapOf()")
    }

    @Test
    fun `Nullable into nullable`() {
        assertThat(assigned("Map<String, ItemSource>?", "Map<String, ItemDto>?"))
            .contains("byId = this.byId?.mapValues { it.value.toItemDto() }")
    }

    @Test
    fun `Nullable map with identical values into non-null MutableMap`() {
        val text = generatedText(models("Map<String, Int>?", "MutableMap<String, Int>", name = "m", extraModels = ""))
        assertThat(text).contains("m = this.m?.toMutableMap() ?: mutableMapOf()")
    }

    @Test
    fun `Nullable values kept`() {
        assertThat(assigned("Map<String, ItemSource?>", "Map<String, ItemDto?>"))
            .contains("byId = this.byId.mapValues { it.value?.toItemDto() }")
    }

    @Test
    fun `Nullable values into non-null values`() {
        val result = compile(models("Map<String, ItemSource?>", "Map<String, ItemDto>", name = "byId"))
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("[KRAFT-E005]")
        assertThat(result.messages).contains("byId")
        assertThat(result.messages).contains(
            "Map values cannot be dropped like list elements: removing a null value would also remove its key."
        )
    }

    @Test
    fun `List mismatch does not get the map hint`() {
        val result = compile(models("List<ItemSource?>", "Map<String, ItemDto>", name = "byId"))
        assertThat(result.messages).doesNotContain("Map values cannot be dropped")
    }
}
