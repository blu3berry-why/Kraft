package hu.nova.blu3berry.kraft.mapignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapIgnoreMultiplePropertiesTest {

    @Test
    fun `@MapIgnore on multiple properties omits all of them`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class OrderSource(val id: Int, val total: Double, val discount: Double, val tax: Double)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(OrderSource::class)
            data class OrderDto(
                val id: Int,
                @hu.nova.blu3berry.kraft.mapping.MapIgnore
                val total: Double = 0.0,
                @hu.nova.blu3berry.kraft.mapping.MapIgnore
                val discount: Double = 0.0,
                @hu.nova.blu3berry.kraft.mapping.MapIgnore
                val tax: Double? = null
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun OrderSource.toOrderDto()")
        assertThat(content).contains("id = this.id")
        assertThat(content).doesNotContain("total")
        assertThat(content).doesNotContain("discount")
        assertThat(content).doesNotContain("tax")
    }
}
