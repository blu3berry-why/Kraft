# Ignore Rules

Skip target properties during mapping when the target has fields that should not be populated from the source. Ignored properties must have a default value in the target constructor, since they are omitted from the generated constructor call.

## @MapIgnoreField (Config-Level)

Declare ignored properties inside `@MapConfig.ignoredMappings` using `MapIgnoreField`.

```kotlin
data class UserSource(val id: Int, val name: String)
data class UserDto(val id: Int, val name: String, val internalNotes: String? = null)

@MapConfig(
    source = UserSource::class,
    target = UserDto::class,
    ignoredMappings = [MapIgnoreField("internalNotes")]
)
object UserMapper
```

Generated code:

```kotlin
fun UserSource.toUserDto(): UserDto = UserDto(
    id = this.id,
    name = this.name
    // internalNotes is omitted -- uses its default value null
)
```

## @MapIgnore (Class-Level)

Place `@MapIgnore` on a property in a `@MapFrom` or `@MapTo` class. The annotated property is omitted from the generated constructor call and falls back to its default value.

### On a @MapFrom class

The annotated class is the target, so the annotated property is a target constructor parameter that gets skipped directly.

```kotlin
data class ProductSource(val name: String, val price: Double)

@MapFrom(ProductSource::class)
data class ProductDto(
    val name: String,
    @MapIgnore
    val price: Double = 0.0
)
```

Generated code:

```kotlin
fun ProductSource.toProductDto(): ProductDto = ProductDto(
    name = this.name
    // price is omitted -- uses its default value 0.0
)
```

### On a @MapTo class

The annotated class is the source. The annotated property name is matched against the target constructor by name and the corresponding target parameter is skipped. This only works when both classes share the same property name.

```kotlin
data class UserDto(val name: String, val internalNotes: String = "")

@MapTo(UserDto::class)
data class User(
    val name: String,
    @MapIgnore
    val internalNotes: String
)
```

Generated: the `internalNotes` parameter is omitted from the `UserDto(...)` constructor call.

## IgnoreSide -- Directional Ignoring

`MapIgnoreField` accepts an optional `direction` parameter of type `IgnoreSide` to control which mapping direction the ignore applies to. The default is `IgnoreSide.BOTH`.

| Value | Behavior |
|---|---|
| `IgnoreSide.TARGET` | Ignore only when mapping source to target (forward direction). The `name` refers to a target-class constructor parameter. |
| `IgnoreSide.SOURCE` | Ignore only when mapping target to source (reverse direction). Honored by `@MapReverse`. The `name` refers to a source-class constructor parameter. See [Reverse Mapping](reverse-mapping.md) for details. |
| `IgnoreSide.BOTH` | Ignore in both directions. If the property name exists in only one direction's target, it is applied there; if it exists in both, it is applied in both. |

### TARGET direction example

```kotlin
data class User(val id: Int, val name: String, val secret: String)
data class UserDto(val id: Int, val name: String, val secret: String? = null)

@MapConfig(
    source = User::class,
    target = UserDto::class,
    ignoredMappings = [
        MapIgnoreField("secret", direction = IgnoreSide.TARGET)
    ]
)
object UserMapper
```

Generated: the `secret` parameter is omitted from the `UserDto(...)` constructor call. If a reverse mapper were generated in the future, `secret` would not be ignored in that direction.

### BOTH direction with unknown property

When using `IgnoreSide.BOTH` (the default), a property name that does not exist in the current forward target is silently skipped. This allows you to declare ignore rules for future reverse-mapping without causing errors on the forward mapper:

```kotlin
@MapConfig(
    source = Item::class,
    target = ItemDto::class,
    ignoredMappings = [MapIgnoreField("futureReverseOnly")]
)
object ItemMapper
```

This compiles successfully even though `futureReverseOnly` does not exist on `ItemDto`.

### Multiple ignored properties

You can ignore multiple properties in a single config:

```kotlin
@MapConfig(
    source = Order::class,
    target = OrderDto::class,
    ignoredMappings = [
        MapIgnoreField("auditLog", direction = IgnoreSide.TARGET),
        MapIgnoreField("internalNotes")
    ]
)
object OrderMapper
```

## Error Cases

| Condition | Result |
|---|---|
| Ignored property has no default value in the target constructor | Compile-time error ("has no default value") |
| `MapIgnoreField` with `IgnoreSide.TARGET` references a name that does not exist in the target class | Compile-time error ("not found") |
| `@MapIgnore` on a non-null property with no default value | Compile-time error ("has no default value") |
