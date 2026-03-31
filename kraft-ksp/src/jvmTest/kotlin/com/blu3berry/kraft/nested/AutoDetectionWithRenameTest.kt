package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class AutoDetectionWithRenameTest {

    @Test
    fun `auto-detection works with FieldMapping rename in MapConfig`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val homeAddress: AddressSource)

            data class AddressDto(val street: String, val city: String)
            data class PersonDto(val name: String, val address: AddressDto)

            @com.blu3berry.kraft.config.MapConfig(
                source = PersonSource::class,
                target = PersonDto::class,
                fieldMappings = [
                    com.blu3berry.kraft.config.FieldMapping(source = "homeAddress", target = "address")
                ]
            )
            object PersonMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun PersonSource.toPersonDto()")
        assertThat(content).contains("fun AddressSource.toAddressDto()")
        assertThat(content).contains("address = this.homeAddress.toAddressDto()")
    }

    @Test
    fun `auto-detection works with MapField rename on MapFrom class`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val homeAddress: AddressSource)

            data class AddressDto(val street: String, val city: String)

            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(
                val name: String,
                @com.blu3berry.kraft.mapping.MapField(counterPartName = "homeAddress")
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

    @Test
    fun `auto-detection works with collection rename in MapConfig`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class ItemSource(val id: Int, val label: String)
            data class OrderSource(val ref: String, val orderItems: List<ItemSource>)

            data class ItemDto(val id: Int, val label: String)
            data class OrderDto(val ref: String, val items: List<ItemDto>)

            @com.blu3berry.kraft.config.MapConfig(
                source = OrderSource::class,
                target = OrderDto::class,
                fieldMappings = [
                    com.blu3berry.kraft.config.FieldMapping(source = "orderItems", target = "items")
                ]
            )
            object OrderMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        assertThat(content).contains("fun OrderSource.toOrderDto()")
        assertThat(content).contains("fun ItemSource.toItemDto()")
        assertThat(content).contains("items = this.orderItems.map { it.toItemDto() }")
    }

    @Test
    fun `auto-detection with rename works in reverse mapping`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class AddressSource(val street: String, val city: String)
            data class PersonSource(val name: String, val homeAddress: AddressSource)

            data class AddressDto(val street: String, val city: String)
            data class PersonDto(val name: String, val address: AddressDto)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.config.MapConfig(
                source = PersonSource::class,
                target = PersonDto::class,
                fieldMappings = [
                    com.blu3berry.kraft.config.FieldMapping(source = "homeAddress", target = "address")
                ]
            )
            object PersonMapper
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val content = generated.joinToString("\n") { it.readText() }

        // Forward
        assertThat(content).contains("fun PersonSource.toPersonDto()")
        assertThat(content).contains("address = this.homeAddress.toAddressDto()")

        // Reverse
        assertThat(content).contains("fun PersonDto.toPersonSource()")
        assertThat(content).contains("homeAddress = this.address.toAddressSource()")
    }
}
