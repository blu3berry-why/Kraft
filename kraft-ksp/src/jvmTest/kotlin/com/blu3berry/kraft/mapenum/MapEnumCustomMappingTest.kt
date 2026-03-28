package com.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import com.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapEnumCustomMappingTest {

    @Test
    fun `@MapEnum maps all entries via fieldMappings when names differ`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class OrderState  { PENDING, SHIPPED, CANCELLED }
            enum class OrderStatus { IN_PROGRESS, DELIVERED, REJECTED }

            @com.blu3berry.kraft.config.MapEnum(
                source = OrderState::class,
                target   = OrderStatus::class,
                fieldMappings = [
                    com.blu3berry.kraft.config.FieldMapping(source = "PENDING",   target = "IN_PROGRESS"),
                    com.blu3berry.kraft.config.FieldMapping(source = "SHIPPED",   target = "DELIVERED"),
                    com.blu3berry.kraft.config.FieldMapping(source = "CANCELLED", target = "REJECTED")
                ]
            )
            object OrderStateMapping
            """
        )

        val content = TestKspRunner.compileAndReturnGenerated(source)
            .first().readText()

        assertThat(content).contains("fun OrderState.toOrderStatus()")
        assertThat(content).contains("OrderState.PENDING -> OrderStatus.IN_PROGRESS")
        assertThat(content).contains("OrderState.SHIPPED -> OrderStatus.DELIVERED")
        assertThat(content).contains("OrderState.CANCELLED -> OrderStatus.REJECTED")
    }
}
