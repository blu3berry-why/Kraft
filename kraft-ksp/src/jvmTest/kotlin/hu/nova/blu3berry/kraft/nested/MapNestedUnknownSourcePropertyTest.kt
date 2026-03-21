package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapNestedUnknownSourcePropertyTest {

    @Test
    fun `@MapNested with unknown sourceName emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val address: AddressSource)

            data class AddressDto(val street: String, val city: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(
                val name: String,
                @hu.nova.blu3berry.kraft.mapping.MapNested(sourceName = "typo")
                val address: AddressDto
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("source property 'typo' does not exist")
    }
}
