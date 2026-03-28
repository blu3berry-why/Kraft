package com.blu3berry.kraft

import com.blu3berry.kraft.config.MapEnum
import com.blu3berry.kraft.config.FieldMapping
import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.NestedMapping
import com.blu3berry.kraft.mapping.MapTo

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
    source = Store::class,
    target = StoreDto::class,
    nestedMappings = [
        NestedMapping(source = User::class, target = UserDto::class),
    ]
)
object StoreMapping



enum class Status { ACTIVE, BLOCKED }
enum class StatusDto { ACTIVE, BANNED, UNKNOWN }

@MapEnum(
    source = Status::class,
    target = StatusDto::class,
    fieldMappings = [
        FieldMapping(source = "BLOCKED", target = "BANNED"),
    ]
)
object StatusMapping