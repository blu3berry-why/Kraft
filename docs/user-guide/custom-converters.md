# Custom Converters

Use `@MapUsing` inside a `@MapConfig`-annotated object to provide custom conversion logic for individual target properties. Kraft calls your function during code generation and wires the result into the generated constructor call.

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
| Two `@MapUsing` functions targeting the same property | Compile-time error ("Multiple") |
| Whole-source function parameter type does not match the source class | Compile-time error ("source class") |
