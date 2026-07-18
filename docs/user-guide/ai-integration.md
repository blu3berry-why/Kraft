# Using Kraft with AI Coding Agents

AI coding assistants (Claude, Copilot, Cursor, etc.) tend to write manual mapping code by default. Giving them context about Kraft lets them generate proper annotation-based mappers instead.

Kraft ships a ready-made agent skill so you don't have to write (or paste) that context yourself:

**[`.claude/skills/kraft-mappers/SKILL.md`](https://github.com/blu3berry-why/Kraft/blob/main/.claude/skills/kraft-mappers/SKILL.md)**

The skill covers:

- The **decision tree** for choosing between `@MapConfig`, `@MapFrom`/`@MapTo`, `@MapEnum`, `@KraftConverter`, and `@MapUsing` (including whole-source mode for decompose / compose / constant / default patterns)
- Reverse mapping with `@MapReverse` and converter direction inference
- The **Gradle plugin** and `kraft { }` DSL — build setup, side aliases (never hand-write `.toDomain()` wrapper extensions), `functionNameFormat`, `moduleId`
- Placement and naming conventions for mapper objects and converter functions
- **Known gotchas** with generated API-client DTOs (nested `$ref` copies, enum filename collisions, type-aliased properties) and which Kraft version fixes each
- A common-errors table mapping processor messages to fixes
- A pre-commit verification checklist

## Quick Setup

**Claude Code** — copy the skill file into your project so the agent auto-loads it whenever a mapper file is touched:

```bash
mkdir -p .claude/skills/kraft-mappers
curl -o .claude/skills/kraft-mappers/SKILL.md \
  https://raw.githubusercontent.com/blu3berry-why/Kraft/main/.claude/skills/kraft-mappers/SKILL.md
```

Alternatively, add one pointer line to your project's `CLAUDE.md`:

```markdown
## Kraft Mapper Library
This project uses Kraft (com.blu3berry.kraft), a KSP compile-time mapper generator.
When writing or reviewing DTO ↔ domain mappers, follow .claude/skills/kraft-mappers/SKILL.md
— use Kraft annotations, never manual mapping extension functions.
```

**Cursor / Copilot / other agents** — these read a single instructions file instead of a skill directory. Paste the skill file's content into `.cursorrules`, `AGENTS.md`, or your agent's equivalent. The skill is plain Markdown; no Claude-specific syntax beyond the frontmatter block, which you can drop.

## How It Works

With the skill in place:

1. The agent knows Kraft exists and what it does.
2. When asked to "create a mapper" or "map X to Y", it uses annotations instead of manual code.
3. It follows the decision tree for choosing the right annotation.
4. It avoids common mistakes (missing defaults on `@MapIgnore`, `@MapFrom` + `@MapTo` on the same class, manual extension functions) and recognizes processor errors from the common-errors table.

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

**With Kraft**, the AI writes only the exception — the one thing that isn't a direct match:

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
```

Kraft auto-matches most same-named properties, handles nested objects and collections, and the one rename (`orderId` → `id`) is a single `FieldMapping`. Even the `OrderStatus` → `OrderStatusDto` enum mapper is [auto-derived](enum-mapping.md#when-you-do-not-need-mapenum) — both enums live in the same module, every entry pairs by name, and the pair is reachable from the `@MapConfig` — so no `@MapEnum` declaration is needed. When you add a new field to both classes, Kraft picks it up automatically — no mapper code to update. The generated code is produced at compile time, is type-safe, and stays in sync with your data classes.

## Customizing the Skill

The skill file is yours once copied — extend it with project-specific conventions:

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
