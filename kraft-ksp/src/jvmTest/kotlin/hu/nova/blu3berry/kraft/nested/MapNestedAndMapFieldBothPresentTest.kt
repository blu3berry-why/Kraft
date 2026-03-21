package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class MapNestedAndMapFieldBothPresentTest {

    @Test
    fun `@MapNested and @MapField on same property emits warning but compiles`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val homeAddress: AddressSource)

            data class AddressDto(val street: String, val city: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(
                val name: String,
                @hu.nova.blu3berry.kraft.mapping.MapNested(sourceName = "homeAddress")
                @hu.nova.blu3berry.kraft.mapping.MapField(otherName = "homeAddress")
                val address: AddressDto
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        assertThat(result.messages).contains("@MapNested takes precedence")
    }
}
