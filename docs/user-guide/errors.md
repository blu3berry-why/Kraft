# Errors Reference

Every error and warning Kraft prints during compilation has a stable code. This page lists each code with its cause and fix.

## Reading a Kraft diagnostic

A Kraft diagnostic looks like this:

```text
----------------------------------------
Kraft KSP Warning
----------------------------------------

[KRAFT-W001] <what went wrong>
<details: the types and properties involved, and how to fix it>

----------------------------------------
See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#kraft-w001
```

- The code in square brackets on the first line identifies the diagnostic. `KRAFT-E###` codes are errors, which fail compilation. `KRAFT-W###` codes are warnings, which do not.
- The `See:` line on the last line links to that code's entry below.
- A code keeps its meaning across releases. The message wording can change, but the code stays the same, so it is safe to search for a code in logs, issues or CI output.

## Suppressing a warning

A warning can be silenced on the declaration it points at, using Kotlin's standard `@Suppress` with the code:

```kotlin
@Suppress("KRAFT-W001")
fun List<RowDto>?.toRows(): List<Row> = this.orEmpty().filter { it.visible }.map { it.toRow() }
```

- The code must match exactly, including case: `@Suppress("kraft-w001")` does not suppress anything.
- A suppression only covers the code it names. `@Suppress("KRAFT-W002")` does not silence `KRAFT-W001`.
- The annotation goes on the declaration itself. A `@Suppress` on the enclosing class or file is not read.
- Errors cannot be suppressed: they mean Kraft cannot generate valid code.

## Errors

Examples omit imports. The annotations live in `com.blu3berry.kraft.config` and `com.blu3berry.kraft.mapping`.

### KRAFT-E001 — Annotation on the wrong target {#kraft-e001}

**Severity:** error

**Cause:** A Kraft annotation is placed on a declaration kind it does not support, for example `@MapFrom` or `@MapTo` on an object or interface.

```kotlin
data class Source(val value: Int)

@MapFrom(Source::class) // error: @MapFrom is only valid on class declarations
object NotAClass
```

**Fix:**

- Move the annotation onto a class, for example `@MapFrom(Source::class) data class Target(...)`.
- For a `@MapConfig` or `@MapEnum`, put it on an `object`.

### KRAFT-E002 — Missing annotation argument {#kraft-e002}

**Severity:** error

**Cause:** A required argument of a Kraft annotation could not be read from the annotation. Valid Kotlin source cannot normally produce this, so it points at a processor and annotations version mismatch or a broken classpath.

```kotlin
// No minimal input is known; the message names the annotation and the missing argument.
```

**Fix:**

