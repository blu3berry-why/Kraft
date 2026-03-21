package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapToDirectionNestedMappingTest {

    @Test
    fun `@MapTo direction auto-detects nested mapping for same-name property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class AddressDto(val street: String, val city: String)
            data class PersonDto(val name: String, val address: AddressDto)

            @hu.nova.blu3berry.kraft.mapping.MapTo(PersonDto::class)
            data class PersonSource(val name: String, val address: AddressSource)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun PersonSource.toPersonDto()")
        assertThat(content).contains("fun AddressSource.toAddressDto()")
        assertThat(content).contains("address = this.address.toAddressDto()")
    }
}
