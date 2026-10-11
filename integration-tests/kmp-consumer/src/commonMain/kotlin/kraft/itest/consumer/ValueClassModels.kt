package kraft.itest.consumer

import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapReverse
import kraft.itest.producer.OrderId
import kotlin.jvm.JvmInline

@JvmInline
value class Sku(val value: String)

data class Order(val id: OrderId, val skus: List<Sku>, val parent: OrderId?)

data class OrderDto(val id: Long, val skus: List<String>, val parent: Long?)

@MapReverse
@MapConfig(source = Order::class, target = OrderDto::class)
object OrderMapper
