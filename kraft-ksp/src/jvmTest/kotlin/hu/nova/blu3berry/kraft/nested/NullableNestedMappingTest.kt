package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class NullableNestedMappingTest {

    @Test
    fun `nullable source with nullable target emits safe call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String)
            data class PersonSource(val name: String, val address: AddressSource?)

            data class AddressDto(val street: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val address: AddressDto?)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }

        assertThat(content).contains("address = this.address?.toAddressDto()")
    }

    @Test
    fun `non-null source with non-null target emits direct call`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String)
            data class PersonSource(val name: String, val address: AddressSource)

            data class AddressDto(val street: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val address: AddressDto)
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }

        assertThat(content).contains("address = this.address.toAddressDto()")
    }

    @Test
    fun `nullable source with nullable target via @MapNested emits safe call`() {
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
                val employer: CompanyDto?
            )
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .joinToString("\n") { it.readText() }

        assertThat(content).contains("employer = this.employer?.toCompanyDto()")
    }
}
