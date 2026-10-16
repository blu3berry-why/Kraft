package com.blu3berry.kraft.containers

import com.google.common.truth.Truth.assertThat
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * Spec requirement "Nullability at the element level" for enum elements: the enum mapper is
 * registered for the non-null pair and serves nullable elements, as a real converter does
 * (PR #120 review).
 */
@OptIn(ExperimentalCompilerApi::class)
class NullableEnumElementTest {

    private val enums = """
        enum class StatusDto { OPEN, CLOSED }
        enum class Status { OPEN, CLOSED }
    """

    @Test
    fun `Nullable enum elements kept`() {
        assertThat(generatedText(models("List<StatusDto?>", "List<Status?>", name = "states", extraModels = enums)))
            .contains("states = this.states.map { it?.toStatus() }")
    }

    @Test
    fun `Nullable enum elements dropped`() {
        assertThat(generatedText(models("List<StatusDto?>", "List<Status>", name = "states", extraModels = enums)))
            .contains("states = this.states.mapNotNull { it?.toStatus() }")
    }

    @Test
    fun `Nullable enum map values kept`() {
        assertThat(generatedText(models("Map<String, StatusDto?>", "Map<String, Status?>", name = "m", extraModels = enums)))
            .contains("m = this.m.mapValues { it.value?.toStatus() }")
    }
}
