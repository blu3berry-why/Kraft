# Collection Mapping

Kraft supports nested mapping inside `List<T>` and `Set<T>` collections. When the source and target have collection properties with different element types, Kraft automatically generates both a parent mapper and a child mapper for the element type pair.

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

## Nullable Elements

When the source element type is nullable but the target element type is not, Kraft uses `mapNotNull` instead of `map` to filter out null entries:

```kotlin
// Source: val items: List<ItemSource?>
// Target: val items: List<ItemDto>
```

Generated: `items = this.items.mapNotNull { it?.toItemDto() }`

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

- **Element type is not a concrete class** (e.g., an interface): compile-time error stating the element type "is not a concrete class"
- **Mismatched collection kinds** (e.g., `List` on source, `Set` on target with the same element type): produces a type mismatch compilation error
