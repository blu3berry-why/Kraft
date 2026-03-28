package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class SharedChildMapperTest {

    @Test
    fun `child mapper shared by two parents is generated exactly once`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val address: AddressSource)
            data class EmployeeSource(val id: Int, val address: AddressSource)

            data class AddressDto(val street: String, val city: String)

            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val address: AddressDto)

            @com.blu3berry.kraft.mapping.MapFrom(EmployeeSource::class)
            data class EmployeeDto(val id: Int, val address: AddressDto)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun PersonSource.toPersonDto()")
        assertThat(content).contains("fun EmployeeSource.toEmployeeDto()")

        val childMapperCount = content.split("fun AddressSource.toAddressDto()").size - 1
        assertThat(childMapperCount).isEqualTo(1)
    }
}
