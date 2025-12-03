package hu.nova.blu3berry.kraft


import hu.nova.blu3berry.kraft.config.EnumMap
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.config.StringPair
import hu.nova.blu3berry.kraft.onclass.from.MapField
import hu.nova.blu3berry.kraft.onclass.from.MapFrom
import hu.nova.blu3berry.kraft.onclass.to.MapTo


data class User(
    val id: Int,
    val name: String,
    val test: Test
)

@MapFrom(User::class)
data class UserDto(
    val id: String,
    val name: String,
    val test: Test
)

enum class Test{
    A,
    B
}

enum class Test2{
    A,
    C
}

@MapConfig(
    from = User::class,
    to = (UserDto::class),
    fieldMapping= [
        StringPair(from = "id", to = "id"),
        StringPair(from = "name", to = "name")
    ]
)
object E {

    @MapUsing(from = "id", to = "id")
    fun mapId(id:Int): String = "1"
}
