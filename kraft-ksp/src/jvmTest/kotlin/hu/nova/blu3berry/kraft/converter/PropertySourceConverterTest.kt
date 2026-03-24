package hu.nova.blu3berry.kraft.converter

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class PropertySourceConverterTest {

    @Test
    fun `property-source regular function in object generates object call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val count: Int)
            data class Dst(val label: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @hu.nova.blu3berry.kraft.config.MapUsing(source = "count", target = "label")
                fun convert(v: Int): String = v.toString()
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)

        assertThat(generated.any { it.readText().contains("label = SrcMapper.convert(this.count)") }).isTrue()
    }

    @Test
    fun `property-source extension function in object generates with-block call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val count: Int)
            data class Dst(val label: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @hu.nova.blu3berry.kraft.config.MapUsing(source = "count", target = "label")
                fun Int.toLabel(): String = this.toString()
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)

        assertThat(generated.any { it.readText().contains("label = with(SrcMapper) { this@toDst.count.toLabel() }") }).isTrue()
    }

    @Test
    fun `multiple property-source converters in same object all apply`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val a: Int, val b: Int)
            data class Dst(val x: String, val y: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @hu.nova.blu3berry.kraft.config.MapUsing(source = "a", target = "x")
                fun convertA(v: Int): String = "a:${'$'}v"

                @hu.nova.blu3berry.kraft.config.MapUsing(source = "b", target = "y")
                fun convertB(v: Int): String = "b:${'$'}v"
            }
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)

        assertThat(generated.any { it.readText().contains("x = SrcMapper.convertA(this.a)") }).isTrue()
        assertThat(generated.any { it.readText().contains("y = SrcMapper.convertB(this.b)") }).isTrue()
    }
}
