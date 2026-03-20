package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class UnresolvableChildPropertyTest {

    @Test
    fun `auto-detected child with unresolvable non-null property emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val address: AddressSource)

            // 'city' renamed to 'zipCode' — no source match
            data class AddressDto(val street: String, val zipCode: String)

            @hu.nova.blu3berry.kraft.onclass.from.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val address: AddressDto)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("has no mapping source and is non-nullable")
    }
}
