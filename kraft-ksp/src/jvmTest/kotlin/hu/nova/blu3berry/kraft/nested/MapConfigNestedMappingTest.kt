package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapConfigNestedMappingTest {

    @Test
    fun `explicit @NestedMapping in @MapConfig generates child mapper`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val address: AddressSource)

            data class AddressDto(val street: String, val city: String)
            data class PersonDto(val name: String, val address: AddressDto)

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = PersonSource::class,
                target   = PersonDto::class,
                nestedMappings = [
                    hu.nova.blu3berry.kraft.config.NestedMapping(
                        source = AddressSource::class,
                        target   = AddressDto::class
                    )
                ]
            )
            object PersonMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun PersonSource.toPersonDto()")
        assertThat(content).contains("fun AddressSource.toAddressDto()")
        assertThat(content).contains("address = this.address.toAddressDto()")
    }
}
