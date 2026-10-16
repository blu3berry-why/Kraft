package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/** Spec requirements "Nullability at the container level" and "Nullability at the element level". */
@OptIn(ExperimentalCompilerApi::class)
class ContainerNullabilityTest {

    private fun assigned(sourceType: String, targetType: String, name: String = "items"): String =
        generatedText(models(sourceType, targetType, name))

    @Test
    fun `Nullable Array into non-null List`() {
        assertThat(assigned("Array<ItemSource>?", "List<ItemDto>"))
            .contains("items = this.items?.map { it.toItemDto() } ?: emptyList()")
    }

    @Test
    fun `Nullable into nullable`() {
        assertThat(assigned("Collection<ItemSource>?", "Collection<ItemDto>?"))
            .contains("items = this.items?.map { it.toItemDto() }")
    }

    @Test
    fun `Nullable elements kept`() {
        assertThat(assigned("Collection<ItemSource?>", "Collection<ItemDto?>"))
            .contains("items = this.items.map { it?.toItemDto() }")
    }

    @Test
    fun `Nullable elements dropped`() {
        assertThat(assigned("Array<ItemSource?>", "List<ItemDto>"))
            .contains("items = this.items.mapNotNull { it?.toItemDto() }")
    }

    @Test
    fun `Empty values per target kind`() {
        assertThat(assigned("List<ItemSource>?", "MutableList<ItemDto>"))
            .contains("items = this.items?.map { it.toItemDto() }?.toMutableList() ?: mutableListOf()")
        assertThat(assigned("Set<ItemSource>?", "MutableSet<ItemDto>"))
            .contains("items = this.items?.map { it.toItemDto() }?.toMutableSet() ?: mutableSetOf()")
        assertThat(assigned("List<ItemSource>?", "Array<ItemDto>"))
            .contains("items = this.items?.map { it.toItemDto() }?.toTypedArray() ?: emptyArray()")
        assertThat(assigned("List<ItemSource>?", "Iterable<ItemDto>"))
            .contains("items = this.items?.map { it.toItemDto() } ?: emptyList()")
        assertThat(assigned("Set<ItemSource>?", "Set<ItemDto>"))
            .contains("items = this.items?.map { it.toItemDto() }?.toSet() ?: emptySet()")
    }

    @Test
    fun `Nullable container with identical elements`() {
        assertThat(assigned("Array<String>?", "List<String>", name = "tags"))
            .contains("tags = this.tags?.toList() ?: emptyList()")
        assertThat(assigned("Array<String>?", "List<String>?", name = "tags"))
            .contains("tags = this.tags?.toList()")
    }
}
