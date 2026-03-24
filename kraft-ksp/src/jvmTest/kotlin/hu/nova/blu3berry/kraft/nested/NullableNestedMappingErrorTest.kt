package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class NullableNestedMappingErrorTest {

    @Test
    fun `nullable source mapped to non-null target emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String)
            data class PersonSource(val name: String, val address: AddressSource?)

            data class AddressDto(val street: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val address: AddressDto)
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("nullable")
        assertThat(result.messages).contains("address")
    }

    @Test
    fun `nullable source with @MapNested mapped to non-null target emits error`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class CompanySource(val name: String)
            data class EmployeeSource(val fullName: String, val employer: CompanySource?)

            data class CompanyDto(val name: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(EmployeeSource::class)
            data class EmployeeDto(
                val fullName: String,
                @hu.nova.blu3berry.kraft.mapping.MapNested
                val employer: CompanyDto
            )
            """
        )

        val result = TestKspRunner.compile(source)

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("nullable")
        assertThat(result.messages).contains("employer")
    }
}
