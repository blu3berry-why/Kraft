package hu.nova.blu3berry.kraft


import hu.nova.blu3berry.kraft.E.asd
import hu.nova.blu3berry.kraft.config.EnumMap
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
    val id: Int,
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

@EnumMap(from = Test::class, to = Test2::class, fieldMapping = [
    StringPair(from ="B", to = "C")
])
object E {

    fun Test2.asd() = "lol"
}
