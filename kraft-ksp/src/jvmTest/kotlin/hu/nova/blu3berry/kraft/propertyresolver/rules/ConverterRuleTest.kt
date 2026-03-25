package hu.nova.blu3berry.kraft.propertyresolver.rules

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ConverterRuleTest {

    @Test
    fun `match - property-source converter generates converter call in output`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class EventSource(val durationMs: Long)
            data class EventDto(val duration: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = EventSource::class,
                target = EventDto::class
            )
            object EventMapper {
                @hu.nova.blu3berry.kraft.config.MapUsing(source = "durationMs", target = "duration")
                fun formatDuration(ms: Long): String = "${'$'}{ms}ms"
            }
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }

        assertThat(content).contains("duration = EventMapper.formatDuration(this.durationMs)")
    }

    @Test
    fun `match - whole-object converter generates call without source property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class CoordSource(val lat: Double, val lon: Double)
            data class CoordDto(val label: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = CoordSource::class,
                target = CoordDto::class
            )
            object CoordMapper {
                @hu.nova.blu3berry.kraft.config.MapUsing(target = "label")
                fun format(src: CoordSource): String = "${'$'}{src.lat},${'$'}{src.lon}"
            }
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }

        assertThat(content).contains("label = CoordMapper.format(this)")
    }

    @Test
    fun `error - multiple converters targeting the same property emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val value: Int)
            data class Dst(val text: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @hu.nova.blu3berry.kraft.config.MapUsing(source = "value", target = "text")
                fun convertFirst(v: Int): String = v.toString()

                @hu.nova.blu3berry.kraft.config.MapUsing(source = "value", target = "text")
                fun convertSecond(v: Int): String = "duplicate"
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Multiple")
    }

    @Test
    fun `error - converter referencing unknown source property emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Src(val value: Int)
            data class Dst(val text: String)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = Src::class,
                target = Dst::class
            )
            object SrcMapper {
                @hu.nova.blu3berry.kraft.config.MapUsing(source = "ghost", target = "text")
                fun convert(v: Int): String = v.toString()
            }
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("ghost")
    }
}
