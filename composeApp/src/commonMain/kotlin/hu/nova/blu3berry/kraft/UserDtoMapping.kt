package hu.nova.blu3berry.kraft

import hu.nova.blu3berry.kraft.config.EnumMap
import hu.nova.blu3berry.kraft.config.FieldOverride
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
    val name: StatusDto,
    val userUser: UserDto,
)

data class Store(
    val name: Status,
    val userUser: User,
)

@MapConfig(
    from = Store::class,
    to = StoreDto::class,
    nestedMappings = [
        NestedMapping(from = User::class, to = UserDto::class),
        NestedMapping(from = Status::class, to = StatusDto::class)
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

enum class Status { ACTIVE, BLOCKED }
enum class StatusDto { ACTIVE, BANNED, UNKNOWN }

@EnumMap(
from = Status::class,
to = StatusDto::class,
fieldMapping = [
    FieldOverride(from = "BLOCKED", to = "BANNED"),
]
    )
object StatusMapping