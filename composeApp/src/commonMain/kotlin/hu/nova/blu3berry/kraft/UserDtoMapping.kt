package hu.nova.blu3berry.kraft

import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.config.NestedMapping


data class User(
    val id: Int,
    val name: String,
)

data class UserDto(
    val id: String,
    val name: String,
)

data class StoreDto(
    val name: String,
    val userUser: UserDto,
)

data class Store(
    val name: String,
    val userUser: User,
)

@MapConfig(
    from = Store::class,
    to = StoreDto::class,
    nestedMappings = [
        NestedMapping(from = User::class, to = UserDto::class)
    ]
)
object StoreMapping


@MapConfig(
    from = User::class,
    to = (UserDto::class),
)
object E {
    val a= Unit

    @MapUsing(from = "id", to = "id")
    fun mapId(id: Int): String = "1"


}
