package com.blu3berry.kraft.configignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapIgnoreFieldMultipleEntriesTest {

    @Test
    fun `multiple MapIgnoreField entries each suppress their respective property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Product(val id: Int, val name: String, val cost: Double, val tax: Double, val fee: Double)

            @com.blu3berry.kraft.config.MapConfig(
                source = Product::class,
                target   = ProductDto::class,
                ignoredMappings = [
                    com.blu3berry.kraft.config.MapIgnoreField("cost"),
                    com.blu3berry.kraft.config.MapIgnoreField("tax"),
                    com.blu3berry.kraft.config.MapIgnoreField("fee")
                ]
            )
            object ProductMapper

            data class ProductDto(
                val id: Int,
                val name: String,
                val cost: Double = 0.0,
                val tax: Double = 0.0,
                val fee: Double = 0.0
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun Product.toProductDto()")
        assertThat(content).contains("id = this.id")
        assertThat(content).contains("name = this.name")
        assertThat(content).doesNotContain("cost")
        assertThat(content).doesNotContain("tax")
        assertThat(content).doesNotContain("fee")
    }
}
