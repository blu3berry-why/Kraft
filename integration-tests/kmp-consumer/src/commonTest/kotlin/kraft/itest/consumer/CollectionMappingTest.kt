package kraft.itest.consumer

import kraft.itest.consumer.generated.toBasket
import kraft.itest.consumer.generated.toBasketDto
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Map-of-list and Array-of-value-class fields map element-wise on every target. */
class CollectionMappingTest {

    private val basket = Basket(
        groups = mapOf("a" to listOf(Item("x", 1), Item("y", 2)), "b" to emptyList()),
        owners = arrayOf(UserId("u1"), UserId("u2")),
    )

    @Test
    fun mapsMapValuesAndArrayElements() {
        val dto = basket.toBasketDto()
        assertEquals(mapOf("a" to listOf(ItemDto("x", 1), ItemDto("y", 2)), "b" to emptyList()), dto.groups)
        assertContentEquals(arrayOf("u1", "u2"), dto.owners)
    }

    @Test
    fun reverseWrapsArrayElements() {
        val back = basket.toBasketDto().toBasket()
        assertEquals(basket.groups, back.groups)
        assertContentEquals(basket.owners, back.owners)
    }
}
