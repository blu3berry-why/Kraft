package hu.nova.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapNestedWithConfigTest {

    @Test
    fun `@MapConfig for child pair is auto-applied to implicit nested mapper`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val streetName: String, val city: String)
            data class PersonSource(val name: String, val address: AddressSource)

            data class AddressDto(val street: String, val city: String)

            @hu.nova.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(
                val name: String,
                @hu.nova.blu3berry.kraft.mapping.MapNested
                val address: AddressDto
            )

            @hu.nova.blu3berry.kraft.config.MapConfig(
                source = AddressSource::class,
                target = AddressDto::class,
                fieldMappings = [
                    hu.nova.blu3berry.kraft.config.FieldMapping("streetName", "street")
                ]
            )
            object AddressMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun PersonSource.toPersonDto()")
        assertThat(content).contains("fun AddressSource.toAddressDto()")
        assertThat(content).contains("street = this.streetName")
    }
}
