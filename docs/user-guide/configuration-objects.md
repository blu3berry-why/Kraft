# Configuration Objects

`@MapConfig` lets you define mapping rules on a standalone object instead of annotating the data classes themselves. It centralizes field renames, nested mappings, ignore rules, and custom converters in one place.

## When to Use @MapConfig

- You cannot modify the source or target classes (e.g. third-party or generated types).
- You want all mapping logic grouped in a single config object.
- You need converters (`@MapUsing`), nested mappings, or ignore rules that do not belong on the data classes.
- You want to supplement a `@MapFrom` / `@MapTo` class with additional config (converters, overrides).

## Basic Usage

Annotate a Kotlin `object` with `@MapConfig`, specifying source and target classes. Kraft generates an extension function on the source class.

```kotlin
data class User(val id: Int, val name: String)
data class UserDto(val id: Int, val name: String)

@MapConfig(source = User::class, target = UserDto::class)
object UserMapper
```

**Generated output:**

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
  id = this.id,
  name = this.name
)
```

## Field Renames with FieldMapping

Use `fieldMappings` when the source and target have properties with different names.

```kotlin
data class User(val userId: Int, val fullName: String)
data class UserDto(val id: Int, val name: String)

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

**Generated output:**

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
  id = this.userId,
  name = this.fullName
)
```

## Nested Objects

When source and target contain nested objects of different types, Kraft auto-detects the nested relationship and generates a child mapper. No additional configuration is needed for same-named properties:

```kotlin
data class Address(val street: String, val city: String)
data class AddressDto(val street: String, val city: String)

data class Store(val location: Address)
data class StoreDto(val location: AddressDto)

@MapConfig(source = Store::class, target = StoreDto::class)
object StoreMapper
```

**Generated output:**

```kotlin
public fun Store.toStoreDto(): StoreDto = StoreDto(
  location = this.location.toAddressDto()
)

public fun Address.toAddressDto(): AddressDto = AddressDto(
  street = this.street,
  city = this.city
)
```

If the nested property is renamed, use `FieldMapping` -- the child mapper is still auto-detected. See [Nested Mapping](nested-mapping.md#renamed-nested-properties) for details.

## Ignoring Properties with MapIgnoreField

Use `ignoredMappings` to skip target constructor parameters. The skipped property must have a default value.

```kotlin
data class User(val id: Int, val name: String, val internalNotes: String)
data class UserDto(val id: Int, val name: String, val internalNotes: String = "")

@MapConfig(
    source = User::class,
    target = UserDto::class,
    ignoredMappings = [MapIgnoreField("internalNotes")]
)
object UserMapper
```

**Generated output:**

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
  id = this.id,
  name = this.name
  // internalNotes is omitted; its default value "" is used
)
```

## Custom Converters with @MapUsing

Place `@MapUsing`-annotated functions inside the config object to transform property values during mapping.

### Property-source mode

The function receives a single source property value.

```kotlin
data class Src(val int: Int)
data class Dst(val text: String)

@MapConfig(source = Src::class, target = Dst::class)
object MyMapper {
    @MapUsing(source = "int", target = "text")
    fun convert(v: Int): String = "Number: $v"
}
```

**Generated output:**

```kotlin
public fun Src.toDst(): Dst = Dst(
  text = MyMapper.convert(this.int)
)
```

### Whole-source mode

Omit the `source` parameter (or leave it blank) to receive the entire source object. This is useful when a target property depends on multiple source fields.

```kotlin
@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(target = "combined")
    fun combine(src: Src): String = "${src.a}-${src.b}"
}
```

**Generated output:**

```kotlin
public fun Src.toDst(): Dst = Dst(
  combined = SrcMapper.combine(this)
)
```

You can also write it as an extension function:

```kotlin
@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(target = "combined")
    fun Src.combine(): String = "${this.a}-${this.b}"
}
```

**Generated output:**

```kotlin
public fun Src.toDst(): Dst = Dst(
  combined = with(SrcMapper) { this@toDst.combine() }
)
```

#### Whole-source patterns at a glance

