package hu.nova.blu3berry.kraft.mapignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapIgnoreNonNullNoDefaultTest {

    @Test
    fun `@MapIgnore on non-null property with no default produces a compilation error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val name: String, val quantity: Int)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(ItemSource::class)
            data class ItemDto(
                val name: String,
                @hu.nova.blu3berry.kraft.mapping.MapIgnore
                val quantity: Int   // non-null, no default — ignored property leaves constructor incomplete
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("has no default value")
    }
}
