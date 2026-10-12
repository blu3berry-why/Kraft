package kraft.itest.consumer

import kraft.itest.consumer.generated.toOrder
import kraft.itest.consumer.generated.toOrderDto
import kraft.itest.producer.OrderId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Value classes are wrapped and unwrapped in commonMain-generated mappers on every
 * target, including a value class declared in another KMP module (`OrderId`).
 */
class ValueClassMappingTest {

    private val order = Order(id = OrderId(7), skus = listOf(Sku("a"), Sku("b")), parent = null)
    private val dto = OrderDto(id = 7, skus = listOf("a", "b"), parent = null)

    @Test
    fun unwrapsToDto() {
        assertEquals(dto, order.toOrderDto())
    }

    @Test
    fun reverseWrapsBack() {
        assertEquals(order, dto.toOrder())
        assertEquals(OrderId(3), dto.copy(parent = 3).toOrder().parent)
    }
}