Whole-source mode covers four practical patterns. See [custom-converters.md → Whole-Source Mode Patterns](custom-converters.md#whole-source-mode-patterns) for full examples.

| Pattern | Shape | One-line example |
|---|---|---|
| Decompose 1 → N | Value type on source, primitives on target | `@MapUsing(target = "salePriceMinorUnits") fun Product.minorUnits() = salePrice.amount` |
| Compose N → 1 | Primitives on source, value type on target | `@MapUsing(target = "salePrice") fun ProductDto.toMoney() = Money(salePriceMinorUnits, salePriceCurrency)` |
| Constant injection | Target field has no source counterpart | `@MapUsing(target = "currency") fun LongAmount.fixedCurrency() = "HUF"` |
| Nullable → non-null default | Source nullable, target non-null | `@MapUsing(target = "packSize") fun CartItemDto.packSizeOrDefault() = packSize ?: 1` |

If a target field doesn't line up with a source field, reach for one of these patterns before writing a manual mapper or a global `@KraftConverter`.

### Converter Direction

When using `@MapReverse` and both classes share a property name with different types, use the `direction` parameter to specify which direction each converter applies to. Kraft auto-detects direction by default, but you can be explicit with `ConverterDirection.FORWARD` or `ConverterDirection.REVERSE`. See [Custom Converters -- Direction Parameter](custom-converters.md#direction-parameter) for details.

### Disabling global converters per config

By default, every `@MapConfig` consults the [global `@KraftConverter` registry](custom-converters.md#global-converters) when a property pair has mismatched types. Set `useGlobalConverters = false` to force this config to use only its own `@MapUsing` declarations:

```kotlin
@MapConfig(
    source = User::class,
    target = UserDto::class,
    useGlobalConverters = false
)
object UserMapper {
    @MapUsing(source = "id", target = "id")
    fun explicit(id: Uuid): String = id.toString()
}
```

Class-level `@MapFrom` / `@MapTo` mappings are gated through any attached `@MapConfig`, so opting out on the config also opts out the class-level mapper.

## Combining All Features

Here is an example using field renames, auto-detected nested mapping, ignore rules, and a converter together.

```kotlin
data class Address(val street: String, val city: String)
data class AddressDto(val street: String, val city: String)

data class Order(
    val orderId: Int,
    val createdAt: Long,
    val internalNotes: String,
    val shippingAddress: Address
)
data class OrderDto(
    val id: Int,
    val dateStr: String,
    val internalNotes: String = "",
    val shippingAddress: AddressDto
)

@MapConfig(
    source = Order::class,
    target = OrderDto::class,
    fieldMappings = [FieldMapping(source = "orderId", target = "id")],
    ignoredMappings = [MapIgnoreField("internalNotes")]
)
object OrderMapper {
    @MapUsing(source = "createdAt", target = "dateStr")
    fun formatDate(v: Long): String = v.toString()
}
```

**Generated output:**

```kotlin
public fun Order.toOrderDto(): OrderDto = OrderDto(
  id = this.orderId,
  dateStr = OrderMapper.formatDate(this.createdAt),
  shippingAddress = this.shippingAddress.toAddressDto()
  // internalNotes omitted; default "" used
)

public fun Address.toAddressDto(): AddressDto = AddressDto(
  street = this.street,
  city = this.city
)
```

## Config + Class Annotations

`@MapConfig` can supplement a `@MapFrom` or `@MapTo` class by providing converters and overrides. Reference the config via the `config` parameter.

```kotlin
data class User(val birthDate: Long, val name: String)

@MapConfig(source = User::class, target = UserDto::class)
object UserMappingConfig {
    @MapUsing(source = "birthDate", target = "age")
    fun toAge(v: Long): String = ((System.currentTimeMillis() / 1000 - v) / 31536000).toString()
}

@MapFrom(User::class, config = UserMappingConfig::class)
data class UserDto(val name: String, val age: String)
```

Both the class annotation and the config must reference the same source/target pair. Duplicate source-to-target pairs across multiple configs produce a compile-time error.

## Error Cases

| Scenario | Result |
|----------|--------|
| `@MapIgnoreField` names a property without a default value | Compile-time error |
| `@MapIgnoreField(direction = IgnoreSide.TARGET)` names a property that does not exist on the target | Compile-time error |
| `@MapIgnoreField` (default `IgnoreSide.BOTH`) names a property absent from the forward target | KSP warning; the rule is skipped for that direction to allow declaring reverse-only ignores |
| `@MapUsing` whole-source converter parameter type does not match source class | Compile-time error |
| Duplicate source-to-target pair across multiple `@MapConfig` objects | Compile-time error |
