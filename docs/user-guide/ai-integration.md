# Using Kraft with AI Coding Agents

AI coding assistants (Claude, Copilot, Cursor, etc.) tend to write manual mapping code by default. Giving them context about Kraft lets them generate proper annotation-based mappers instead.

## Quick Setup

Copy the skill block below into your project's AI configuration file:

- **Claude Code**: `CLAUDE.md` in your project root
- **Cursor**: `.cursorrules`
- **Other agents**: `AGENTS.md` or equivalent

## Kraft Agent Skill

Below is a ready-to-copy block. It gives the AI agent everything it needs to use Kraft correctly.

````markdown
## Kraft Mapper Library

This project uses Kraft, a KSP-based compile-time mapper generator for Kotlin. When creating mappers between data classes, use Kraft annotations instead of writing manual mapping code.

### Quick Reference

| Task | Annotation | Placement |
|------|-----------|-----------|
| Config-based mapping | `@MapConfig(source = A::class, target = B::class)` | On object |
| Map source to target | `@MapFrom(Source::class)` | On target class |
| Map target from source | `@MapTo(Target::class)` | On source class |
| Rename property (config) | `FieldMapping(source = "src", target = "tgt")` | In @MapConfig.fieldMappings |
| Rename property (class) | `@MapField(counterPartName = "srcProp")` | On target property |
| Nested object mapping | Auto-detected when types differ | No annotation needed; use `FieldMapping` if renamed |
| Ignore property (config) | `MapIgnoreField("prop")` | In @MapConfig.ignoredMappings |
| Ignore property (class) | `@MapIgnore` | On target property (must have default) |
| Reverse mapping | `@MapReverse` | On class with @MapFrom/@MapTo or on @MapConfig object |
| Enum mapping | `@MapEnum(source = A::class, target = B::class)` | On config object |
| Custom converter | `@MapUsing(source = "prop", target = "prop")` | On function in @MapConfig object |
| Whole-source converter | `@MapUsing(target = "prop")` | On function in @MapConfig object (omit source) |
| Converter direction | `@MapUsing(..., direction = ConverterDirection.FORWARD)` | On function when @MapReverse has same-name properties |

### Decision Rules

- **Simple same-name properties**: No annotation needed -- Kraft maps them automatically.
- **Standalone mapping config**: Use `@MapConfig` on an object. Supports field renames, nested mappings, ignore rules, converters, and reverse mapping.
- **Different property names**: Use `FieldMapping` in `@MapConfig`, or `@MapField` on the class.
- **Nested objects**: Auto-detected when source and target have same-named properties with different types. Use `FieldMapping` or `@MapField` if the property is renamed.
- **Complex transformations**: Use `@MapUsing` inside a `@MapConfig` object. Omit the `source` parameter to receive the whole source object.
- **Need both directions**: Add `@MapReverse` to generate the inverse mapper. If both classes share a property name with different types, provide both forward and reverse `@MapUsing` converters -- Kraft auto-detects direction, or use `direction = ConverterDirection.FORWARD/REVERSE` to be explicit.
- **Enum-to-enum**: Use `@MapEnum` with auto-matching or explicit `fieldMappings`.
- **Class-level annotations**: Use `@MapFrom` on the target class or `@MapTo` on the source class when you prefer annotating the data classes directly.

### Anti-Patterns

- Do NOT write manual mapping extension functions -- use Kraft annotations.
- Do NOT use `@MapFrom` and `@MapTo` on the same class (compile-time error).
- Do NOT forget default values on `@MapIgnore` properties (compile-time error).
- Do NOT mix `@MapNested` with `@MapField` on the same property (`@MapNested` wins with a warning).

### Generated Code Location

Generated extension functions appear in:
- KMP: `build/generated/ksp/metadata/commonMain/kotlin/`
- JVM: `build/generated/ksp/main/kotlin/`

### Imports

Annotations are in two packages:
- `com.blu3berry.kraft.mapping.*` -- @MapFrom, @MapTo, @MapField, @MapIgnore, @MapNested
- `com.blu3berry.kraft.config.*` -- @MapConfig, @MapEnum, @MapUsing, @MapReverse, FieldMapping, MapIgnoreField, IgnoreSide, ConverterDirection
````

## How It Works

When you add this to your project's AI config:

1. The agent knows Kraft exists and what it does.
2. When asked to "create a mapper" or "map X to Y", it uses annotations instead of manual code.
3. It knows the decision rules for choosing the right annotation.
4. It avoids common mistakes (missing defaults, duplicate annotations, manual extension functions).

## Example Interaction

Consider a real-world e-commerce scenario. Your domain model has an `Order` entity and you need a `OrderDto` for your API layer:

