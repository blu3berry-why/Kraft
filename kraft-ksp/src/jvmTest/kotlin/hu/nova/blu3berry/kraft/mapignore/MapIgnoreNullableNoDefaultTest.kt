package hu.nova.blu3berry.kraft.mapignore

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapIgnoreNullableNoDefaultTest {

    @Test
    fun `@MapIgnore on nullable property with no default emits a KSP error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val name: String, val summary: String?)

            @hu.nova.blu3berry.kraft.onclass.from.MapFrom(ItemSource::class)
            data class ItemDto(
                val name: String,
                @hu.nova.blu3berry.kraft.onclass.MapIgnore
                val summary: String?   // nullable but no default — omitting still fails compilation
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("has no default value")
    }
}
