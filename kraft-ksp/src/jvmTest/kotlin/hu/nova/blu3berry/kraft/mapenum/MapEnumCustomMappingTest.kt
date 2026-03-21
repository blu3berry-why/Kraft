package hu.nova.blu3berry.kraft.mapenum

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.SourceFile
import hu.nova.blu3berry.kraft.TestKspRunner
import org.junit.jupiter.api.Test

class MapEnumCustomMappingTest {

    @Test
    fun `@MapEnum maps all entries via fieldMappings when names differ`() {
        val source = SourceFile.kotlin(
            "Models.kt",
            """
            enum class OrderState  { PENDING, SHIPPED, CANCELLED }
            enum class OrderStatus { IN_PROGRESS, DELIVERED, REJECTED }

            @hu.nova.blu3berry.kraft.config.MapEnum(
                from = OrderState::class,
                to   = OrderStatus::class,
                fieldMappings = [
                    hu.nova.blu3berry.kraft.config.FieldOverride(from = "PENDING",   to = "IN_PROGRESS"),
                    hu.nova.blu3berry.kraft.config.FieldOverride(from = "SHIPPED",   to = "DELIVERED"),
                    hu.nova.blu3berry.kraft.config.FieldOverride(from = "CANCELLED", to = "REJECTED")
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
