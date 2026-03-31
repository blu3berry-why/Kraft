# Basic Mapping

This page covers the core mapping annotations and how Kraft matches properties between source and target classes.

## @MapConfig

`@MapConfig` is placed on a standalone object and references both source and target externally. This keeps mapping logic separate from your data classes.

```kotlin
import com.blu3berry.kraft.config.MapConfig

data class User(val name: String, val age: Int)
data class UserDto(val name: String, val age: Int)

@MapConfig(
    source = User::class,
    target = UserDto::class,
)
object UserMapper
```

Generated output:

```kotlin
fun User.toUserDto(): UserDto = UserDto(
    name = this.name,
    age = this.age,
)
```

Property matching is by name and type. Every target constructor parameter that shares a name and type with a source property is mapped automatically.

## @MapFrom

`@MapFrom` is placed on the **target** class. The `source` parameter specifies the class to map from.

```kotlin
import com.blu3berry.kraft.mapping.MapFrom

data class User(val name: String, val age: Int)

@MapFrom(User::class)
data class UserDto(val name: String, val age: Int)
```

Generated output:

```kotlin
fun User.toUserDto(): UserDto = UserDto(
    name = this.name,
    age = this.age,
)
```

## @MapTo

`@MapTo` is placed on the **source** class. The `target` parameter specifies the class to map to.

```kotlin
import com.blu3berry.kraft.mapping.MapTo

@MapTo(UserDto::class)
data class User(val name: String, val age: Int)

data class UserDto(val name: String, val age: Int)
```

Generated output (identical to the `@MapFrom` example above):

```kotlin
fun User.toUserDto(): UserDto = UserDto(
    name = this.name,
    age = this.age,
)
```

## When to Use Which

| Scenario | Recommended annotation |
|---|---|
| You want mapping logic separate from data classes | `@MapConfig` on an object |
| You own neither class, or they are generated/third-party | `@MapConfig` on an object |
| You own the target class (typical DTO pattern) | `@MapFrom` on the target |
| You own the source class | `@MapTo` on the source |

All three produce the same generated code. The difference is only where the annotation lives. You cannot place both `@MapFrom` and `@MapTo` on the same class -- doing so results in a compile-time error.

## Properties with Default Values

Target properties that have a default value and no matching source property are automatically omitted from the generated constructor call. No ignore annotation is needed in this case.

```kotlin
data class OrderSource(val id: Int)
data class OrderDto(
    val id: Int,
    val status: String = "PENDING"  // no matching source property -- uses default
)

@MapConfig(source = OrderSource::class, target = OrderDto::class)
object OrderMapper
```

Generated output:

```kotlin
fun OrderSource.toOrderDto(): OrderDto = OrderDto(
    id = this.id,
    // status is omitted -- Kotlin uses its default value "PENDING"
)
```

## @MapIgnore

Use `@MapIgnore` to explicitly skip a target property when using `@MapFrom` or `@MapTo`. The property must have a default value (or be nullable) since Kraft will not supply a value for it. For the config-based equivalent, see [Ignore Rules](ignore-rules.md).

```kotlin
import com.blu3berry.kraft.mapping.MapFrom
import com.blu3berry.kraft.mapping.MapIgnore

data class ProductSource(val name: String, val price: Double)

@MapFrom(ProductSource::class)
data class ProductDto(
    val name: String,
    @MapIgnore
    val price: Double = 0.0
)
```

Generated output:

```kotlin
fun ProductSource.toProductDto(): ProductDto = ProductDto(
    name = this.name,
    // price is ignored
)
```

## Error Cases

**Missing source property with no default:** If the target has a non-nullable constructor parameter without a default value and there is no matching source property, Kraft reports a compile-time error.

**Type mismatch:** If a source property and target property share the same name but have incompatible types, Kraft reports a compile-time error. Use a field rename or converter to handle this situation.

## See Also

- [Configuration Objects](configuration-objects.md) -- an alternative way to declare mappings without annotating the classes themselves.
- [Field Renaming](field-renaming.md) -- mapping properties with different names.
- [Nested Mapping](nested-mapping.md) -- mapping properties that contain other mapped types.
