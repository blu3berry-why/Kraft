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

            @hu.nova.blu3berry.kraft.onclass.from.MapFrom(ItemSource::class)
            data class ItemDto(
                val name: String,
                @hu.nova.blu3berry.kraft.onclass.MapIgnore
                val quantity: Int   // non-null, no default — ignored property leaves constructor incomplete
            )
            """
        )

        val result = TestKspRunner.compile(source)

        // The KSP step itself succeeds (no KSP error), but the generated code omits 'quantity'
        // from the constructor call, making the resulting Kotlin file invalid.
        // TODO: the processor should emit a KSP error here instead of generating invalid code.
        // When that is implemented, change this to assertThat(result.messages).contains("...")
        // and remove the exitCode assertion (or keep it for double-safety).
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
    }
}
