package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapNestedRenamedTest {

    @Test
    fun `@MapNested with sourceName reads from renamed source property`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val homeAddress: AddressSource)

            data class AddressDto(val street: String, val city: String)

            @hu.nova.blu3berry.kraft.onclass.from.MapFrom(PersonSource::class)
            data class PersonDto(
                val name: String,
                @hu.nova.blu3berry.kraft.onclass.MapNested(sourceName = "homeAddress")
                val address: AddressDto
            )
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun PersonSource.toPersonDto()")
        assertThat(content).contains("fun AddressSource.toAddressDto()")
        assertThat(content).contains("address = this.homeAddress.toAddressDto()")
    }
}
