# Value Classes

Typed IDs and units are usually modelled as value classes in the domain layer, while DTOs and entities carry the raw type. Kraft wraps and unwraps them automatically. You don't need a converter.

```kotlin
@JvmInline
value class UserId(val value: String)

data class User(val id: UserId, val friends: List<UserId>, val invitedBy: UserId?)
data class UserDto(val id: String, val friends: List<String>, val invitedBy: String?)

@MapReverse
@MapConfig(source = User::class, target = UserDto::class)
object UserMapper
```

Generated code:

```kotlin
fun User.toUserDto(): UserDto = UserDto(
    id = this.id.value,
    friends = this.friends.map { it.value },
    invitedBy = this.invitedBy?.value
)

fun UserDto.toUser(): User = User(
    id = UserId(this.id),
    friends = this.friends.map { UserId(it) },
    invitedBy = this.invitedBy?.let { UserId(it) }
)
```

Value classes are a Kotlin language feature, so this works on every Kotlin Multiplatform target. `@JvmInline` is the marker Kotlin requires for value classes compiled to the JVM. Declare it in `commonMain` as shown.

## What is supported

| Source | Target | Generated |
|--------|--------|-----------|
| `UserId` | `String` | `this.id.value` |
| `String` | `UserId` | `UserId(this.id)` |
| `UserId?` | `String?` | `this.id?.value` |
| `String?` | `UserId?` | `this.id?.let { UserId(it) }` |
| `List<UserId>` / `Set<UserId>` | `List<String>` / `Set<String>` | `this.ids.map { it.value }` (`.toSet()` for sets) |
| `List<String>` / `Set<String>` | `List<UserId>` / `Set<UserId>` | `this.ids.map { UserId(it) }` |

Renames (`@MapField`, `FieldMapping`) and `@MapReverse` work as usual. A value class from another module or library works the same way as one in your own module.

## Rules

- **The value class must be constructible and readable.** Its constructor and its single underlying property must be public, and it must not be generic. Otherwise Kraft reports the usual type mismatch, and you supply a [custom converter](custom-converters.md).
- **Kraft never invents a value.** A nullable source can't fill a non-null target (`UserId?` → `String`), so that case is reported as a type mismatch.
- **Collections need non-null elements.** `List<UserId?>` is not auto-mapped.
- **Your converter wins.** If a `@KraftConverter` is registered for the pair (e.g. `UserId.toDisplay(): String`), Kraft uses it instead of unwrapping.
- **Value class to value class** with the same shape (`UserId` → `UserIdDto`) is handled by [nested mapping](nested-mapping.md). Different underlying types (`UserId(String)` → `UserIdDto(Uuid)`) need a converter.
