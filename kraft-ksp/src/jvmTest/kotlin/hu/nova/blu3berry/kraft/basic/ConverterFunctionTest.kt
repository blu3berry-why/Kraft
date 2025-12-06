package hu.nova.blu3berry.kraft.basic

import com.tschuchort.compiletesting.SourceFile
import com.google.common.truth.Truth.assertThat
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class ConverterFunctionTest {

    @Test
    fun `custom converter function is applied`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val int: Int)
            data class Dst(val text: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                from = Src::class,
                to = Dst::class
            )
            object MyMapper {
                @hu.nova.blu3berry.kraft.MapUsing(
                    from = "int",
                    to   = "text"
                )
                fun convert(v: Int): String = "Number: " + v
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val text = generated.first().readText()

        assertThat(text).contains("text = MyMapper.convert(this.int)")
    }
}
