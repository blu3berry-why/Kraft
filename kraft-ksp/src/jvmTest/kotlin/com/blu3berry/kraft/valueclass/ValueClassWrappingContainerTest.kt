package com.blu3berry.kraft.valueclass

import com.google.common.truth.Truth.assertThat
import com.blu3berry.kraft.containers.ContainerTestSupport.generatedText
import com.blu3berry.kraft.containers.ContainerTestSupport.models
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

/**
 * A value class that wraps a whole container is a scalar wrap/unwrap, not container mapping.
 * In 0.13 only List/Set counted as containers, so Map/Array/Collection wrappers compiled;
 * widening the container kinds must not take that away (PR #120 review).
 */
@OptIn(ExperimentalCompilerApi::class)
class ValueClassWrappingContainerTest {

    private val wrappers = """
        @kotlin.jvm.JvmInline value class Attrs(val value: Map<String, String>)
        @kotlin.jvm.JvmInline value class Tags(val value: Array<String>)
        @kotlin.jvm.JvmInline value class Names(val value: Collection<String>)
    """

    @Test
    fun `Map wrapped into a value class`() {
        assertThat(generatedText(models("Map<String, String>", "Attrs", name = "attrs", extraModels = wrappers)))
            .contains("attrs = Attrs(this.attrs)")
    }

    @Test
    fun `Map unwrapped from a value class`() {
        assertThat(generatedText(models("Attrs", "Map<String, String>", name = "attrs", extraModels = wrappers)))
            .contains("attrs = this.attrs.value")
    }

    @Test
    fun `Array wrapped and unwrapped`() {
        assertThat(generatedText(models("Array<String>", "Tags", name = "tags", extraModels = wrappers)))
            .contains("tags = Tags(this.tags)")
        assertThat(generatedText(models("Tags", "Array<String>", name = "tags", extraModels = wrappers)))
            .contains("tags = this.tags.value")
    }

    @Test
    fun `Collection wrapped into a value class`() {
        assertThat(generatedText(models("Collection<String>", "Names", name = "names", extraModels = wrappers)))
            .contains("names = Names(this.names)")
    }
}
