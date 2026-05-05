package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class CrossPackageNestedMappingTest {

    @Test
    fun `parent mapper imports nested mapper that lives in a different package`() {
        val outer = SourceFile.kotlin(
            "Outer.kt",
            """
            package com.example.outer

            import com.example.address.AddressSource

            data class PersonSource(val name: String, val address: AddressSource)

            data class AddressDto(val street: String)

            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val address: AddressDto)
            """
        )

        val inner = SourceFile.kotlin(
            "Inner.kt",
            """
            package com.example.address

            data class AddressSource(val street: String)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(outer, inner)
        val parentFile = generated.single { it.name.startsWith("PersonSourceToPersonDtoMapper") }
        val parentContent = parentFile.readText()

        assertThat(parentContent).contains("import com.example.address.generated.toAddressDto")
        assertThat(parentContent).contains("address = this.address.toAddressDto()")
    }
}
