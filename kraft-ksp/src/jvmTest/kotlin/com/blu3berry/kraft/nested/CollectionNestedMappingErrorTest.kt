package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class CollectionNestedMappingErrorTest {

    @Test
    fun `@MapNested on List property whose element type is an interface emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class SourceItem(val id: Int)
            data class OrderSource(val ref: String, val items: List<SourceItem>)

            interface ItemInterface { val id: Int }

            @com.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(
                val ref: String,
                @com.blu3berry.kraft.mapping.MapNested
                val items: List<ItemInterface>
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("is not a concrete class")
    }
}
