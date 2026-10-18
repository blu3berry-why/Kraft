# Collection Mapping

Kraft maps collection properties element by element: `List`, `Set`, `Collection`, `Iterable`, `Array`, `MutableList`, `MutableSet` and `MutableCollection`. When the source and target have collection properties with different element types, Kraft automatically generates both a parent mapper and a child mapper for the element type pair.

## How It Works

When a source class has `List<SourceItem>` and the target has `List<TargetItem>` (with different element types), Kraft generates:

1. An extension function for the parent class mapping
2. A child extension function for `SourceItem -> TargetItem`
3. A `.map { ... }` call inside the parent mapper that invokes the child mapper

### List Example

```kotlin
data class ItemSource(val id: Int, val label: String)
data class OrderSource(val ref: String, val items: List<ItemSource>)

data class ItemDto(val id: Int, val label: String)
data class OrderDto(val ref: String, val items: List<ItemDto>)

@MapConfig(source = OrderSource::class, target = OrderDto::class)
object OrderMapper
```

Generated code:

```kotlin
fun OrderSource.toOrderDto(): OrderDto = OrderDto(
    ref = this.ref,
    items = this.items.map { it.toItemDto() }
)

fun ItemSource.toItemDto(): ItemDto = ItemDto(
    id = this.id,
    label = this.label
)
```

### Set Example

For `Set` properties, Kraft appends `.toSet()` after the `.map { ... }` call:

```kotlin
data class ItemSource(val id: Int, val label: String)
data class OrderSource(val ref: String, val items: Set<ItemSource>)

data class ItemDto(val id: Int, val label: String)
data class OrderDto(val ref: String, val items: Set<ItemDto>)

@MapConfig(source = OrderSource::class, target = OrderDto::class)
object OrderMapper
```

Generated code:

```kotlin
fun OrderSource.toOrderDto(): OrderDto = OrderDto(
    ref = this.ref,
    items = this.items.map { it.toItemDto() }.toSet()
)
```

## Supported Collection Types

Source and target may each be any of these types, and they need not be the same one:

| Type | Appended after `.map { ... }` |
|---|---|
| `List`, `Collection`, `Iterable` | nothing |
| `Set` | `.toSet()` |
| `Array` | `.toTypedArray()` |
| `MutableList`, `MutableCollection` | `.toMutableList()` |
| `MutableSet` | `.toMutableSet()` |

```kotlin
// Source: val items: Array<ItemSource>
// Target: val items: Array<ItemDto>
```

Generated: `items = this.items.map { it.toItemDto() }.toTypedArray()`

### Converting between collection types

When the source and target types differ, Kraft converts as long as every element survives:

| Source | Target | Generated |
|---|---|---|
| `Array<ItemSource>` | `List<ItemDto>` | `this.items.map { it.toItemDto() }` |
| `Set<ItemSource>` | `List<ItemDto>` | `this.items.map { it.toItemDto() }` |
| `Iterable<ItemSource>` | `MutableCollection<ItemDto>` | `this.items.map { it.toItemDto() }.toMutableList()` |
| `Set<ItemSource>` | `MutableSet<ItemDto>` | `this.items.map { it.toItemDto() }.toMutableSet()` |
| `Array<String>` | `List<String>` | `this.tags.toList()` |
| `List<String>` | `Collection<String>` | `this.tags` (already a supertype, no copy) |

Any source type converts into `List`, `Collection`, `Iterable`, `Array`, `MutableList` and `MutableCollection`. Only a `Set` or `MutableSet` converts into a `Set` or `MutableSet`.

### The Set rule