```kotlin
// Domain model
data class Order(
    val orderId: String,
    val customerName: String,
    val customerEmail: String,
    val shippingAddress: Address,
    val billingAddress: Address,
    val items: List<OrderItem>,
    val status: OrderStatus,
    val subtotalCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val internalNotes: String,
)

data class Address(val street: String, val city: String, val zip: String, val country: String)
data class OrderItem(val sku: String, val name: String, val quantity: Int, val priceCents: Long)
enum class OrderStatus { PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }

// API DTO
data class OrderDto(
    val id: String,                    // renamed from orderId
    val customerName: String,          // direct match
    val customerEmail: String,         // direct match
    val shippingAddress: AddressDto,   // nested mapping
    val billingAddress: AddressDto,    // nested mapping
    val items: List<OrderItemDto>,     // nested collection
    val status: OrderStatusDto,        // enum mapping
    val subtotalCents: Long,           // direct match
    val taxCents: Long,                // direct match
    val totalCents: Long,              // direct match
    val createdAt: Instant,            // direct match
    val updatedAt: Instant,            // direct match
    // internalNotes intentionally excluded from DTO
)

data class AddressDto(val street: String, val city: String, val zip: String, val country: String)
data class OrderItemDto(val sku: String, val name: String, val quantity: Int, val priceCents: Long)
enum class OrderStatusDto { PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }
```

**Without Kraft**, the AI writes (and you maintain) every single property assignment:

```kotlin
fun Order.toOrderDto(): OrderDto = OrderDto(
    id = this.orderId,
    customerName = this.customerName,
    customerEmail = this.customerEmail,
    shippingAddress = AddressDto(
        street = this.shippingAddress.street,
        city = this.shippingAddress.city,
        zip = this.shippingAddress.zip,
        country = this.shippingAddress.country,
    ),
    billingAddress = AddressDto(
        street = this.billingAddress.street,
        city = this.billingAddress.city,
        zip = this.billingAddress.zip,
        country = this.billingAddress.country,
    ),
    items = this.items.map { item ->
        OrderItemDto(
            sku = item.sku,
            name = item.name,
            quantity = item.quantity,
            priceCents = item.priceCents,
        )
    },
    status = when (this.status) {
        OrderStatus.PENDING -> OrderStatusDto.PENDING
        OrderStatus.CONFIRMED -> OrderStatusDto.CONFIRMED
        OrderStatus.SHIPPED -> OrderStatusDto.SHIPPED
        OrderStatus.DELIVERED -> OrderStatusDto.DELIVERED
        OrderStatus.CANCELLED -> OrderStatusDto.CANCELLED
    },
    subtotalCents = this.subtotalCents,
    taxCents = this.taxCents,
    totalCents = this.totalCents,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
)
```

That is **35+ lines** of boilerplate. Every time you add, rename, or remove a field in either class, you must update this function manually — and the compiler won't always catch the mismatch.

**With Kraft**, the AI writes only the exceptions — the 3 things that aren't a direct match:

```kotlin
data class OrderDto(
    val id: String,
    val customerName: String,
    val customerEmail: String,
    val shippingAddress: AddressDto,
    val billingAddress: AddressDto,
    val items: List<OrderItemDto>,
    val status: OrderStatusDto,
    val subtotalCents: Long,
    val taxCents: Long,
    val totalCents: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    // internalNotes is simply not declared — Kraft ignores it
)

data class AddressDto(val street: String, val city: String, val zip: String, val country: String)
data class OrderItemDto(val sku: String, val name: String, val quantity: Int, val priceCents: Long)

@MapConfig(
    source = Order::class,
    target = OrderDto::class,
    fieldMappings = [FieldMapping(source = "orderId", target = "id")]
)
object OrderMapper

@MapEnum(source = OrderStatus::class, target = OrderStatusDto::class)
object OrderStatusMapping
```

Kraft auto-matches the 9 same-named properties, handles nested objects and collections, maps the enum, and the one rename (`orderId` → `id`) is a single `FieldMapping`. When you add a new field to both classes, Kraft picks it up automatically — no mapper code to update. The generated code is produced at compile time, is type-safe, and stays in sync with your data classes.

## Customizing the Skill

You can extend the skill block with project-specific conventions:

- **Preferred mapping style**: State whether your project prefers `@MapFrom`/`@MapTo` on classes or `@MapConfig` on standalone objects.
- **Naming conventions**: Specify naming patterns for config objects (e.g. `XToYMapper`, `XMappingConfig`).
- **KSP options**: Document which `kraft.functionNameFormat` your project uses so the AI knows the generated function names.
- **Reverse mapping policy**: State whether `@MapReverse` should be used by default or only when explicitly requested.

Example addition:

````markdown
### Project Conventions

- Prefer `@MapConfig` on standalone objects for all mappings.
- Name config objects as `{Source}To{Target}Mapper` (e.g. `UserToUserDtoMapper`).
- This project uses `kraft.functionNameFormat = "map${source}To${target}"`.
- Always add `@MapReverse` when both directions are needed.
````
