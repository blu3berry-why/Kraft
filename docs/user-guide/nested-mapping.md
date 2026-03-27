# Nested Mapping

When source and target objects contain properties that are themselves mapped types, Kraft can generate child mappers automatically. This page covers the three ways to declare nested mappings and the rules Kraft follows to resolve them.

## Auto-Detection

When a source and target share a same-named property with **different** data class types, Kraft auto-detects the nested relationship and generates a child mapper alongside the parent.

```kotlin
import hu.nova.blu3berry.kraft.mapping.MapFrom

data class AddressSource(val street: String, val city: String)
data class PersonSource(val name: String, val address: AddressSource)

data class AddressDto(val street: String, val city: String)

@MapFrom(PersonSource::class)
data class PersonDto(val name: String, val address: AddressDto)
```

Generated output:

```kotlin
fun PersonSource.toPersonDto(): PersonDto = PersonDto(
    name = this.name,
    address = this.address.toAddressDto(),
)

fun AddressSource.toAddressDto(): AddressDto = AddressDto(
    street = this.street,
    city = this.city,
)
```

Both types must be concrete classes with primary constructors. Kraft recognizes that `AddressSource` and `AddressDto` are different types on the same-named `address` property and generates the child mapper automatically.

## @MapNested (on @MapFrom / @MapTo Classes)

`@MapNested` is an explicit annotation placed on a property to trigger nested mapping. It is useful when you want to be explicit or when the source property has a different name.

### Same Property Name

When the source and target property names match, `@MapNested` with no arguments produces the same result as auto-detection:

```kotlin
@MapFrom(PersonSource::class)
data class PersonDto(
    val name: String,
    @MapNested
    val address: AddressDto
)
```

Generated output (identical to the auto-detection example):

```kotlin
address = this.address.toAddressDto()
```

### Different Property Name (sourceName)

Use the `sourceName` parameter to read from a differently named source property:

```kotlin
data class AddressSource(val street: String, val city: String)
data class PersonSource(val name: String, val homeAddress: AddressSource)

data class AddressDto(val street: String, val city: String)

@MapFrom(PersonSource::class)
data class PersonDto(
    val name: String,
    @MapNested(sourceName = "homeAddress")
    val address: AddressDto
)
```

Generated output:

```kotlin
fun PersonSource.toPersonDto(): PersonDto = PersonDto(
    name = this.name,
    address = this.homeAddress.toAddressDto(),
)

fun AddressSource.toAddressDto(): AddressDto = AddressDto(
    street = this.street,
    city = this.city,
)
```

When `sourceName` is left empty (the default), Kraft uses the annotated property's own name.

## @MapConfig.nestedMappings

When using `@MapConfig`, declare nested type pairs with `NestedMapping`:

```kotlin
import hu.nova.blu3berry.kraft.config.MapConfig
import hu.nova.blu3berry.kraft.config.NestedMapping

data class AddressSource(val street: String, val city: String)
data class PersonSource(val name: String, val address: AddressSource)

data class AddressDto(val street: String, val city: String)
data class PersonDto(val name: String, val address: AddressDto)

@MapConfig(
    source = PersonSource::class,
    target = PersonDto::class,
    nestedMappings = [
        NestedMapping(source = AddressSource::class, target = AddressDto::class)
    ]
)
object PersonMapper
```

Generated output:

```kotlin
fun PersonSource.toPersonDto(): PersonDto = PersonDto(
    name = this.name,
    address = this.address.toAddressDto(),
)

fun AddressSource.toAddressDto(): AddressDto = AddressDto(
    street = this.street,
    city = this.city,
)
```

Kraft matches source properties by type: it finds that `PersonSource.address` has type `AddressSource`, which matches the declared `NestedMapping`, and generates the child mapper call.

## Transitive (Multi-Level) Nested Mapping

Kraft uses depth-first search to resolve nested dependencies. If a child type itself contains nested mappings, all required mappers are generated in a single pass.

```kotlin
data class EmployeeSource(val id: Int)
data class DepartmentSource(val name: String, val manager: EmployeeSource)
data class CompanySource(val title: String, val department: DepartmentSource)

data class EmployeeDto(val id: Int)
data class DepartmentDto(val name: String, val manager: EmployeeDto)

@MapFrom(CompanySource::class)
data class CompanyDto(val title: String, val department: DepartmentDto)
```

Generated output -- three mappers from one annotation:

```kotlin
fun CompanySource.toCompanyDto(): CompanyDto = CompanyDto(
    title = this.title,
    department = this.department.toDepartmentDto(),
)

fun DepartmentSource.toDepartmentDto(): DepartmentDto = DepartmentDto(
    name = this.name,
    manager = this.manager.toEmployeeDto(),
)

fun EmployeeSource.toEmployeeDto(): EmployeeDto = EmployeeDto(
    id = this.id,
)
```

## Nullable Nested Properties

Kraft handles nullability for nested properties:

### Nullable source, nullable target

```kotlin
data class AddressSource(val street: String)
data class PersonSource(val name: String, val address: AddressSource?)

data class AddressDto(val street: String)

@MapFrom(PersonSource::class)
data class PersonDto(val name: String, val address: AddressDto?)
```

Generated output uses a safe call:

```kotlin
address = this.address?.toAddressDto()
```

### Non-null source, non-null target

```kotlin
data class PersonSource(val name: String, val address: AddressSource)

@MapFrom(PersonSource::class)
data class PersonDto(val name: String, val address: AddressDto)
```

Generated output uses a direct call:

```kotlin
address = this.address.toAddressDto()
```

### Nullable source to non-null target (compile-time error)

If the source property is nullable but the target property is non-null, Kraft cannot safely generate the mapping and reports a compile-time error:

```kotlin
data class PersonSource(val name: String, val address: AddressSource?)

@MapFrom(PersonSource::class)
data class PersonDto(val name: String, val address: AddressDto)  // ERROR: nullable source, non-null target
```

This applies to both auto-detected nested mappings and explicit `@MapNested` annotations.

## Circular Dependencies

Kraft detects circular nested mapping dependencies and reports them as compile-time errors:

```kotlin
data class NodeSource(val value: Int, val next: NodeSource?)
data class NodeDto(val value: Int, val next: NodeDto?)

data class Container(val node: NodeSource)

@MapFrom(Container::class)
data class ContainerDto(val node: NodeDto)  // ERROR: Circular nested mapping (NodeSource -> NodeDto -> NodeSource)
```

The DFS-based resolver tracks visited type pairs and halts when it encounters a cycle.

## Combining @MapNested with @MapField

If both `@MapNested` and `@MapField` are present on the same property, `@MapNested` takes precedence. Kraft emits a warning but compiles successfully. Prefer using `@MapNested(sourceName = "...")` instead of combining both annotations.
