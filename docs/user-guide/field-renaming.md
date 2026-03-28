# Field Renaming

When source and target properties have different names, Kraft provides two ways to declare the mapping: `@MapField` (on properties in annotated classes) and `FieldMapping` (in `@MapConfig`).

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

## FieldMapping (in @MapConfig)

When using `@MapConfig`, declare renames with `FieldMapping`. Its parameters use explicit `source` and `target` names -- no ambiguity about direction.

```kotlin
import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.FieldMapping

data class PersonDto(val fullName: String)
data class Person(val name: String)

@MapConfig(
    source = PersonDto::class,
    target = Person::class,
    fieldMappings = [
        FieldMapping(source = "fullName", target = "name")
    ]
)
object PersonMapper
```

Generated output:

```kotlin
fun PersonDto.toPerson(): Person = Person(
    name = this.fullName,
)
```

`source` is the property name on the source class; `target` is the constructor parameter name on the target class.

### Multiple Field Mappings

You can declare as many renames as needed in a single `@MapConfig`:

```kotlin
@MapConfig(
    source = EmployeeRecord::class,
    target = EmployeeDto::class,
    fieldMappings = [
        FieldMapping(source = "empId", target = "id"),
        FieldMapping(source = "empName", target = "name"),
        FieldMapping(source = "empEmail", target = "email"),
    ]
)
object EmployeeMapper
```

## Combining with Other Features

Field renames work alongside nested mapping, converters, and ignore rules. A renamed property can also be a nested object or have a converter applied to it.

### With @MapNested

If both `@MapField` and `@MapNested` are present on the same property, `@MapNested` takes precedence. Kraft emits a compile-time warning in this case:

```kotlin
@MapFrom(PersonSource::class)
data class PersonDto(
    val name: String,
    @MapNested(sourceName = "homeAddress")
    @MapField(counterPartName = "homeAddress")  // redundant -- @MapNested wins
    val address: AddressDto
)
```

This compiles successfully but produces a warning: `@MapNested takes precedence`. Prefer using only `@MapNested` when nested mapping is required.

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
