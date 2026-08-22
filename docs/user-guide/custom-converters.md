# Custom Converters

Kraft supports two ways to register a value-type converter:

- **`@MapUsing`** — declared inside a `@MapConfig` object, scoped to that single mapper. Use this when the conversion is specific to one source-target pair, when you need access to the whole source object, or when you want to override a globally-registered converter for one mapping.
- **`@KraftConverter`** — a top-level extension function discoverable across the whole module (and across modules via the classpath). Use this for value-type conversions that recur in many mappers (`Uuid` ↔ `String`, `Instant` ↔ `Long`, primitive widening), so you don't have to copy a `@MapUsing` block into every config.

`@MapUsing` always wins over `@KraftConverter` when both target the same property. See [Global Converters](#global-converters) at the end of this page for the full discovery and resolution rules.

*See also: [Configuration Objects](configuration-objects.md) for a full overview of `@MapConfig`-based mapping.*

## Property-Source Mode

When `source` is specified, the function receives the value of that single source property and converts it to the target type.

```kotlin
@MapUsing(source = "propertyName", target = "targetParam")
```

### Regular Function

A regular (non-extension) function receives the source property value as its parameter.

```kotlin
data class Src(val count: Int)
data class Dst(val label: String)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "count", target = "label")
    fun convert(v: Int): String = v.toString()
}
```

Generated: `label = SrcMapper.convert(this.count)`

### Extension Function

An extension function receives the source property value as `this`.

```kotlin
@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "count", target = "label")
    fun Int.toLabel(): String = this.toString()
}
```

Generated: `label = with(SrcMapper) { this@toDst.count.toLabel() }`

### Multiple Converters

You can define multiple `@MapUsing` functions in the same config object, each targeting a different property:

```kotlin
data class Src(val a: Int, val b: Int)
data class Dst(val x: String, val y: String)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "a", target = "x")
    fun convertA(v: Int): String = "a:$v"

    @MapUsing(source = "b", target = "y")
    fun convertB(v: Int): String = "b:$v"
}
```

Generated:

```kotlin
fun Src.toDst(): Dst = Dst(
    x = SrcMapper.convertA(this.a),
    y = SrcMapper.convertB(this.b)
)
```

## Whole-Source Mode

When `source` is omitted (or left blank), the function receives the entire source object. This is useful for computing a target value from multiple source properties.

```kotlin
@MapUsing(target = "targetParam")
```

### Regular Function

```kotlin
data class Src(val a: Int, val b: Int)
data class Dst(val combined: String)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(target = "combined")
    fun combine(src: Src): String = "${src.a}-${src.b}"
}
```

Generated: `combined = SrcMapper.combine(this)`

### Extension Function

```kotlin
@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(target = "combined")
    fun Src.combine(): String = "${this.a}-${this.b}"
}
```

Generated: `combined = with(SrcMapper) { this@toDst.combine() }`

### Whole-Source Mode Patterns

Whole-source mode is the right tool for several patterns where source and target fields don't line up 1:1. Each one is a single `@MapUsing(target = "...")` declaration that reads any combination of source fields.

#### Pattern 1 — Decompose one source field into N target fields

A value type on the source (like `Money`) maps onto multiple primitive fields on the target (`minorUnits` + `currency`). Declare one whole-source `@MapUsing` per target field:

```kotlin
data class Money(val amount: Long, val currency: String)
data class Product(val salePrice: Money)
data class ProductDto(val salePriceMinorUnits: Long, val salePriceCurrency: String)

@MapConfig(source = Product::class, target = ProductDto::class)
object ProductMapper {
    @MapUsing(target = "salePriceMinorUnits")
    fun Product.minorUnits(): Long = salePrice.amount

    @MapUsing(target = "salePriceCurrency")
    fun Product.currency(): String = salePrice.currency
}
```

#### Pattern 2 — Compose N source fields into one target field

The reverse direction: two flat source fields fold into a single target value type. One whole-source `@MapUsing` reads both:

```kotlin
@MapConfig(source = ProductDto::class, target = Product::class)
object ProductReverseMapper {
    @MapUsing(target = "salePrice")
    fun ProductDto.toMoney(): Money = Money(salePriceMinorUnits, salePriceCurrency)
}
```

#### Pattern 3 — Inject a constant on a target field with no source counterpart

A target field has no equivalent on the source — supply a constant via a whole-source function whose body ignores the receiver:

```kotlin
@MapConfig(source = LongAmount::class, target = Money::class)
object MoneyMapper {
    @MapUsing(target = "currency")
    fun LongAmount.fixedCurrency(): String = "HUF"
}
```

#### Pattern 4 — Coalesce a nullable source into a non-null target with a default

When the source field is nullable but the target requires a value, use whole-source mode to apply a default in one place:

```kotlin
data class CartItemDto(val packSize: Int? = null)
data class CartItem(val packSize: Int)

@MapConfig(source = CartItemDto::class, target = CartItem::class)
object CartItemMapper {
    @MapUsing(target = "packSize")
    fun CartItemDto.packSizeOrDefault(): Int = packSize ?: 1
}
```

These four patterns cover almost every "fields don't line up" case without writing manual mappers. If you find yourself reaching for a hand-written extension function or a global `@KraftConverter`, check whether one of these whole-source patterns fits first.

## Type Matching Rules

- **Property-source mode**: the function parameter type must match the source property type exactly (including nullability)
- **Whole-source mode**: the function parameter type (or extension receiver) must match the source class type
- The function return type must match the target constructor parameter type exactly
- Nullable parameters are supported when the source property is nullable
- Generic types (e.g., `List<String>`) are supported and matched including type arguments

### Nullable Parameter Example

```kotlin
data class Src(val name: String?)
data class Dst(val label: String)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "name", target = "label")
    fun convert(v: String?): String = v ?: ""
}
```

Generated: `label = SrcMapper.convert(this.name)`

Note: a nullable parameter is only valid when the source property is also nullable. A `String?` parameter with a non-null `String` source property produces a compile-time error.

### Generic Type Example

```kotlin
data class Src(val tags: List<String>)
data class Dst(val tagStr: String)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "tags", target = "tagStr")
    fun convert(tags: List<String>): String = tags.joinToString()
}
```

Generated: `tagStr = SrcMapper.convert(this.tags)`

## Direction Parameter

When using `@MapReverse`, you may need both a forward and a reverse converter for the same property. The `direction` parameter on `@MapUsing` controls which mapping direction the converter applies to.

| Value | Meaning |
|---|---|
| `ConverterDirection.AUTO` (default) | Kraft infers the direction from the converter's parameter type |
| `ConverterDirection.FORWARD` | Converter is used only for source -> target |
| `ConverterDirection.REVERSE` | Converter is used only for target -> source |

### Auto-Detection

By default (`AUTO`), Kraft matches the converter's parameter type against the source property types of each direction. This works automatically when the types differ between source and target:

```kotlin
data class Entity(val id: Int, val name: String)
data class Dto(val id: String, val name: String)

@MapReverse
@MapConfig(source = Entity::class, target = Dto::class)
object EntityMapper {
    @MapUsing(source = "id", target = "id")  // param Int matches Entity.id -> forward
    fun intToString(v: Int): String = v.toString()

    @MapUsing(source = "id", target = "id")  // param String matches Dto.id -> reverse
    fun stringToInt(v: String): Int = v.toInt()
}
```

### Explicit Direction

Use explicit `direction` when auto-detection cannot disambiguate (e.g. both sides have the same type for a property) or when you prefer to be explicit:

```kotlin
@MapUsing(source = "id", target = "id", direction = ConverterDirection.FORWARD)
fun forwardConvert(v: Int): String = v.toString()

@MapUsing(source = "id", target = "id", direction = ConverterDirection.REVERSE)
fun reverseConvert(v: String): Int = v.toInt()
```

See [Reverse Mapping -- Converters in Reverse](reverse-mapping.md#converters-in-reverse) for the full reverse converter workflow.

## Error Cases

| Condition | Result |
|---|---|
| `target` is blank or empty | Compile-time error |
| `source` property name does not exist on the source class | Compile-time error |
| `target` property name does not exist on the target class | Compile-time error |
| Parameter type does not match the source property type | Compile-time error ("mismatch") |
| Return type does not match the target constructor parameter type | Compile-time error ("mismatch") |
| Nullable parameter with non-nullable source property | Compile-time error ("mismatch") |
| Generic type argument mismatch (e.g., `List<Int>` vs `List<String>`) | Compile-time error ("mismatch") |
| Two `@MapUsing` functions targeting the same property in the same direction | Compile-time error ("Multiple") |
| Whole-source function parameter type does not match the source class | Compile-time error ("source class") |
| Explicit `direction` mismatches the converter's types | Compile-time error ("mismatch") |

## Global Converters

`@KraftConverter` registers a top-level extension function as a globally discoverable converter. When a generated mapper finds two properties whose types differ but whose `(sourceType → targetType)` pair matches a registered converter, the converter is invoked automatically — no per-`@MapConfig` `@MapUsing` is needed.

### Declaring a converter

The annotated function must be a top-level extension; the receiver is the source type, the return is the target type. No value parameters are allowed.

The receiver and return types must not be parameterized: converters are registered by their raw `(source → target)` class pair, so type arguments cannot be matched — `fun List<Int>.toCsv(): String` is rejected even though the pair is fully concrete. This is a `@KraftConverter`-only limitation: `List`/`Set` **properties** whose element types map are converted element-wise automatically, and `@MapUsing` functions accept parameterized types (matched including type arguments).

```kotlin
package com.example.util

import com.blu3berry.kraft.config.KraftConverter
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@KraftConverter
fun Uuid.toStringValue(): String = toString()

@OptIn(ExperimentalUuidApi::class)
@KraftConverter
fun String.toUuidValue(): Uuid = Uuid.parse(this)
```

Any mapper in the same module — and any mapper in a downstream module that depends on this one — can now translate between `Uuid` and `String` automatically:

```kotlin
data class User(val id: Uuid, val name: String)
data class UserDto(val id: String, val name: String)

@MapConfig(source = User::class, target = UserDto::class)
object UserMapper

// Generated:
// public fun User.toUserDto(): UserDto = UserDto(
//   id = this.id.toStringValue(),
//   name = this.name,
// )
```

### Resolution order

For each target property, Kraft walks the resolver chain in this order:

1. `@MapUsing` on the `@MapConfig` object (explicit local override).
2. Same-module `@KraftConverter` extension functions.
3. Classpath `@KraftConverter` extensions discovered from upstream modules.
4. The existing type-mismatch error path when no converter applies.

Because `@MapUsing` runs first, you can always override a globally-registered converter for one specific mapping without removing the global declaration.

### Cross-module discovery

When KSP processes a module that contains `@KraftConverter` functions, it generates a small wrapper file at `kraft.generated.registry.Converters_<moduleId>.kt`. Each wrapper is annotated with `@KraftConverterDelegate`, is named deterministically after its source/target type pair, and trampolines to your original function.

Consuming compilations discover the wrappers **by name**: for each type pair that no same-module converter resolved, the consumer constructs the expected wrapper name from the pair and resolves it by qualified name. This is the only lookup that works on both JVM class-file classpaths and klib (KMP metadata) classpaths — package enumeration returns nothing for klib dependencies — and it is why the wrapper names are derived from the type pair rather than from your function's name. No full classpath scan, no service-loader files.

You generally do not need to think about the delegate file. Two things to know:

- The wrapper is generated automatically; do not write `@KraftConverterDelegate` yourself.
- If multiple modules contribute converters that all end up on the same compile classpath, set the [`kraft.moduleId`](ksp-options.md#kraftmoduleid) processor option in each producing module so the delegate file names do not collide (the id is also embedded in the delegate and used to name the conflicting modules in ambiguity errors).

> **Version rule:** every module on a compile classpath — converter producers and consumers alike — must build with the **same Kraft version**. The wrapper naming scheme is internal wiring and may change between releases; a version mismatch (including artifacts published with Kraft ≤ 0.10.x) makes upstream converters invisible, and the build fails with the normal "no converter found" type-mismatch error — never with wrong generated code. Rebuild the producing module on the current Kraft version, or declare a local converter copy.
>
> Kraft **cannot distinguish** a version-mismatched delegate from a converter that simply doesn't exist — by-name lookup either finds the expected name or it doesn't, and enumerating the registry package to look for old-style names is not supported on klib classpaths (it's the API limitation that motivated by-name discovery in the first place). So if a converter you *know* an upstream module declares comes up "missing", check the Kraft versions on both sides before anything else.

### Disabling for a single config

Set `useGlobalConverters = false` on `@MapConfig` to skip both the same-module and classpath registries for that one mapper. Type-mismatched properties then have to be claimed by an explicit `@MapUsing`.

```kotlin
@MapConfig(
    source = User::class,
    target = UserDto::class,
    useGlobalConverters = false
)
object UserMapper {
    @MapUsing(source = "id", target = "id")
    fun explicitConvert(id: Uuid): String = id.toString()
}
```

The default is `true`. Class-level `@MapFrom` / `@MapTo` mappings always use global converters unless an attached `@MapConfig` opts out.

### Ambiguity

Two `@KraftConverter` functions registering the same `(sourceType, targetType)` pair within one module produce a KSP error pointing at both candidates. The same applies across the classpath: if two upstream modules register the same pair and you don't shadow it locally, KSP errors out and asks you to resolve the conflict by adding a same-module `@KraftConverter` or a per-property `@MapUsing`.

### Opt-in propagation

When the source class, target class, `@MapConfig` object, `@MapUsing` function, or any invoked `@KraftConverter` carries an `@OptIn(...)` or an `@RequiresOptIn`-meta-annotated marker, Kraft copies a deduplicated `@OptIn(...)` onto the generated mapper function. This keeps experimental APIs like `kotlin.uuid.Uuid` from forcing every consumer of the generated mapper to add an opt-in at the call site.

Both placements are honoured: a marker on the declaration itself **and** a file-level `@file:OptIn(...)` on the file that declares it. That second form matters for generated DTOs — code generators differ on which one they emit, and on Kraft < 0.13.0 the file-level form was silently dropped, so the same models produced a compiling mapper with one generator version and an opt-in error with another. On 0.13.0+ the generated mapper carries its own opt-ins either way, and you do not need a module-wide `freeCompilerArgs += "-opt-in=..."` to compile it.

### Restrictions and caveats

- Only top-level extension functions are accepted; member functions and free-standing functions with a value parameter are rejected at compile time.
- Lookup matches the source/target qualified names *and* nullability, with one lift: a `X? → Y?` property pair reuses a registered non-null `X → Y` converter through a safe call (`this.field?.toY()`), so `Uuid → String` covers `Uuid? → String?` without a second declaration. A **nullable source with a non-null target is deliberately not lifted** — a safe call yields `Y?`, and unlike collections (which fall back to `emptyList()`) a scalar has no natural empty value. For that pair, declare an explicit `X? → Y` converter, or use a whole-source `@MapUsing` that supplies the default (see [Pattern 4](#pattern-4-coalesce-a-nullable-source-into-a-non-null-target-with-a-default)).
- Kraft does not ship a built-in primitives or stdlib converter set yet. Each module that needs `Int → Long`, `Uuid → String`, etc. must declare the converters itself.
