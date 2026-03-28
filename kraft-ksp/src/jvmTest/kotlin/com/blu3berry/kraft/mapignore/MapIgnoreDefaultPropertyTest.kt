package com.blu3berry.kraft.mapignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapIgnoreDefaultPropertyTest {

    @Test
    fun `@MapIgnore on property with default value omits it from generated constructor call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ProductSource(val name: String, val price: Double)

            @com.blu3berry.kraft.mapping.MapFrom(ProductSource::class)
            data class ProductDto(
                val name: String,
                @com.blu3berry.kraft.mapping.MapIgnore
                val price: Double = 0.0
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun ProductSource.toProductDto()")
        assertThat(content).contains("name = this.name")
        assertThat(content).doesNotContain("price")
    }
}
