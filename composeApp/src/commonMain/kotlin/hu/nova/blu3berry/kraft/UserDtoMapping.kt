package hu.nova.blu3berry.kraft

import hu.nova.blu3berry.kraft.config.MapEnum
import hu.nova.blu3berry.kraft.config.FieldMapping
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.config.NestedMapping
import hu.nova.blu3berry.kraft.onclass.to.MapTo

@MapTo(UserDto::class)
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



enum class Status { ACTIVE, BLOCKED }
enum class StatusDto { ACTIVE, BANNED, UNKNOWN }

@MapEnum(
    from = Status::class,
    to = StatusDto::class,
    fieldMappings = [
        FieldMapping(from = "BLOCKED", to = "BANNED"),
    ]
)
object StatusMapping