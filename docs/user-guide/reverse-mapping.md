# Reverse Mapping

`@MapReverse` generates the inverse mapper alongside the forward mapper. Instead of writing two separate mapping declarations, annotate once and get both directions.

## Basic Usage

### With @MapConfig

```kotlin
import com.blu3berry.kraft.config.MapReverse
import com.blu3berry.kraft.config.MapConfig

data class User(val id: Int, val name: String)
data class UserDto(val id: Int, val name: String)

@MapReverse
@MapConfig(source = User::class, target = UserDto::class)
object UserMapper
```

**Generated output:**

```kotlin
// Forward
public fun User.toUserDto(): UserDto = UserDto(
  id = this.id,
  name = this.name
)

// Reverse
public fun UserDto.toUser(): User = User(
  id = this.id,
  name = this.name
)
```

### With @MapFrom

```kotlin
import com.blu3berry.kraft.config.MapReverse
import com.blu3berry.kraft.mapping.MapFrom

data class User(val id: Int, val name: String)

@MapReverse
@MapFrom(User::class)
data class UserDto(val id: Int, val name: String)
```

Generates the same two functions: `User.toUserDto()` and `UserDto.toUser()`.

### With @MapTo

```kotlin
import com.blu3berry.kraft.config.MapReverse
import com.blu3berry.kraft.mapping.MapTo

@MapReverse
@MapTo(UserDto::class)
data class User(val id: Int, val name: String)

data class UserDto(val id: Int, val name: String)
```

Generates the same two functions: `User.toUserDto()` and `UserDto.toUser()`.

## Renamed Properties

Forward renames declared with `@MapField` or `FieldMapping` are automatically inverted in the reverse direction.

```kotlin
import com.blu3berry.kraft.config.MapReverse
import com.blu3berry.kraft.mapping.MapFrom
import com.blu3berry.kraft.mapping.MapField

data class User(val userId: Int, val fullName: String)

@MapReverse
@MapFrom(User::class)
data class UserDto(
    @MapField(counterPartName = "userId")
    val id: Int,
    @MapField(counterPartName = "fullName")
    val name: String
)
```

**Generated output:**

```kotlin
// Forward
public fun User.toUserDto(): UserDto = UserDto(
  id = this.userId,
  name = this.fullName
)

// Reverse (renames are inverted automatically)
public fun UserDto.toUser(): User = User(
  userId = this.id,
  fullName = this.name
)
```

The same inversion applies to `FieldMapping` entries in `@MapConfig`:

```kotlin
@MapReverse
@MapConfig(
    source = User::class,
    target = UserDto::class,
    fieldMappings = [
        FieldMapping(source = "userId", target = "id"),
        FieldMapping(source = "fullName", target = "name")
    ]
)
object UserMapper
```

## Nested Children

Nested child mappers are auto-reversed. When you add `@MapReverse` to a parent mapping that contains nested objects, Kraft generates reverse mappers for the children too.

```kotlin
import com.blu3berry.kraft.config.MapReverse
import com.blu3berry.kraft.mapping.MapFrom

data class Address(val street: String, val city: String)
data class AddressDto(val street: String, val city: String)

data class User(val name: String, val address: Address)

@MapReverse
@MapFrom(User::class)
data class UserDto(val name: String, val address: AddressDto)
```

**Generated output (four functions):**

```kotlin
// Forward
public fun User.toUserDto(): UserDto = UserDto(
  name = this.name,
  address = this.address.toAddressDto()
)

public fun Address.toAddressDto(): AddressDto = AddressDto(
  street = this.street,
  city = this.city
)

// Reverse (auto-generated for nested too)
public fun UserDto.toUser(): User = User(
  name = this.name,
  address = this.address.toAddress()
)

public fun AddressDto.toAddress(): Address = Address(
  street = this.street,
  city = this.city
)
```

This also works for collection nested properties (e.g. `List<Item>` mapped to `List<ItemDto>`).

## Converters in Reverse

If the forward mapping uses `@MapUsing` converters, you must provide a corresponding reverse converter in the same config object. The reverse converter maps from the target property back to the source property.