- Provide the missing argument. The message shows an example that matches the argument's kind: `@MapConfig(source = MyType::class)` for a class, `@MapUsing(target = "propertyName")` for a string, or the enum's first entry such as `ConverterDirection.AUTO`.
- Arguments that have a default (such as `@MapUsing`'s `source` and `direction`) are never reported here; Kraft applies the declared default when the compiler leaves them out.
- Make sure the Kraft annotations artifact and the Kraft KSP processor are the same version.

### KRAFT-E003 — Argument is not a class literal {#kraft-e003}

**Severity:** error

**Cause:** An annotation argument that must be a class literal (`KClass<*>`) has a different type. Valid Kotlin source cannot normally produce this, so it points at a processor and annotations version mismatch.

```kotlin
// No minimal input is known; the message names the annotation, the argument and the type found.
```

**Fix:**

- Use a class literal like `MyType::class`.
- Make sure the Kraft annotations artifact and the Kraft KSP processor are the same version.

### KRAFT-E004 — Required property has no mapping source {#kraft-e004}

**Severity:** error

**Cause:** A non-nullable target property has no default value and no source property to map from.

```kotlin
data class User(val name: String)

@MapFrom(User::class)
data class UserDto(
    val name: String,
    val age: Int, // error: no 'age' in User, not nullable, no default
)
```

**Fix:**

- Add `@MapField("sourceName")` to the target property.
- Or add a config override: `FieldMapping(source = "sourceName", target = "age")`.
- Or make the property nullable.
- Or provide a default value in the target constructor.

### KRAFT-E005 — Property type mismatch {#kraft-e005}

**Severity:** error

**Cause:** A source property and the target property it maps to have different types, and no converter bridges them.

```kotlin
data class User(val id: Int)

@MapFrom(User::class)
data class UserDto(val id: String) // error: Int -> String has no converter
```

**Fix:**

- Register a `@KraftConverter` for the pair: `@KraftConverter fun Int.toString(): String = ...`.
- Or, if both types are enums, add `@MapEnum(source = A::class, target = B::class)`.
- Or override just this property with `@MapUsing` on the `@MapConfig` object.
- Or align the declared types, including nullability.
- When only nullability differs (`String?` to `String`), make the target nullable, give it a default and add `@MapIgnore`, or choose the fallback with a whole-source `@MapUsing(target = ...)`.

#### Map values that are nullable

When two maps differ only because the source value is nullable (`Map<String, ItemSource?>` into `Map<String, ItemDto>`), the error adds the hint `Map values cannot be dropped like list elements: removing a null value would also remove its key.` A list would drop the null element with `mapNotNull`, but a map cannot drop a value without dropping its key. Make the target values nullable, or filter first with a whole-source `@MapUsing`.

#### Duplicated generated types

When the two types share a simple name, live under different enclosing classes (`AuthResponse.User.Role` and `AuthMe200Response.Role`) and have identical enum entries or identical primary-constructor parameter names, the error opens with `Types differ only by enclosing class: ...`. That shape usually comes from a code generator (for example kmpgen) inlining the same `$ref` twice, so the real fix is in the generator. To bridge the pair anyway, use `@MapEnum(source = ..., target = ...)` for enums, or a `@MapConfig` or `@KraftConverter` for classes. The usual "How to fix" list still follows the hint. See [API-first client design](api-first-client-design.md) for how to structure generated clients.

### KRAFT-E006 — Invalid @MapField override {#kraft-e006}

**Severity:** error

**Cause:** `@MapField` names a source property that does not exist in the source type.

```kotlin
data class User(val fullName: String)

@MapFrom(User::class)
data class UserDto(
    @MapField("fulName") // error: User has no 'fulName'
    val name: String,
)
```

**Fix:**

- Correct the `@MapField` name; the message lists the available source properties and close matches.
- Or make sure the source class declares the referenced property.

### KRAFT-E007 — Missing primary constructor {#kraft-e007}

**Severity:** error

**Cause:** The target type of a mapping declares no primary constructor, so Kraft cannot build an instance.

```kotlin
data class User(val name: String)

@MapFrom(User::class)
class UserDto { // error: only a secondary constructor
    constructor(name: String)
}
```

**Fix:**

- Add a primary constructor to the target type: `data class UserDto(val name: String)`.

### KRAFT-E008 — Constructor parameter without property {#kraft-e008}

**Severity:** error

**Cause:** A constructor parameter of the target type has no matching property, so Kraft cannot read its type.

```kotlin
data class User(val name: String)

@MapFrom(User::class)
class UserDto(name: String) // error: 'name' is a parameter, not a property
```

**Fix:**

- Declare the parameter as a property: `val name` or `var name`.

### KRAFT-E009 — Unsupported constructor parameter type {#kraft-e009}

**Severity:** error

**Cause:** A constructor parameter of the target type has a type that is not a class declaration, such as a type parameter.

```kotlin
data class Source(val value: Int)

@MapConfig(source = Source::class, target = Target::class)
object SourceMapper

class Target<T>(val value: Int, val extra: T) // error: T is a type parameter, not a class
```

**Fix:**

- Use a concrete class type for the parameter.
- Or give the parameter a default value; an unsupported parameter with a default is skipped.

### KRAFT-E010 — Constructor and property mismatch {#kraft-e010}

**Severity:** error

**Cause:** A target constructor parameter was skipped as unsupported and has no default value, so the generated constructor call cannot line up with the properties. It is reported together with the error that explains the skipped parameter (for example `KRAFT-E009`).

```kotlin
data class Source(val value: Int)

@MapConfig(source = Source::class, target = Target::class)
object SourceMapper

class Target<T>(val value: Int, val extra: T) // error: KRAFT-E009 for 'extra', then this
```

**Fix:**

- Fix the error reported for the skipped parameter first.
- Ensure each constructor argument has a matching property and a supported type.

### KRAFT-E011 — Unsupported source property type {#kraft-e011}

**Severity:** error

**Cause:** A source property has a type that does not resolve to a concrete class declaration, such as a type parameter.

```kotlin
// No minimal input is known; the message names the property, its resolved type and
// the declaration kind.
```

**Fix:**

- Use a concrete type for the property in the source type.
- Or handle the property with a `@MapUsing` converter.

### KRAFT-E012 — Unmapped enum entries {#kraft-e012}

**Severity:** error

**Cause:** A source enum entry has no matching target entry, and no `FieldMapping` covers it.

```kotlin
enum class Status { ACTIVE, ARCHIVED }
enum class StatusDto { ACTIVE }

@MapEnum(source = Status::class, target = StatusDto::class) // error: ARCHIVED is unmapped
object StatusMapper
```

**Fix:**

- Add a `FieldMapping` for each unmapped entry: `fieldMappings = [FieldMapping(source = "ARCHIVED", target = "ACTIVE")]`.
- Or rename the target entry to match the source name for automatic 1:1 mapping.

### KRAFT-E013 — Ignored property has no default {#kraft-e013}

**Severity:** error

**Cause:** `@MapIgnore` or `@MapIgnoreField` ignores a target property that has no default value, so the generated constructor call would not compile.

```kotlin
data class User(val name: String)

@MapFrom(User::class)
data class UserDto(
    val name: String,
    @MapIgnore val age: Int, // error: ignored but has no default
)
```

**Fix:**

- Add a default value to the property in the target constructor.
- Or remove the ignore declaration.

### KRAFT-E014 — Nullable nested source {#kraft-e014}

**Severity:** error

**Cause:** A nullable nested source property maps to a non-null nested target property, so the generated nested call could fail at runtime.

```kotlin
data class Address(val city: String)
data class AddressDto(val city: String)
data class User(val address: Address?)

@MapFrom(User::class)
data class UserDto(val address: AddressDto) // error: nullable source, non-null target
```

**Fix:**

- Mark the target property as nullable.
- Or provide a `@MapUsing` converter that handles the null case.

### KRAFT-E015 — Nested type not mappable {#kraft-e015}

**Severity:** error

**Cause:** A nested mapping involves a type that is not a concrete class with a primary constructor, such as an interface, an abstract or sealed class, or a type parameter. For a collection property, the message names the element type that is not mappable.

```kotlin
interface Shape
data class Circle(val r: Int) : Shape
data class Source(val shape: Circle)

@MapFrom(Source::class)
data class Target(
    @MapNested val shape: Shape, // error: Shape is an interface
)
```

**Fix:**

- Use a `@MapUsing` converter for this property.
- Or make sure the collection's element type is a concrete mappable class.
- Or declare an explicit `@MapConfig` with a `@NestedMapping` for the pair.

### KRAFT-E016 — Ambiguous nested mapping {#kraft-e016}

**Severity:** error

**Cause:** Several `@NestedMapping` declarations in a `@MapConfig` share the same target type, so Kraft cannot pick one.

```kotlin
@MapConfig(
    source = Order::class,
    target = OrderDto::class,
    nestedMappings = [
        NestedMapping(source = Home::class, target = AddressDto::class),
        NestedMapping(source = Work::class, target = AddressDto::class), // error: same target twice
    ],
)
object OrderMapper
```

**Fix:**

- Remove duplicate `@NestedMapping` entries so only one targets the type.
- Or annotate the target property with `@MapNested` to resolve the ambiguity explicitly.

### KRAFT-E017 — Ambiguous nested source property {#kraft-e017}

**Severity:** error

**Cause:** Several source properties have the type an explicit `@NestedMapping` names as its source, so Kraft cannot pick the property.

```kotlin
data class Order(val billing: Address, val shipping: Address)

@MapConfig(
    source = Order::class,
    target = OrderDto::class,
    nestedMappings = [NestedMapping(source = Address::class, target = AddressDto::class)], // error: two Address properties
)
object OrderMapper
```

**Fix:**

- Annotate the target property with `@MapNested(sourceName = "propertyName")` to resolve the ambiguity explicitly.

### KRAFT-E018 — Nested mapping source not found {#kraft-e018}

**Severity:** error

**Cause:** An explicit `@NestedMapping` names a source type for which the source class has no property.

```kotlin
data class Order(val id: Int)

@MapConfig(
    source = Order::class,
    target = OrderDto::class,
    nestedMappings = [NestedMapping(source = Address::class, target = AddressDto::class)], // error: Order has no Address property
)
object OrderMapper
```

**Fix:**

- Add a property of the nested source type to the source class.
- Or remove the `@NestedMapping` declaration if it is no longer needed.

### KRAFT-E019 — Duplicate mapping {#kraft-e019}

**Severity:** error

**Cause:** Several `@MapConfig` objects declare the same source and target pair. Only one mapping per type pair is allowed.

```kotlin
@MapConfig(source = User::class, target = UserDto::class)
object FirstMapper

@MapConfig(source = User::class, target = UserDto::class) // error: pair already mapped by FirstMapper
object SecondMapper
```

**Fix:**

- Remove one of the `@MapConfig` objects, or merge their settings into one.

### KRAFT-E020 — Circular nested mapping {#kraft-e020}

**Severity:** error

**Cause:** Nested mappings reference each other in a cycle, which would generate mappers that call each other forever.

```kotlin
data class NodeSource(val value: Int, val next: NodeSource?)
data class NodeDto(val value: Int, val next: NodeDto?)

data class Container(val node: NodeSource)

@MapFrom(Container::class)
data class ContainerDto(val node: NodeDto) // error: NodeSource -> NodeDto nests itself
```

**Fix:**

- Break the cycle with a `@MapUsing` converter on one side.

### KRAFT-E021 — Nested mapper could not be built {#kraft-e021}

**Severity:** error

**Cause:** A mapper depends on a nested mapper that could not be built, so its generated code would not compile. It is reported together with the error that made the nested mapper fail.

```kotlin
data class Address(val city: String)
data class AddressDto(val city: String, val zip: String) // zip has no source: KRAFT-E004
data class Container(val address: Address)

@MapFrom(Container::class)
data class ContainerDto(val address: AddressDto) // error: the Address -> AddressDto mapper failed
```

**Fix:**

- Fix the error reported for the nested mapper first.
- Or map the property with a `@MapUsing` converter.

### KRAFT-E022 — @MapIgnoreField property not found {#kraft-e022}

**Severity:** error

**Cause:** `@MapIgnoreField` names a property that does not exist in the target constructor, for a `TARGET` or `SOURCE` direction.

```kotlin
@MapConfig(
    source = User::class,
    target = UserDto::class,
    ignoredMappings = [MapIgnoreField("nonExistent", IgnoreSide.TARGET)], // error: UserDto has no 'nonExistent'
)
object UserMapper
```

**Fix:**

- Correct the property name; the message lists the available target properties.
- Or remove the `@MapIgnoreField` declaration.

### KRAFT-E023 — Missing reverse converter {#kraft-e023}

**Severity:** error

**Cause:** `@MapReverse` reverses a mapping that uses a forward `@MapUsing` converter, but no reverse converter is defined.

```kotlin
@MapConfig(source = Source::class, target = Target::class)
@MapReverse
object SourceMapper {
    @MapUsing(source = "value", target = "text")
    fun format(v: Int): String = v.toString() // error: no reverse converter for text -> value
}
```

**Fix:**

- Add a reverse `@MapUsing` function in the same config object.
- Or add `@MapIgnoreField("name", direction = IgnoreSide.SOURCE)` to skip the property in reverse.

### KRAFT-E024 — Config override of unknown property {#kraft-e024}

**Severity:** error

**Cause:** A config-level `FieldMapping` names a source property that does not exist.

```kotlin
@MapConfig(
    source = User::class,
    target = UserDto::class,
    fieldMappings = [FieldMapping(source = "ghost", target = "name")], // error: User has no 'ghost'
)
object UserMapper
```

**Fix:**

- Correct the source name in the `FieldMapping`; the message lists the available source properties.

### KRAFT-E025 — Multiple @MapUsing converters for one property {#kraft-e025}

**Severity:** error

**Cause:** More than one `@MapUsing` converter targets the same property. Only one converter per target property is allowed.

```kotlin
@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "value", target = "text")
    fun convertFirst(v: Int): String = v.toString()

    @MapUsing(source = "value", target = "text") // error: 'text' already has a converter
    fun convertSecond(v: Int): String = "duplicate"
}
```

**Fix:**

- Keep one `@MapUsing` converter per target property and remove the others.

### KRAFT-E026 — Unknown property in @MapUsing {#kraft-e026}

**Severity:** error

**Cause:** `@MapUsing` names a source or target property that does not exist.

```kotlin
@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "ghost", target = "text") // error: Src has no 'ghost'
    fun convert(v: Int): String = v.toString()
}
```

**Fix:**

- Correct the property name; the message lists the available properties.
- Or add the missing property to the source or target class.

### KRAFT-E027 — @MapNested source property not found {#kraft-e027}

**Severity:** error

**Cause:** `@MapNested` resolves to a source property name that does not exist in the source type.

```kotlin
@MapFrom(User::class)
data class UserDto(
    @MapNested(sourceName = "typo") // error: User has no 'typo'
    val address: AddressDto,
)
```

**Fix:**

- Correct the `sourceName`, or remove it to use the target property's own name.
- Or add the property to the source class.

### KRAFT-E028 — @MapFrom and @MapTo on one class {#kraft-e028}

**Severity:** error

**Cause:** A class carries both `@MapFrom` and `@MapTo`. Only one mapping annotation is allowed per class.

```kotlin
@MapFrom(Source::class)
@MapTo(Other::class) // error: both @MapFrom and @MapTo
data class Both(val value: Int)
```

**Fix:**

- Keep one of the two annotations on the class.
- Declare the second direction on the other class, or use a `@MapConfig` object.

### KRAFT-E029 — @MapReverse without a mapping annotation {#kraft-e029}

**Severity:** error

**Cause:** `@MapReverse` is not paired with a mapping annotation (`@MapFrom`, `@MapTo`, `@MapConfig` or `@MapEnum`) on the same declaration.

```kotlin
@MapReverse // error: nothing to reverse
data class Orphan(val value: Int)
```

**Fix:**

- Add `@MapFrom`, `@MapTo`, `@MapConfig` or `@MapEnum` to the same declaration.
- Or remove `@MapReverse`.

### KRAFT-E030 — Ambiguous classpath converter {#kraft-e030}

**Severity:** error

**Cause:** Two dependency modules register a `@KraftConverter` for the same source and target pair, so Kraft cannot choose between them.

```kotlin
// module :upstream-a
@KraftConverter fun Int.toLabel(): String = "a$this"

// module :upstream-b
@KraftConverter fun Int.toLabel(): String = "b$this"

// module :app depends on both and maps an Int to a String // error: ambiguous
```

**Fix:**

- Add a same-module `@KraftConverter` for the pair; it takes precedence over dependencies.
- Or use a per-property `@MapUsing`.

### KRAFT-E031 — @MapUsing REVERSE without @MapReverse {#kraft-e031}

**Severity:** error

**Cause:** A `@MapUsing(direction = REVERSE)` converter sits in a `@MapConfig` that is not annotated with `@MapReverse`, so the converter would never be used.

```kotlin
@MapConfig(source = Src::class, target = Dst::class) // no @MapReverse
object SrcMapper {
    @MapUsing(source = "text", target = "value", direction = ConverterDirection.REVERSE) // error: no effect
    fun parse(s: String): Int = s.toInt()
}
```

**Fix:**

- Remove the `direction` argument.
- Or add `@MapReverse` to the config object.

### KRAFT-E032 — @MapUsing internal lookup failure {#kraft-e032}

**Severity:** error

**Cause:** Kraft validated a `@MapUsing` property name and then could not find the property in its own lookup. This is an internal inconsistency, not a mistake in your code.

```kotlin
// No minimal input is known; the message names the converter and the property.
```

**Fix:**

- Report it on the Kraft issue tracker with the converter and type names from the message.
- Work around it by moving the property mapping to a `FieldMapping` or a different `@MapUsing`.

### KRAFT-E033 — @MapUsing without a target {#kraft-e033}

**Severity:** error

**Cause:** `@MapUsing` has an empty `target` value, so Kraft does not know which property it converts.

```kotlin
@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(target = "") // error: empty target
    fun convert(src: Src): String = ""
}
```

**Fix:**

- Set `target` to the name of the target property the function produces.

### KRAFT-E034 — @MapUsing unknown direction {#kraft-e034}

**Severity:** error

**Cause:** `@MapUsing` has a `direction` that names an enum entry the processor does not know. Valid source cannot normally produce this, so it points at a processor and annotations version mismatch.

```kotlin
// No minimal input is known; the message names the direction found.
```

**Fix:**

- Use one of the `ConverterDirection` values: `AUTO`, `FORWARD` or `REVERSE`.
- Make sure the Kraft annotations artifact and the Kraft KSP processor are the same version.

### KRAFT-E035 — Whole-source converter parameter mismatch {#kraft-e035}

**Severity:** error

**Cause:** A whole-source `@MapUsing` converter (no `source`) takes a parameter whose type is not the source class of the mapping.

```kotlin
@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(target = "label")
    fun format(other: Other): String = "" // error: parameter must be Src
}
```

**Fix:**

- Change the parameter (or extension receiver) type to the source class of the `@MapConfig`.
- Or name a source property with `@MapUsing(source = ...)` for a single-property converter.

### KRAFT-E036 — @MapUsing return type mismatch {#kraft-e036}

**Severity:** error

**Cause:** The return type of a `@MapUsing` function does not match the type of the target property it fills.

```kotlin
data class Dst(val text: String)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "value", target = "text")
    fun convert(v: Int): Int = v // error: text is a String
}
```

**Fix:**

- Change the function's return type to the target property's type.
- Or point `target` at a property of the returned type.

### KRAFT-E037 — @MapUsing extension with parameters {#kraft-e037}

**Severity:** error

**Cause:** A `@MapUsing` extension function declares value parameters. Only the receiver is used as input.

```kotlin
@MapUsing(target = "text")
fun Src.format(prefix: String): String = prefix + value // error: extension takes no parameters
```

**Fix:**

- Remove the value parameters from the extension function; read what you need from the receiver.

### KRAFT-E038 — Invalid @MapUsing signature {#kraft-e038}

**Severity:** error

**Cause:** A `@MapUsing` function is neither an extension function nor a function with exactly one parameter.

```kotlin
@MapUsing(target = "text")
fun convert(a: Int, b: Int): String = "" // error: two parameters, not an extension
```

**Fix:**

- Give the function exactly one parameter, or make it an extension function.

### KRAFT-E039 — @MapUsing without a return type {#kraft-e039}

**Severity:** error

**Cause:** A `@MapUsing` function has no return type that Kraft can read. Kotlin infers return types even when they are not written, so this is a defensive check.

```kotlin
// No minimal input is known; the message points at the function.
```

**Fix:**

- Declare an explicit return type on the function.

### KRAFT-E040 — @MapUsing type is not a class {#kraft-e040}

**Severity:** error

**Cause:** The parameter or return type of a `@MapUsing` function is not a class, for example a type parameter.

```kotlin
@MapUsing(target = "text")
fun <T> convert(v: T): String = "" // error: parameter type is a type parameter
```

**Fix:**

- Use concrete class types for the parameter and the return type.

### KRAFT-E041 — @MapUsing parameter type mismatch {#kraft-e041}

**Severity:** error

**Cause:** The parameter type of a property-source `@MapUsing` function does not match the type of the source property it reads.

```kotlin
data class Src(val value: Int)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper {
    @MapUsing(source = "value", target = "text")
    fun convert(v: String): String = v // error: value is an Int
}
```

**Fix:**

- Change the function's parameter type to the source property's type.
- Or point `source` at a property of the parameter's type.

### KRAFT-E042 — @MapIgnoreField with a blank name {#kraft-e042}

**Severity:** error

**Cause:** `@MapIgnoreField` has a blank `name`.

```kotlin
@MapConfig(
    source = User::class,
    target = UserDto::class,
    ignoredMappings = [MapIgnoreField(name = "")], // error: blank name
)
object UserMapper
```

**Fix:**

- Set `name` to the property to ignore.

### KRAFT-E043 — @MapIgnoreField unknown direction {#kraft-e043}

**Severity:** error

**Cause:** `@MapIgnoreField` has a `direction` that names an enum entry the processor does not know. Valid source cannot normally produce this, so it points at a processor and annotations version mismatch.

```kotlin
// No minimal input is known; the message names the direction and the property.
```

**Fix:**

- Use one of the `IgnoreSide` values: `TARGET`, `SOURCE` or `BOTH`.
- Make sure the Kraft annotations artifact and the Kraft KSP processor are the same version.

### KRAFT-E044 — @MapEnum type is not an enum {#kraft-e044}

**Severity:** error

**Cause:** A `@MapEnum` source or target (or a `from`/`to` type given to the enum generator) is not an enum class.

```kotlin
data class NotAnEnum(val value: Int)
enum class Status { ACTIVE }

@MapEnum(source = NotAnEnum::class, target = Status::class) // error: source is not an enum
object StatusMapper
```

**Fix:**

- Point `@MapEnum` `source` and `target` at enum classes.

### KRAFT-E045 — @MapEnum duplicate source entry {#kraft-e045}

**Severity:** error

**Cause:** A source enum entry appears more than once in the `fieldMappings` of a `@MapEnum`.

```kotlin
@MapEnum(
    source = Status::class,
    target = StatusDto::class,
    fieldMappings = [
        FieldMapping(source = "ACTIVE", target = "ON"),
        FieldMapping(source = "ACTIVE", target = "OFF"), // error: ACTIVE mapped twice
    ],
)
object StatusMapper
```

**Fix:**

- Keep one `FieldMapping` per source enum entry.

### KRAFT-E046 — @MapEnum malformed field mapping {#kraft-e046}

**Severity:** error

**Cause:** A `FieldMapping` inside `@MapEnum` has a `source` or `target` that is missing or not a String.

```kotlin
@MapEnum(
    source = Status::class,
    target = StatusDto::class,
    fieldMappings = [FieldMapping(source = 1, target = "ON")], // error: source must be a String
)
object StatusMapper
```

**Fix:**

- Give both `source` and `target` as String literals naming enum entries.

### KRAFT-E047 — @MapEnum unknown source entry {#kraft-e047}

**Severity:** error

**Cause:** A `FieldMapping` in `@MapEnum` names a source entry that is not a value of the source enum.

```kotlin
@MapEnum(
    source = Status::class,
    target = StatusDto::class,
    fieldMappings = [FieldMapping(source = "MISSING", target = "ON")], // error: Status has no MISSING
)
object StatusMapper
```

**Fix:**

- Correct the `source` name to an entry of the source enum.

### KRAFT-E048 — @MapEnum unknown target entry {#kraft-e048}

**Severity:** error

**Cause:** A `FieldMapping` in `@MapEnum` names a target entry that is not a value of the target enum.

```kotlin
@MapEnum(
    source = Status::class,
    target = StatusDto::class,
    fieldMappings = [FieldMapping(source = "ACTIVE", target = "MISSING")], // error: StatusDto has no MISSING
)
object StatusMapper
```

**Fix:**

- Correct the `target` name to an entry of the target enum.

### KRAFT-E049 — @KraftConverter with generic types {#kraft-e049}

**Severity:** error

**Cause:** A `@KraftConverter` has a parameterized receiver or return type. Converters are registered by their raw source and target class pair, so type arguments cannot be matched.

```kotlin
@KraftConverter
fun List<Int>.toLabel(): String = joinToString() // error: List<Int> has a type argument
```

**Fix:**

- Rely on element-wise conversion: `List`/`Set` properties whose element types map are already converted without a `@KraftConverter`.
- For any other parameterized conversion, use a per-property `@MapUsing` on the mapper.

### KRAFT-E050 — Ambiguous @KraftConverter {#kraft-e050}

**Severity:** error

**Cause:** Two `@KraftConverter` functions in the same module register the same source and target pair.

```kotlin
@KraftConverter fun Int.toLabel(): String = "a$this"
@KraftConverter fun Int.toLabelAgain(): String = "b$this" // error: Int -> String already registered
```

**Fix:**

- Remove one of the two converters.
- Or wrap the alternative call site with `@MapUsing`.

### KRAFT-E051 — @KraftConverter not top-level {#kraft-e051}

**Severity:** error

**Cause:** A `@KraftConverter` function is declared inside a class or object instead of at the top level of a file.

```kotlin
object Converters {
    @KraftConverter
    fun Int.toLabel(): String = "$this" // error: member function
}
```

**Fix:**

- Move the function to the top level of a file.

### KRAFT-E052 — @KraftConverter not an extension {#kraft-e052}

**Severity:** error

**Cause:** A `@KraftConverter` function has no receiver. The receiver type is the source type of the conversion.

```kotlin
@KraftConverter
fun toLabel(value: Int): String = "$value" // error: not an extension function
```

**Fix:**

- Turn the function into an extension: `fun Int.toLabel(): String`.

### KRAFT-E053 — @KraftConverter with parameters {#kraft-e053}

**Severity:** error

**Cause:** A `@KraftConverter` extension function declares value parameters. Only the receiver is used as input.

```kotlin
@KraftConverter
fun Int.toLabel(prefix: String): String = prefix + this // error: parameter not allowed
```

**Fix:**

- Remove the value parameters; read what you need from the receiver.

### KRAFT-E054 — @KraftConverter without a return type {#kraft-e054}

**Severity:** error

**Cause:** A `@KraftConverter` function has no return type that Kraft can read. Kotlin infers return types even when they are not written, so this is a defensive check.

```kotlin
// No minimal input is known; the message points at the function.
```

**Fix:**

- Declare an explicit return type: `fun Int.toLabel(): String`.

### KRAFT-E055 — Annotation argument is not a String {#kraft-e055}

**Severity:** error

**Cause:** An annotation argument that must be a String has a different type.

```kotlin
@MapConfig(
    source = A::class,
    target = B::class,
    fieldMappings = [FieldMapping(source = 1, target = "a")], // error: source must be a String
)
object M
```

**Fix:**

- Pass a String literal for the argument.

### KRAFT-E056 — Annotation argument is not an enum value {#kraft-e056}

**Severity:** error

**Cause:** An annotation argument that must be an enum value has a different type.

```kotlin
@MapUsing(target = "text", direction = 1) // error: direction must be a ConverterDirection
fun convert(v: Int): String = v.toString()
```

**Fix:**

- Pass an enum constant, for example `ConverterDirection.FORWARD`.

### KRAFT-E057 — Annotation argument is not an array {#kraft-e057}

**Severity:** error

**Cause:** An annotation argument that must be an array or list (such as `fieldMappings`) has a different type.

```kotlin
@MapConfig(source = A::class, target = B::class, fieldMappings = FieldMapping("a", "b")) // error: not an array
```

**Fix:**

- Pass an array literal: `fieldMappings = [FieldMapping("a", "b")]`.

### KRAFT-E058 — Invalid side configuration {#kraft-e058}

**Severity:** error

**Cause:** The `kraft { }` side options (`kraft.side.*` KSP arguments) are invalid, for example two side patterns that overlap.

```kotlin
kraft {
    side("outer") { packagePattern = "**.data.**" }
    side("inner") { packagePattern = "**.data.api.**" } // error: pattern is a subset of 'outer'
}
```

**Fix:**

- Fix the side configuration in `build.gradle.kts`; the message states which rule it broke.
- Make the package patterns of different sides disjoint.

### KRAFT-E059 — Delegate name collision {#kraft-e059}

**Severity:** error

**Cause:** Two distinct converter type pairs hash to the same generated delegate name. This is not a mistake in your code.

```kotlin
// No minimal input is known; the message names the colliding type pairs.
```

**Fix:**

- Report it on the Kraft issue tracker with the type names from the message.

### KRAFT-E060 — Ambiguous @MapEnum {#kraft-e060}

**Severity:** error

**Cause:** More than one `@MapEnum` declaration registers the same source and target pair.

```kotlin
@MapEnum(source = Status::class, target = StatusDto::class)
object FirstEnumMapper

@MapEnum(source = Status::class, target = StatusDto::class) // error: pair already registered
object SecondEnumMapper
```

**Fix:**

- Remove one of the `@MapEnum` declarations.
- Or change one of the source/target pairs.

### KRAFT-E061 — Enum converter conflict {#kraft-e061}

**Severity:** error

**Cause:** A hand-written `@KraftConverter` and a `@MapEnum`-generated mapper register the same enum pair.

```kotlin
@MapEnum(source = Status::class, target = StatusDto::class)
object StatusMapper

@KraftConverter
fun Status.toDto(): StatusDto = StatusDto.valueOf(name) // error: Status -> StatusDto is also @MapEnum-generated
```

**Fix:**

- Remove one of the two; the `@MapEnum`-generated mapper already auto-resolves for this enum pair.

### KRAFT-E062 — Alias collision {#kraft-e062}

**Severity:** error

**Cause:** Two mappers generate the same side alias name for the same source class, so one would shadow the other.

```kotlin
// package app.shared is configured as side "Shared"
@MapConfig(source = Source::class, target = TargetA::class)
object SourceToTargetAMapper

@MapConfig(source = Source::class, target = TargetB::class) // error: both generate Source.toShared()
object SourceToTargetBMapper
```

**Fix:**

- Disambiguate via the side template, for example `to{side}{target}` instead of `to{side}`.
- Or set `aliasEmitMode = AliasEmitMode.FULL_NAME_ONLY` on one of the mappers.

### KRAFT-E063 — Lossy collection conversion {#kraft-e063}

**Severity:** error

**Cause:** A collection property would be converted into a `Set` or `MutableSet` from something that is not a `Set` or `MutableSet`. Duplicate elements would be dropped without a trace, so Kraft refuses to generate the conversion. It applies at every nesting level and to the reverse mapper that `@MapReverse` generates: a mapper from `Set` to `List` forward is fine, but its reverse would turn a `List` into a `Set`.

The deprecated `@MapNested` and `nestedMappings` paths are frozen at their 0.13 behaviour and are not checked.

```kotlin
data class ItemSource(val id: Int)
data class ItemDto(val id: Int)

data class Source(val items: List<ItemSource>)
data class Target(val items: Set<ItemDto>) // error: converting List<ItemSource> to Set<ItemDto> would drop duplicate elements

@MapConfig(source = Source::class, target = Target::class)
object SourceToTargetMapper
```

**Fix:**

- Change the target to `List<ItemDto>` (or another collection that keeps duplicates).
- Or map the property with `@MapUsing` and call `.toSet()` explicitly, which makes the dropped duplicates your decision.

## Warnings

### KRAFT-W001 — Redundant @MapUsing {#kraft-w001}

**Severity:** warning

**Cause:** A property-source `@MapUsing` (one with a `source`) claims a target property that Kraft would map on its own: a direct copy, a nested or element-wise collection mapping, a value-class wrap or unwrap, or a registered `@KraftConverter`. Kraft still calls your converter and still generates the mapper. The warning shows the assignment Kraft would have generated instead, so you can check that it is equivalent. Kraft cannot read function bodies, so it compares outcomes, not code. Whole-source `@MapUsing` (no `source`) is never reported. Neither is a `@MapUsing` that replaces a global `@KraftConverter` for the same property types: Kraft treats that as a deliberate per-mapper override. A global converter applied element-wise to a collection, or through `?.` to a nullable value, is still reported.

```kotlin
data class RowDto(val id: Int)
data class Row(val id: Int)

data class ReportDto(val rows: List<RowDto>?)
data class Report(val rows: List<Row>)

@MapConfig(source = RowDto::class, target = Row::class)
object RowMapper

@MapConfig(source = ReportDto::class, target = Report::class)
object ReportMapper {
    @MapUsing(source = "rows", target = "rows") // warning: Kraft already maps List<RowDto>? to List<Row>
    fun List<RowDto>?.toRows(): List<Row> = this?.map { it.toRow() } ?: emptyList()
}
```

The warning reads:

```text
[KRAFT-W001] @MapUsing 'toRows' on target property 'rows' duplicates a mapping Kraft already generates.

Kraft would generate:
    rows = this.rows?.map { it.toRow() } ?: emptyList()

Remove 'toRows', or add @Suppress("KRAFT-W001") to it if its body intentionally differs (filtering, sorting, defaults).
```

**Fix:**

- Delete the converter. Kraft generates the assignment shown in the warning.
- If the converter deliberately does something different (it filters, sorts, or supplies a different default), keep it and suppress the warning on the function:

```kotlin
@Suppress("KRAFT-W001")
@MapUsing(source = "rows", target = "rows")
fun List<RowDto>?.toRows(): List<Row> = this.orEmpty().filter { it.id > 0 }.map { it.toRow() }
```

Suppressing the warning does not change the generated code.

### KRAFT-W002 — Nullable-twin @KraftConverter {#kraft-w002}

**Severity:** warning

**Cause:** A same-module `@KraftConverter` maps `S? → T?` while a `S → T` converter is also registered, in the same module or upstream. Kraft already reuses the `S → T` converter for nullable `S? → T?` properties through a safe call (`this.<property>?.<fn>()`), so the nullable converter only lifts it through null. Both converters stay registered and the generated code is unchanged. A nullable converter in an upstream module is never reported.

```kotlin
@KraftConverter
fun ReasonDto.toReason(): Reason = Reason(code)

@KraftConverter // warning: only lifts toReason through null
fun ReasonDto?.toReasonNullable(): Reason? = this?.toReason()
```

The warning reads:

```text
[KRAFT-W002] @KraftConverter 'toReasonNullable' (ReasonDto? → Reason?) only lifts 'toReason' (ReasonDto → Reason) through null.

Kraft already generates 'this.<property>?.toReason()' for nullable ReasonDto? → Reason? properties. Remove 'toReasonNullable', or add @Suppress("KRAFT-W002") if it does more than a safe call.
```

When the non-null converter comes from an upstream module, the message names the generated delegate function instead of your original function.

**Fix:**

- Delete the nullable converter. Kraft generates the safe call for you.
- If it does more than a safe call (for example it supplies a different value for `null`), keep it and suppress the warning on the function:

```kotlin
@Suppress("KRAFT-W002")
@KraftConverter
fun ReasonDto?.toReasonNullable(): Reason? = this?.toReason() ?: Reason.UNKNOWN
```

### KRAFT-W003 — @MapIgnoreField(BOTH) property not found {#kraft-w003}

**Severity:** warning

**Cause:** A `BOTH`-direction `@MapIgnoreField` names a property that is missing from the target of one direction. The ignore is skipped for that direction.

```kotlin
@MapConfig(
    source = User::class,
    target = UserDto::class,
    ignoredMappings = [MapIgnoreField("legacyId")], // warning: UserDto has no 'legacyId'
)
object UserMapper
```

**Fix:**

- Remove the `@MapIgnoreField`, or limit it to the direction where the property exists with `direction = IgnoreSide.SOURCE` or `TARGET`.

### KRAFT-W004 — @MapNested overrides @MapField {#kraft-w004}

**Severity:** warning

**Cause:** `@MapNested` and `@MapField` are both on one property. `@MapNested` takes precedence and `@MapField` is ignored.

```kotlin
@MapFrom(User::class)
data class UserDto(
    @MapField("home")
    @MapNested // warning: @MapNested takes precedence
    val address: AddressDto,
)
```

**Fix:**

- Remove `@MapField`, and use `@MapNested(sourceName = "home")` for the rename.

### KRAFT-W005 — Redundant @MapNested sourceName {#kraft-w005}

**Severity:** warning

**Cause:** `@MapNested(sourceName = ...)` repeats the property's own name, so the argument has no effect.

```kotlin
@MapFrom(User::class)
data class UserDto(
    @MapNested(sourceName = "address") // warning: same as the property name
    val address: AddressDto,
)
```

**Fix:**

- Remove the `sourceName` argument.

### KRAFT-W006 — Multiple mapper generator providers {#kraft-w006}

**Severity:** warning

**Cause:** More than one `MapperGeneratorProvider` service is on the processor classpath. Kraft uses the first one it finds.

```kotlin
// Two artifacts on the KSP classpath each ship a META-INF/services entry
// for MapperGeneratorProvider. // warning
```

**Fix:**

- Remove the extra generator artifact from the `ksp` configuration so a single provider remains.

### KRAFT-W007 — Multiple enum generator providers {#kraft-w007}

**Severity:** warning

**Cause:** More than one `EnumMapperGeneratorProvider` service is on the processor classpath. Kraft uses the first one it finds.

```kotlin
// Two artifacts on the KSP classpath each ship a META-INF/services entry
// for EnumMapperGeneratorProvider. // warning
```

**Fix:**

- Remove the extra generator artifact from the `ksp` configuration so a single provider remains.

### KRAFT-W008 — Mapper generation skipped {#kraft-w008}

**Severity:** warning

**Cause:** Kraft skipped generating a mapper because neither the source nor the target class has an originating file, which KSP needs for incremental processing.

```kotlin
// Both types come from outside the module's source files (for example synthetic or
// generated classes with no containing file). // warning
```

**Fix:**

- Declare the source or target type in a source file of the module.
- Or write the mapping by hand.
