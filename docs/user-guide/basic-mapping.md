# Basic Mapping

This page covers the core mapping annotations and how Kraft matches properties between source and target classes.

## @MapFrom

`@MapFrom` is placed on the **target** class. The `source` parameter specifies the class to map from.

```kotlin
import hu.nova.blu3berry.kraft.mapping.MapFrom

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

Property matching is by name and type. Every target constructor parameter that shares a name and type with a source property is mapped automatically.

## @MapTo

`@MapTo` is placed on the **source** class. The `target` parameter specifies the class to map to.

```kotlin
import hu.nova.blu3berry.kraft.mapping.MapTo

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

## @MapConfig

`@MapConfig` is placed on a standalone object and references both source and target externally. This is useful when you do not control either class or prefer to keep mapping logic separate.

```kotlin
import hu.nova.blu3berry.kraft.config.MapConfig

data class UserDto(val name: String, val age: Int)
data class User(val name: String, val age: Int)

@MapConfig(
    source = UserDto::class,
    target = User::class,
)
object UserMapper
```

Generated output:

```kotlin
fun UserDto.toUser(): User = User(
    name = this.name,
    age = this.age,
)
```

## When to Use Which

| Scenario | Recommended annotation |
|---|---|
| You own the target class (typical DTO pattern) | `@MapFrom` on the target |
| You own the source class | `@MapTo` on the source |
| You own neither class, or want external config | `@MapConfig` on an object |

All three produce the same generated code. The difference is only where the annotation lives. You cannot place both `@MapFrom` and `@MapTo` on the same class -- doing so results in a compile-time error.

## Properties with Default Values

Target properties that have a default value and no matching source property are automatically omitted from the generated constructor call. No `@MapIgnore` annotation is needed in this case.

```kotlin
data class OrderSource(val id: Int)

@MapFrom(OrderSource::class)
data class OrderDto(
    val id: Int,
    val status: String = "PENDING"  // no matching source property -- uses default
)
```

Generated output:

```kotlin
fun OrderSource.toOrderDto(): OrderDto = OrderDto(
    id = this.id,
    // status is omitted -- Kotlin uses its default value "PENDING"
)
```

## @MapIgnore

Use `@MapIgnore` to explicitly skip a target property. The property must have a default value (or be nullable) since Kraft will not supply a value for it.

```kotlin
import hu.nova.blu3berry.kraft.mapping.MapFrom
import hu.nova.blu3berry.kraft.mapping.MapIgnore

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
