package com.blu3berry.kraft.reverse

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class ReverseNestedMappingTest {

    @Test
    fun `nested children are auto-reversed`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Address(val street: String, val city: String)
            data class AddressDto(val street: String, val city: String)

            data class User(val name: String, val address: Address)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.mapping.MapFrom(User::class)
            data class UserDto(val name: String, val address: AddressDto)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        // Forward mappers
        assertThat(allContent).contains("fun User.toUserDto()")
        assertThat(allContent).contains("fun Address.toAddressDto()")

        // Reverse mappers (auto-generated for nested too)
        assertThat(allContent).contains("fun UserDto.toUser()")
        assertThat(allContent).contains("fun AddressDto.toAddress()")
    }

    @Test
    fun `collection nested properties are reversed`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            data class Item(val label: String)
            data class ItemDto(val label: String)

            data class Order(val ref: String, val items: List<Item>)

            @com.blu3berry.kraft.config.MapReverse
            @com.blu3berry.kraft.mapping.MapFrom(Order::class)
            data class OrderDto(val ref: String, val items: List<ItemDto>)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(source)
        val files = generated.map { it.readText() }
        val allContent = files.joinToString("\n")

        // Forward
        assertThat(allContent).contains("fun Order.toOrderDto()")
        assertThat(allContent).contains("fun Item.toItemDto()")

        // Reverse
        assertThat(allContent).contains("fun OrderDto.toOrder()")
        assertThat(allContent).contains("fun ItemDto.toItem()")
    }
}
