package kraft.itest.consumer

import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapReverse
import kotlin.jvm.JvmInline

@JvmInline
value class UserId(val value: String)

data class Item(val name: String, val qty: Int)

data class ItemDto(val name: String, val qty: Int)

@MapReverse
@MapConfig(source = Item::class, target = ItemDto::class)
object ItemMapper

data class Basket(val groups: Map<String, List<Item>>, val owners: Array<UserId>)

data class BasketDto(val groups: Map<String, List<ItemDto>>, val owners: Array<String>)

@MapReverse
@MapConfig(source = Basket::class, target = BasketDto::class)
object BasketMapper
