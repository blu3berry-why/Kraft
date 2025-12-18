package hu.nova.blu3berry.kraft

import hu.nova.blu3berry.kraft.config.EnumMap
import hu.nova.blu3berry.kraft.config.FieldOverride
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.config.NestedMapping


data class User(
    val name: String,
    val age: Int,
)

data class UserDto(
    val name: String,
    val age: Int,
)

data class StoreDto(
    val userUser: UserDto,
)

data class Store(
    val userUser: User,
)

@MapConfig(
    from = Store::class,
    to = StoreDto::class,
    nestedMappings = [
        NestedMapping(from = User::class, to = UserDto::class),
    ]
)
object StoreMapping


@MapConfig(
    from= User::class,
    to= UserDto::class,
)
object UserDtoToUserMapping{
    @MapUsing(from="name", to="name")
    fun mapName(name:String):String =
        name.uppercase()
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