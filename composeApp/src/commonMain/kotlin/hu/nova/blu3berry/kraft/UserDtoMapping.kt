package hu.nova.blu3berry.kraft


import hu.nova.blu3berry.kraft.onclass.from.MapField
import hu.nova.blu3berry.kraft.onclass.from.MapFrom
import hu.nova.blu3berry.kraft.onclass.to.MapTo


@MapFrom(UserDto::class)
data class User(
    val id: Int,
    val name: String
)

@MapFrom(User::class)
data class UserDto(
    val id: Int,
    val name: String
)