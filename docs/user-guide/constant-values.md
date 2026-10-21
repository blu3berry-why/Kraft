# Constant Values

Fill a target property with a value that does not come from the source: a fixed currency, a flag, a timestamp. Write the value as ordinary Kotlin on a member of the `@MapConfig` object and mark the member `@MapConstant`. Kotlin type-checks the value, and Kraft checks that its type fits the target before it generates any code.

## A property on the mapper object

```kotlin
data class UserDto(val name: String)
data class User(val name: String, val currency: String)

@MapConfig(source = UserDto::class, target = User::class)
object UserMapper {
    @MapConstant val currency = "HUF"
}
```

Generated code:

```kotlin
fun UserDto.toUser(): User = User(
    name = this.name,
    currency = UserMapper.currency
)
```

The generated mapper reads the member through the object and imports the object itself, so nothing needs to be imported by hand. A `const val` works the same way:

```kotlin
@MapConfig(source = UserDto::class, target = User::class)
object UserMapper {
    @MapConstant const val isActive = true
}
```

A constant wins over a source property of the same name. If `UserDto` also had a `currency`, the mapper would still read `UserMapper.currency`, with no warning.

### Where it goes

Put `@MapConstant` on a property or a function declared directly in the `@MapConfig` object. The member must be readable by the generated mapper:

- not `private` (`internal` and public both work),
- for a function, no parameters and no receiver.

Anything else is a compile-time error ([KRAFT-E069](errors.md#kraft-e069)), and so is `@MapConstant` on a member of an object that has no `@MapConfig`.

## The target name

The target is the member's name. Set `target` when the member should have a different name:

```kotlin
@MapConfig(source = UserDto::class, target = User::class)
object UserMapper {
    @MapConstant(target = "currency") val defaultCurrency = "HUF"
}
// generates: currency = UserMapper.defaultCurrency
```

A target that is not a constructor parameter of the target class is a compile-time error ([KRAFT-E067](errors.md#kraft-e067)). So is a member whose type does not fit the target ([KRAFT-E068](errors.md#kraft-e068)): `@MapConstant val isActive = "yes"` cannot fill `val isActive: Boolean`. A non-null member may fill a nullable target; a nullable member cannot fill a non-null one.

Each target takes one rule. A target filled by a `@MapConstant` cannot also be renamed with `@FieldMapping`, converted with `@MapUsing`, ignored with `@MapIgnoreField` or filled by a second `@MapConstant` ([KRAFT-E070](errors.md#kraft-e070)).

## `val` is read once, `fun` is called every time

A property is evaluated once, when the object is initialised. A function is called on every mapping. Use a function for anything that must be fresh each time, such as a timestamp or a generated ID.

Wrong: every mapped user gets the same timestamp, the moment `UserMapper` was first used.

```kotlin
@MapConfig(source = UserDto::class, target = User::class)
object UserMapper {
    @MapConstant val createdAt = Clock.System.now()
}
```

Right: the mapper calls `UserMapper.createdAt()` each time it runs.

```kotlin
@MapConfig(source = UserDto::class, target = User::class)
object UserMapper {
    @MapConstant fun createdAt() = Clock.System.now()
}
```

A function can have any name when `target` is set. Here a counter shows that each call runs again:

```kotlin
data class User(val name: String, val createdAt: Long)

@MapConfig(source = UserDto::class, target = User::class)
object UserMapper {
    var counter = 0L

    @MapConstant(target = "createdAt") fun now(): Long = counter++
}
// generates: createdAt = UserMapper.now()
// the first toUser() gives createdAt = 0, the second 1
```

## Direction on a reverse mapper

On a [`@MapReverse`](reverse-mapping.md) object a constant applies to one direction only, never both. With the default, `direction = ConverterDirection.AUTO`, it applies forward when the `@MapConfig` target has the parameter, and in reverse otherwise:

```kotlin
data class UserDto(val name: String, val origin: String)
data class User(val name: String)

@MapConfig(source = UserDto::class, target = User::class)
@MapReverse
object UserMapper {
    @MapConstant val origin = "api" // only UserDto has 'origin': applies to User.toUserDto()
}
```

When both classes have the parameter, AUTO picks forward, and the reverse mapper copies the property as usual. Set the direction explicitly to choose the other side:

```kotlin
data class UserDto(val name: String, val currency: String)
data class User(val name: String, val currency: String)

@MapConfig(source = UserDto::class, target = User::class)
@MapReverse
object UserMapper {
    @MapConstant(direction = ConverterDirection.REVERSE) val currency = "HUF"
}
// fun UserDto.toUser():   currency = this.currency
// fun User.toUserDto():   currency = UserMapper.currency
```

## Computing a value from the source: use @MapUsing

A `@MapConstant` cannot see the object being mapped. When the value depends on the source, write a whole-source [`@MapUsing`](custom-converters.md#whole-source-mode) converter instead:

```kotlin
@MapConfig(source = UserDto::class, target = User::class)
object UserMapper {
    @MapUsing(target = "displayName")
    fun UserDto.displayName() = name.uppercase()
}
```