A `List`, `Array` or other collection that may hold duplicates is never converted into a `Set` or `MutableSet`, because the duplicates would be dropped without a trace. Kraft fails compilation with [`KRAFT-E063`](errors.md#kraft-e063):

```kotlin
// Source: val items: List<ItemSource>
// Target: val items: Set<ItemDto>
```

```text
[KRAFT-E063] Cannot map property 'items': converting List<ItemSource> to Set<ItemDto> would drop duplicate elements.
Change the target to List<ItemDto>, or map it with @MapUsing and call .toSet() explicitly.
```

The same check applies to the reverse mapper that `@MapReverse` generates: a `Set<ItemSource>` to `List<ItemDto>` mapper compiles, but its reverse would turn a `List` into a `Set` and is refused.

### Deprecated nested-mapping annotations

`@MapNested` and `@MapConfig(nestedMappings = ...)` are deprecated and stay frozen at their 0.13 behaviour: they work only for `List` and `Set` of the same kind, with the output they always had (including the existing `List` to `Set` conversion through an explicit `NestedMapping` with a `FieldMapping` rename, which is not checked by E063). The other collection types and conversions on this page come from auto-detection and `@MapField` / `FieldMapping` renames only.

## Auto-Detection

Collection nested mapping follows the same auto-detection rules as regular nested mapping:

- The source and target properties must share the same name
- The element types must differ (if both sides use the same element type, no child mapper is generated)
- Both element types must be concrete classes with a primary constructor

When the element types are identical, the collection is assigned directly:

```kotlin
data class Item(val id: Int)
data class OrderSource(val ref: String, val items: List<Item>)
data class OrderDto(val ref: String, val items: List<Item>)

@MapConfig(source = OrderSource::class, target = OrderDto::class)
object OrderMapper
```

Generated: `items = this.items` (no `.map` call needed).

### Multiple Collection Properties

Each collection property is handled independently:

```kotlin
data class AuthorSource(val name: String)
data class CommentSource(val text: String)
data class PostSource(val title: String, val authors: List<AuthorSource>, val comments: List<CommentSource>)

data class AuthorDto(val name: String)
data class CommentDto(val text: String)
data class PostDto(val title: String, val authors: List<AuthorDto>, val comments: List<CommentDto>)

@MapConfig(source = PostSource::class, target = PostDto::class)
object PostMapper
```

Generated:

```kotlin
fun PostSource.toPostDto(): PostDto = PostDto(
    title = this.title,
    authors = this.authors.map { it.toAuthorDto() },
    comments = this.comments.map { it.toCommentDto() }
)
```

## Nullable Collections

Kraft handles nullable collection types with safe-call operators and fallback values.

### Nullable source, nullable target

`List<Source>?` on source and `List<Target>?` on target uses a safe call:

```kotlin
// Source: val items: List<ItemSource>?
// Target: val items: List<ItemDto>?
```

Generated: `items = this.items?.map { it.toItemDto() }`

### Nullable source, non-null target

`List<Source>?` on source and `List<Target>` on target adds an `emptyList()` fallback:

Generated: `items = this.items?.map { it.toItemDto() } ?: emptyList()`

### Set equivalents

For `Set` collections, nullable sources produce `?.toSet()` chaining:

```kotlin
// Source: val items: Set<ItemSource>?
// Target: val items: Set<ItemDto>?
```

Generated: `items = this.items?.map { it.toItemDto() }?.toSet()`

For non-null targets: `items = this.items?.map { it.toItemDto() }?.toSet() ?: emptySet()`

### Why null becomes empty here but is an error elsewhere

A nullable source mapped to a non-null target is handled three ways in Kraft, depending on what the target is:

| Target | Nullable source, non-null target | Why |
|--------|----------------------------------|-----|
| A collection or map | Falls back to an empty one (`emptyList()`, `emptySet()`, `emptyMap()`, `emptyArray()`…) | An empty container is a natural "nothing here" value, and API DTOs often send `null` where the domain model wants an empty list. |
| A nested object | [`KRAFT-E014`](errors.md#kraft-e014) | Kraft never invents an object. There is no value it could build without your input. |
| A scalar through a converter | [`KRAFT-E005`](errors.md#kraft-e005) | A safe call on a converter yields `Y?`, and a scalar has no natural empty value. Declare an `X? → Y` converter, or supply the default with a whole-source `@MapUsing` (see [custom converters](custom-converters.md#restrictions-and-caveats)). |

The collection fallback means a `null` source list and an empty one map to the same target value. If your domain treats them differently (for example, `null` means "not loaded" and empty means "loaded, nothing found"), keep the target nullable (`List<ItemDto>?`), so Kraft generates a plain safe call and the `null` survives. To turn `null` into something else, write a `@MapUsing` for that property.

Inside a map the rule is stricter: a nullable map value mapped to a non-null one is a type-mismatch error, because dropping the entry would also lose its key.

## Converters on Elements

A registered `@KraftConverter` applies to collection elements exactly as it does to a single property, and it is called under its own name. With `@KraftConverter fun Foo.intoBar(): Bar` registered, `List<Foo>` to `List<Bar>` generates:

```kotlin
items = this.items.map { it.intoBar() }
```

A converter registered for the non-null pair also serves nullable elements (`it?.intoBar()`, or `mapNotNull` when the target elements are non-null). As for a single property, an auto-detected nested mapper between two concrete classes takes precedence over a converter for the same pair. A hand-written `@MapUsing` that only repeats this element-wise call now triggers [`KRAFT-W001`](errors.md#kraft-w001).

## Nullable Elements

When the source element type is nullable but the target element type is not, Kraft uses `mapNotNull` instead of `map` to filter out null entries:

```kotlin
// Source: val items: List<ItemSource?>
// Target: val items: List<ItemDto>
```

Generated: `items = this.items.mapNotNull { it?.toItemDto() }`

## Kraft warns when a converter duplicates native mapping

You do not need a `@MapUsing` helper that only maps a collection element by element. If you write one anyway, for example `fun List<RowDto>?.toRows(): List<Row> = this?.map { it.toRow() } ?: emptyList()`, Kraft still uses it but reports warning [`KRAFT-W001`](errors.md#kraft-w001). The warning shows the line Kraft would generate (here `rows = this.rows?.map { it.toRow() } ?: emptyList()`). Delete the helper, or keep it with `@Suppress("KRAFT-W001")` if it filters or sorts.

## Maps

`Map` and `MutableMap` properties are mapped value by value, and their keys are mapped when the key types differ (see [Map keys](#map-keys)). Each value uses the operation Kraft would use for a single property of the value types: a nested mapper, an enum mapper, a `@KraftConverter`, or a value-class wrap or unwrap.

```kotlin
// Source: val byId: Map<String, ItemSource>
// Target: val byId: Map<String, ItemDto>
```

Generated: `byId = this.byId.mapValues { it.value.toItemDto() }`

| Source | Target | Generated |
|---|---|---|
| `Map<String, ItemSource>` | `MutableMap<String, ItemDto>` | `this.byId.mapValues { it.value.toItemDto() }.toMutableMap()` |
| `Map<String, Int>` | `MutableMap<String, Int>` | `this.m.toMutableMap()` |
| `Map<String, ItemSource>?` | `Map<String, ItemDto>` | `this.byId?.mapValues { it.value.toItemDto() } ?: emptyMap()` |
| `Map<String, ItemSource>?` | `MutableMap<String, ItemDto>` | `this.byId?.mapValues { it.value.toItemDto() }?.toMutableMap() ?: mutableMapOf()` |
| `Map<String, ItemSource?>` | `Map<String, ItemDto?>` | `this.byId.mapValues { it.value?.toItemDto() }` |

A registered `@KraftConverter fun Foo.intoBar(): Bar` serves values the same way: `m = this.m.mapValues { it.value.intoBar() }`.

Two shapes are errors:

- **Nullable values into non-null values** (`Map<String, ItemSource?>` into `Map<String, ItemDto>`) fail with [`KRAFT-E005`](errors.md#kraft-e005). Unlike a list, a map cannot drop the null: removing the value would also remove its key.
- **A map and a non-map** (`Map<String, ItemSource>` into `List<ItemDto>`) fail with [`KRAFT-E005`](errors.md#kraft-e005).

Map values and collection elements nest in each other to any depth: `Map<String, List<ItemSource>>` into `Map<String, List<ItemDto>>` generates `groups = this.groups.mapValues { e0 -> e0.value.map { it.toItemDto() } }`, and `List<Map<String, ItemSource>>` generates `pages = this.pages.map { e0 -> e0.mapValues { it.value.toItemDto() } }`. Each level follows its own rules, so a non-`Set` into a `Set` inside a map value is still [`KRAFT-E063`](errors.md#kraft-e063). A hand-written `@MapUsing` that only repeats the `mapValues` call triggers [`KRAFT-W001`](errors.md#kraft-w001).

### Map keys

A map holds one entry per key, so a key mapping that turns two different keys into one would silently drop an entry. Kraft maps keys only through operations that are one-to-one:

- a **value-class wrap** (`String` key into a `UserId` key),
- a **value-class unwrap** (`UserId` key into a `String` key),
- an **enum mapping in which no two source entries map to the same target entry**: an auto-derived enum mapping (same-named entries, always one-to-one) or a one-to-one `@MapEnum`.

| Source | Target | Generated |
|---|---|---|
| `Map<String, ItemSource>` | `Map<UserId, ItemDto>` | `this.byUser.entries.associate { UserId(it.key) to it.value.toItemDto() }` |
| `Map<UserId, Int>` | `Map<String, Int>` | `this.counts.mapKeys { it.key.value }` |
| `Map<StatusDto, Int>` | `Map<Status, Int>` | `this.counts.mapKeys { it.key.toStatus() }` |

`mapKeys` is used when only the keys change, `mapValues` when only the values change, and `entries.associate { … }` when both do. A `MutableMap` target adds `.toMutableMap()`, and a nullable source map is safe-called as for values.

Any other key operation fails with [`KRAFT-E064`](errors.md#kraft-e064), because Kraft cannot prove it one-to-one:

- a **nested mapper** (`Map<KeySource, Int>` into `Map<KeyDto, Int>`),
- a **`@KraftConverter`** (`@KraftConverter fun Long.toKey(): String` for `Map<Long, Int>` into `Map<String, Int>`),
- a **`@MapEnum` that maps two or more source entries to the same target entry**,
- an enum mapper or converter **compiled in another module**, whose entry table Kraft cannot see.

A many-to-one `@MapEnum` is still fine for map **values**: `Map<String, StatusDto>` into `Map<String, Status>` generates `this.m.mapValues { it.value.toStatus() }`. Nullable keys are not mapped and report `KRAFT-E005`. To map keys some other way, map the property with `@MapUsing`.

## Renamed Collection Properties

When the collection property has a different name on source and target, use `FieldMapping` to declare the rename. Auto-detection generates the child mapper:

```kotlin
data class TagSource(val value: String)
data class ArticleSource(val title: String, val articleTags: List<TagSource>)

data class TagDto(val value: String)
data class ArticleDto(val title: String, val tags: List<TagDto>)

@MapConfig(
    source = ArticleSource::class,
    target = ArticleDto::class,
    fieldMappings = [FieldMapping(source = "articleTags", target = "tags")]
)
object ArticleMapper
```

Generated: `tags = this.articleTags.map { it.toTagDto() }`

## Error Cases

- **Element type is not a concrete class** (e.g., an interface): compile-time error ([`KRAFT-E015`](errors.md#kraft-e015)) naming the element type that "is not a concrete class"
- **Lossy collection conversion** (e.g., `List` on source, `Set` on target): fails with [`KRAFT-E063`](errors.md#kraft-e063), see [The Set rule](#the-set-rule)
- **Map keys that could merge entries** (a nested mapper, a `@KraftConverter` or a many-to-one `@MapEnum` on the keys): fails with [`KRAFT-E064`](errors.md#kraft-e064), see [Map keys](#map-keys)