```kotlin
import com.blu3berry.kraft.config.*

data class User(val birthYear: Int, val name: String)
data class UserDto(val age: String, val name: String)

@MapReverse
@MapConfig(source = User::class, target = UserDto::class)
object UserMapper {
    // Forward: birthYear -> age
    @MapUsing(source = "birthYear", target = "age")
    fun toAge(v: Int): String = (2026 - v).toString()

    // Reverse: age -> birthYear
    @MapUsing(source = "age", target = "birthYear")
    fun toBirthYear(v: String): Int = 2026 - v.toInt()
}
```

**Generated output:**

```kotlin
// Forward
public fun User.toUserDto(): UserDto = UserDto(
  age = UserMapper.toAge(this.birthYear),
  name = this.name
)

// Reverse
public fun UserDto.toUser(): User = User(
  birthYear = UserMapper.toBirthYear(this.age),
  name = this.name
)
```

If you omit the reverse converter, Kraft emits a compile-time error with a message indicating that `@MapReverse` requires a reverse converter and none was found.

### Same Property Name with Different Types

When both source and target share a property name but have different types (e.g. `id: Int` vs `id: String`), Kraft auto-detects which converter belongs to which direction by matching the function's parameter type.

```kotlin
data class Entity(val id: Int, val name: String)
data class Dto(val id: String, val name: String)

@MapReverse
@MapConfig(source = Entity::class, target = Dto::class)
object EntityMapper {
    // Forward: Int -> String (auto-detected as forward because param matches Entity.id)
    @MapUsing(source = "id", target = "id")
    fun intToString(v: Int): String = v.toString()

    // Reverse: String -> Int (auto-detected as reverse because param matches Dto.id)
    @MapUsing(source = "id", target = "id")
    fun stringToInt(v: String): Int = v.toInt()
}
```

If auto-detection cannot disambiguate (e.g. both sides have the same type), use the `direction` parameter to be explicit:

```kotlin
@MapUsing(source = "id", target = "id", direction = ConverterDirection.FORWARD)
fun forwardConvert(v: Int): String = v.toString()

@MapUsing(source = "id", target = "id", direction = ConverterDirection.REVERSE)
fun reverseConvert(v: String): Int = v.toInt()
```

See [Custom Converters -- Direction Parameter](custom-converters.md#direction-parameter) for full details.

## IgnoreSide with Reverse

The `direction` parameter on `MapIgnoreField` controls which mapping direction the ignore applies to.

| Value | Forward (source -> target) | Reverse (target -> source) |
|-------|---------------------------|---------------------------|
| `IgnoreSide.TARGET` | Property is ignored | Property is NOT ignored |
| `IgnoreSide.SOURCE` | Property is NOT ignored | Property is ignored |
| `IgnoreSide.BOTH` | Property is ignored | Property is ignored |

```kotlin
import com.blu3berry.kraft.config.*

data class User(val id: Int, val name: String, val internalNote: String = "", val extra: String = "")
data class UserDto(val id: Int, val name: String, val internalNote: String = "", val extra: String = "")

@MapReverse
@MapConfig(
    source = User::class,
    target = UserDto::class,
    ignoredMappings = [
        MapIgnoreField("extra", direction = IgnoreSide.TARGET),
        MapIgnoreField("internalNote", direction = IgnoreSide.SOURCE)
    ]
)
object UserMapper
```

**Result:**

- Forward (`User.toUserDto()`): `extra` is omitted (TARGET), `internalNote` is mapped.
- Reverse (`UserDto.toUser()`): `internalNote` is omitted (SOURCE), `extra` is mapped.

## Error Cases

| Scenario | Result |
|----------|--------|
| `@MapReverse` without `@MapFrom`, `@MapTo`, or `@MapConfig` on the same declaration | Compile-time error: must be paired with a mapping annotation |
| Forward converter exists but no reverse converter is defined | Compile-time error: `no reverse converter` message |
| Ignored property without a default value | Compile-time error (same as without `@MapReverse`) |
| Explicit `direction` mismatches the converter's types | Compile-time error: type mismatch |

## See Also

- [Custom Converters](custom-converters.md) -- writing converter functions that work with forward and reverse mappings.
- [Configuration Objects](configuration-objects.md) -- using `@MapConfig` as the base for `@MapReverse`.
