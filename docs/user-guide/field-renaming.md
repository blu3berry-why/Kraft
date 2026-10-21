# Field Renaming

When source and target properties have different names, Kraft provides two ways to declare the mapping: `FieldMapping` (in `@MapConfig`) and `@MapField` (on properties in annotated classes). These are the recommended approaches for new code.

> **Note:** `@MapNested` is **deprecated** — nested data classes are auto-detected when their types differ, and renamed nested pairs should use `@MapField` / `@FieldMapping`. See the [migration note](#migration-note-replacing-mapnested) below.

## FieldMapping (in @MapConfig)

When using `@MapConfig`, declare each rename as a `@FieldMapping` written directly on the config object. Its parameters use explicit `source` and `target` names -- no ambiguity about direction.

```kotlin
import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.FieldMapping

data class UserDto(val name: String, val mail: String)
data class User(val fullName: String, val email: String)

@MapConfig(source = UserDto::class, target = User::class)
@FieldMapping(source = "name", target = "fullName")
@FieldMapping(source = "mail", target = "email")
object UserMapper
```

Generated output:

```kotlin
fun UserDto.toUser(): User = User(
    fullName = this.name,
    email = this.mail,
)
```

`source` is the property name on the source class; `target` is the constructor parameter name on the target class.

### Multiple Field Mappings

`@FieldMapping` is repeatable: write one annotation per rename, as many as you need. Under `@MapReverse` each rename also applies, swapped, to the reverse mapper (`User.toUserDto()` reads `name = this.fullName` and `mail = this.email`).

### Also supported: the fieldMappings array

The same renames can be written as entries of `@MapConfig.fieldMappings`. Both forms behave the same.

```kotlin
@MapConfig(
    source = UserDto::class,
    target = User::class,
    fieldMappings = [
        FieldMapping(source = "name", target = "fullName"),
        FieldMapping(source = "mail", target = "email"),
    ]
)
object UserMapper
```

Pick one form per object. Mixing them works when they name different targets, but naming the same target in both is a compile-time error ([KRAFT-E066](errors.md#kraft-e066)). A flat `@FieldMapping` on a class without `@MapConfig` is a compile-time error too ([KRAFT-E071](errors.md#kraft-e071)); on `@MapFrom` / `@MapTo` classes use `@MapField` instead.

## @MapField (on @MapFrom / @MapTo Classes)

`@MapField` is placed on a property of a class annotated with `@MapFrom` or `@MapTo`. Its `counterPartName` parameter always refers to the property name on the **other** class.

### With @MapFrom (annotated class is the target)

`counterPartName` is the **source** property name:

```kotlin
import com.blu3berry.kraft.mapping.MapFrom
import com.blu3berry.kraft.mapping.MapField

data class UserSource(val userId: Int, val fullName: String)

@MapFrom(UserSource::class)
data class UserDto(
    @MapField(counterPartName = "userId")
    val id: Int,
    val fullName: String
)
```

Generated output:

```kotlin
fun UserSource.toUserDto(): UserDto = UserDto(
    id = this.userId,
    fullName = this.fullName,
)
```

The `id` property on `UserDto` reads from `userId` on `UserSource`. The `fullName` property matches by name and needs no annotation.

### With @MapTo (annotated class is the source)

`counterPartName` is the **target** property name:

```kotlin
import com.blu3berry.kraft.mapping.MapTo
import com.blu3berry.kraft.mapping.MapField

data class UserDto(val id: Int, val fullName: String)

@MapTo(UserDto::class)
data class User(
    @MapField(counterPartName = "id")
    val userId: Int,
    val fullName: String
)
```

Generated output:

```kotlin
fun User.toUserDto(): UserDto = UserDto(
    id = this.userId,
    fullName = this.fullName,
)
```

The rule is consistent: `counterPartName` always points to the other side.

## Combining with Other Features

Field renames work alongside nested mapping, converters, and ignore rules. A renamed property can also be a nested object or have a converter applied to it.

### Migration note: replacing @MapNested

`@MapNested` is deprecated. Auto-detection now handles nested mapping for same-named properties, and renamed nested pairs should be declared with `@MapField` (or `FieldMapping` inside `@MapConfig`). To migrate, remove `@MapNested` from the property and let auto-detection cover the matching-name case, or add `@MapField(counterPartName = "...")` for the renamed case:

```kotlin
@MapFrom(PersonSource::class)
data class PersonDto(
    val name: String,
    @MapField(counterPartName = "homeAddress")
    val address: AddressDto
)
```

## Error Cases

**Unknown counterpart property:** If `counterPartName` references a property that does not exist on the counterpart class, Kraft reports a compile-time error.

```kotlin
@MapFrom(UserSource::class)
data class UserDto(
    val name: String,
    @MapField(counterPartName = "nonexistent")  // ERROR: no such property on UserSource
    val id: Int
)
```

**Type mismatch after rename:** If the renamed source property exists but has a different type than the target property, Kraft reports a compile-time error indicating a type mismatch.
