# API-First Client Design

In an API-first workflow the OpenAPI spec is the single source of truth: the client code, the DTOs, and the wire format are all derived from it. What usually breaks the promise is the last step — hand-written mapping between the generated DTOs and your domain model. Every spec change ripples into mapper edits, and the "generated" pipeline ends in hand-maintained glue.

Kraft closes that gap. Pair an OpenAPI client generator with Kraft and the entire data path is build output:

```text
openapi.yaml            ← you write this
   │  client generator (e.g. kmpgen)
   ▼
Ktor client + DTOs      ← generated
   │  Kraft (KSP)
   ▼
Domain types            ← you write these
```

You maintain two things: the spec and the domain model. Everything between them is generated and compile-checked.

## The stack

Any generator that emits Kotlin data classes works with Kraft. For Kotlin Multiplatform, [kmpgen](https://github.com/kroegerama/openapi-kmp-gen) (`com.kroegerama.openapi-kmp-gen`) is a natural pairing: it generates a Ktor-based client with `kotlinx-serialization` DTOs and `kotlinx-datetime` date types from OpenAPI 3.0+ specs, targeting the same platforms Kraft does.

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kmpgen)   // com.kroegerama.openapi-kmp-gen
    alias(libs.plugins.ksp)
}

kmpgen {
    spec(packageName = "com.example.shop.api") {
        specFile = file("openapi/shop.yaml")
    }
}

dependencies {
    kspCommonMainMetadata("com.blu3berry.kraft:kraft-ksp:<version>")
    commonMainImplementation("com.blu3berry.kraft:kraft-annotations:<version>")
}
```

!!! note "Version compatibility"
    kmpgen ≥ 1.5.0 declares date-time DTO fields through annotated type aliases — this requires **Kraft ≥ 0.10.1**, which resolves type aliases to their underlying types. Older Kraft versions fail with `expected KSClassDeclaration for [typealias ...]`.

    Verified against kmpgen **1.5.0** (stable) and **1.6.0-RC01**. The alias mechanism is unchanged between them; 1.6.0-RC01 additionally emits aliases whose expansion is itself nullable (`typealias NullableRefTypealias = NullableInlineObject?`) for nullable `$ref`s, which Kraft maps as the nullable underlying type.

## From spec to domain

A spec fragment:

```yaml
components:
  schemas:
    Product:
      type: object
      required: [id, name, priceMinorUnits, status, updatedAt]
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        priceMinorUnits: { type: integer, format: int64 }
        status: { type: string, enum: [ACTIVE, DISCONTINUED] }
        updatedAt: { type: string, format: date-time }
```

The generator emits a DTO shaped roughly like:

```kotlin
@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val priceMinorUnits: Long,
    val status: Status,          // nested enum
    val updatedAt: SerializableISO8601Instant,   // typealias to kotlinx-datetime Instant
) {
    @Serializable
    enum class Status { ACTIVE, DISCONTINUED }
}
```

Your domain model, written by hand, on your terms:

```kotlin
data class Product(
    val id: Uuid,
    val name: String,
    val price: Money,
    val status: ProductStatus,
    val updatedAt: Instant,
)

enum class ProductStatus { ACTIVE, DISCONTINUED }
```

The entire mapping layer:

```kotlin
@KraftConverter
fun String.toUuid(): Uuid = Uuid.parse(this)

@MapConfig(source = ProductDto::class, target = Product::class)
object ProductMapper {
    @MapUsing(target = "price")
    fun ProductDto.toPrice(): Money = Money(priceMinorUnits, "EUR")
}
```

That's it. Same-named properties copy directly, `ProductDto.Status` → `ProductStatus` derives automatically (entries pair by name), the `String` → `Uuid` bridge is a registered converter, and the one structural difference (`priceMinorUnits` → `Money`) is a whole-source [`@MapUsing`](custom-converters.md). At the call site:

```kotlin
val products = api.getProducts().map { it.toProduct() }
```

When the spec adds a field, regenerate: if the domain has it, Kraft picks it up; if it doesn't and it's required on the domain side, the build fails and tells you — the mapping can't silently drift from the spec.

## Generator quirks to know about

Generated code has patterns hand-written code wouldn't. Two worth knowing:

**Inlined `$ref` schemas.** Some generators inline a `$ref`'d schema everywhere it's used instead of emitting one top-level class, producing N structurally-identical types with different fully-qualified names (`AuthResponse.User` vs `AuthMe200Response`). Kraft treats them as the distinct types they are: declare a converter per copy, with disambiguated function names to avoid collisions (`toUserRoleFromAuthMeRole`, `toUserRoleFromAuthResponseRole`). The real fix is generator configuration that resolves `$ref`s to shared classes, when available.

**Type-aliased fields.** Generators attach custom serializers via annotated type aliases — kmpgen's is `typealias SerializableISO8601Instant = @Serializable(ISO8601InstantSerializer::class) Instant`. Kraft ≥ 0.10.1 resolves through aliases transparently — properties, converter signatures, `Alias::class` annotation arguments, collection elements, and aliases that expand to a nullable type all behave as the underlying type, with use-site nullability preserved. Parameterized aliases (`typealias X<T> = ...`) are the one unsupported shape; Kraft reports a clear error and the fix is declaring the property with the underlying type. kmpgen ships one (`SerializableImmutableList<T>`) but does not use it for generated model properties, which are plain `List<T>`.

## See also

- [AI Integration](ai-integration.md) — the Kraft agent skill covers this workflow's decision tree and gotchas, so your coding agent applies them automatically.
- [Custom Converters](custom-converters.md) — `@KraftConverter` and `@MapUsing` in depth.
- [Enum Mapping](enum-mapping.md) — auto-derivation and explicit `fieldMappings`.
