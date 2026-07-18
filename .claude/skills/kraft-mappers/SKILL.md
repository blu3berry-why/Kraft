---
name: kraft-mappers
description: |
  Authoring guide for the Kraft KSP automapper library (com.blu3berry.kraft) in Kotlin/KMP. Covers the decision tree for @MapConfig, @MapEnum, @KraftConverter, and @MapUsing (including whole-source mode for decompose/compose/constant/default patterns); reverse mapping with @MapReverse; the Gradle plugin and kraft { } DSL (side aliases, functionNameFormat, moduleId); placement and naming conventions; and the kmpgen-DTO interaction gotchas (K1, K-A1). Use this skill whenever writing or reviewing mappers in a project that depends on Kraft. Trigger on phrases like "Kraft mapper", "@MapConfig", "@KraftConverter", "@MapEnum", "@MapReverse", "DTO mapper", "auto-mapper", "side alias", "kraft plugin", or anytime a `*Mappers.kt` file is being authored.
---

# Kraft Mappers — Authoring Guide

Kraft is a KSP-driven automapper for Kotlin/KMP. It generates extension-function mappers between source and target types at compile time, with explicit converters for non-trivial conversions.

This skill teaches the **decision tree** ("which annotation do I use?") + the **gotchas** that bite when generated DTOs don't have field-aligned domain types.

---

## Mental model

Kraft works in two passes:

1. **Discovery** — scans annotated declarations (`@MapConfig`, `@MapEnum`, `@MapReverse`, `@KraftConverter`, `@MapUsing`) and builds a registry of source→target conversions.
2. **Codegen** — emits one Kotlin file per mapper. Generated files live under `build/generated/ksp/<sourceset>/kotlin/<package-of-source>/generated/`.

The generated code is plain Kotlin extension functions: `fun Source.toTarget(): Target = Target(...)`.

When you call those functions, they try to resolve every property of `Target` from `Source` using:
- **Identical name + type** — direct copy
- **Registered converter** — call the matching `@KraftConverter` extension or `@MapUsing` function
- **Nested mapper** — if both types have a `@MapConfig` registered for them, recurse
- **Enum mapper** — if both enums have a `@MapEnum` registered (or auto-derive when entries align by name)

If none resolves, Kraft fails compilation with a `Type mismatch` or `Required property has no mapping source` error.

---

## Build setup (Gradle plugin, Kraft 0.12.0+)

A module uses either the Kraft Gradle plugin (preferred) or manual wiring — recognize both:

- **Plugin:** `id("com.blu3berry.kraft")` in the plugins block, after the Kotlin (multiplatform/jvm/android) and KSP plugins. It adds version-pinned `kraft-ksp`/`kraft-annotations` and all wiring — do NOT also add those dependencies by hand in a plugin-applied module.
- **Configuration** goes through the typed `kraft { }` extension, not raw `ksp { arg("kraft.…") }`:

  ```kotlin
  kraft {
      functionNameFormat = "to\${target}"                          // optional
      side("domain") { packagePattern = "com.example.domain.**" }  // side aliases
  }
  ```

- **Side aliases:** a registered side gives every mapper whose target matches its `packagePattern` a short alias (`fun XDto.toDomain()` delegating to the verbose mapper). NEVER hand-write those one-line wrapper extensions (`fun XDto.toDomain() = toX()`) — register a side instead.
- **moduleId** defaults to the project path under the plugin; only set it (DSL `moduleId`) to pin a stable id.

---

## Decision tree — which annotation?

```
Source S, Target T:

├── S and T are data classes with mostly the same shape
│   → @MapConfig — Kraft generates field-by-field copy
│
├── Both are enums
│   → @MapEnum (or rely on by-name auto-derivation when entries align)
│
├── Conversion requires logic on a single type pair (Uuid → String, format, parse)
│   → @KraftConverter on a top-level extension function
│       fun S.toT(): T = ...
│       fun T.toS(): S = ...     // reverse, if needed
│
├── Field-specific override needed inside an otherwise auto-mapped @MapConfig
│   → @MapUsing inside the @MapConfig object
│      • Property-source mode:  @MapUsing(source = "x", target = "y") fun convert(v: X): Y
│      • Whole-source mode:     @MapUsing(target = "y")              fun S.compute(): Y
│        ↑ omit `source` to read multiple fields, inject a constant, or coalesce a nullable
│
└── Want both directions for free?
    → Add @MapReverse — Kraft also emits the reverse mapper
```

### When to reach for `@MapUsing` whole-source mode (not `@KraftConverter` or hand-written)

Whole-source `@MapUsing` (omit `source`) is the right tool — *not* a hand-written extension function and *not* a global `@KraftConverter` — for these four patterns. They are the patterns that most often push authors toward "Kraft can't do this":

| Pattern | Shape | Example |
|---|---|---|
| **Decompose** 1 → N | Source value type → N target primitives | `@MapUsing(target = "salePriceMinorUnits") fun Product.minorUnits() = salePrice.amount` *(one per target field)* |
| **Compose** N → 1 | N source primitives → target value type | `@MapUsing(target = "salePrice") fun ProductDto.toMoney() = Money(salePriceMinorUnits, salePriceCurrency)` |
| **Constant injection** | Target field has no source counterpart | `@MapUsing(target = "currency") fun LongAmount.fixedCurrency() = "HUF"` |
| **Nullable → non-null default** | Source nullable, target non-null | `@MapUsing(target = "packSize") fun CartItemDto.packSizeOrDefault() = packSize ?: 1` |

Rule of thumb: if you're tempted to write `fun X.toY(): Y = ...` by hand because "Kraft can't express this", you're almost certainly looking at one of these four. A whole-source `@MapUsing` keeps the rest of the mapper auto-generated.

### Naming convention for `@KraftConverter` functions

`fun <Receiver>.to<Target>(): <Target>` — Kraft uses the function name during registry lookup.

If two converters would collide on simple-name (e.g. converting from two different `Role` enums in different parents), inject a disambiguator into the function name: `toUserRoleFromAuthMe200Role` / `toUserRoleFromAuthResponseUserRole`.

---

## Placement and module layout

Mappers belong in the data layer, in a `mapper/` subpackage. One file per feature is fine; multiple `@MapConfig` / `@MapEnum` / `@KraftConverter` declarations in the same file are fine.

**Don't:**
- Don't put `@KraftConverter` extensions in `commonMain` if the receiver type is platform-only — it must be visible in the same source set as `@MapConfig` consumers.
- Don't import Kraft annotations from random places — they live in `com.blu3berry.kraft.config.*`.
- Cross-module `@KraftConverter` discovery works on Kraft **0.11.0+** (delegates resolved by name from the classpath): a converter declared in module A IS visible to module B's KSP run, as long as every module on the classpath uses the **same Kraft version**. On older Kraft (<0.11.0), re-declare or use `@MapUsing` on the consumer side. If the same converter pair exists in two classpath modules, the build fails with an ambiguity error — delete one.

---

## Standard pattern — reusable shape

```kotlin
package com.example.feature.data.mapper

import com.blu3berry.kraft.config.KraftConverter
import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapEnum
import com.blu3berry.kraft.config.MapReverse
import com.blu3berry.kraft.config.MapUsing
import com.example.feature.data.generated.models.SomeDto
import com.example.feature.domain.entity.SomeEntity
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// 1. Cross-cutting converters (non-trivial type bridges, 1:1 type pairs)

@KraftConverter
@OptIn(ExperimentalUuidApi::class)
fun Uuid.toIdString(): String = toString()

@KraftConverter
@OptIn(ExperimentalUuidApi::class)
fun String.toUuid(): Uuid = Uuid.parse(this)

// 2. Top-level mapper

@MapReverse
@MapConfig(source = SomeDto::class, target = SomeEntity::class)
object SomeMapper {
    // Whole-source mode for fields that don't line up 1:1
    @MapUsing(target = "currency")
    fun SomeDto.fixedCurrency(): String = "HUF"
}

// 3. Enums

@MapReverse
@MapEnum(source = SomeStatusEntity::class, target = SomeDto.Status::class)
object SomeStatusMapper
```

Generated extensions are then importable from `<package-of-source>.generated.*` and called like `dto.toSomeEntity()`.

---

## Gotchas

### K1 — Generated DTOs may inline `$ref`'d schemas as nested classes

When using kmpgen (or any OpenAPI generator) that inlines schemas referenced by `$ref` instead of resolving them to top-level classes, you'll see N copies of the same logical type with different FQNs (e.g. `AuthResponse.User`, `AuthMe200Response`, both representing the same OpenAPI `User` schema).

**Symptom:**
```
Type mismatch for property 'role'.
From source: role: Role
To target:   role: UserRole
```

The two `Role`s are nested in different parents and are **distinct Kotlin types** even with identical entries.

**Workaround in Kraft:** Add a `@KraftConverter` for each parallel enum copy with disambiguated function names:
```kotlin
@KraftConverter
fun AuthMe200Response.Role.toUserRoleFromAuthMe200Role(): UserRole = when (this) {
    AuthMe200Response.Role.STAFF   -> UserRole.STAFF
    AuthMe200Response.Role.MANAGER -> UserRole.MANAGER
    AuthMe200Response.Role.OWNER   -> UserRole.OWNER
}
```

**Real fix (in the codegen, not Kraft):** make the OpenAPI generator emit a single top-level class per `$ref`'d schema. Kraft's job becomes one mapper instead of N parallel ones.

### K-A1 — `@MapEnum` filename collision on simple target name *(fixed in 0.8.x+)*

**Status:** Fixed in Kraft 0.8.x (PR #65 — generated mapper filenames include the parent-class chain). On older Kraft versions, the workaround below applies.

Pre-fix behavior: Kraft derived the generated mapper's filename from `<sourceSimpleName>_To_<targetSimpleName>_EnumMapper.kt`. Two `@MapEnum` declarations in the same module whose source/target enums had **identical simple names** (different FQNs, e.g. nested-inside-different-parents) collided on the file path and KSP threw `FileAlreadyExistsException`.

**Workaround on pre-fix Kraft:** drop the second `@MapEnum` and use a pair of `@KraftConverter` extensions with author-controlled disambiguated function names.

**Post-fix:** filenames become `AuthMe200Response_Role_To_UserRole_EnumMapper.kt` and `AuthResponse_User_Role_To_UserRole_EnumMapper.kt` — distinct paths, no workaround needed. Top-level types keep their short filenames (backward compatible).

### K3 — Type-aliased property types *(fixed in Kraft 0.10.1)*

**Status:** Fixed in Kraft 0.10.1 (PR #74). On older Kraft, any DTO property declared through a `typealias` (common in generated API clients that attach serializers via annotated aliases, e.g. kmpgen ≥1.5.0's date-time fields) crashes the processor with `expected KSClassDeclaration for [typealias ...]` — or silently skips properties, converters, and enum derivations on scanner paths.

**Fix:** upgrade Kraft to ≥0.10.1 — aliases resolve to their underlying type everywhere (property types, converter signatures, `Alias::class` annotation arguments, collection elements), with use-site nullability preserved. **Remaining limitation:** parameterized aliases (`typealias X<T> = ...`) are unsupported; Kraft reports a clear error — declare the property with the underlying type.

### Don't run mappers on JVM-only paths in `commonMain`

Kraft itself is multiplatform-friendly, but a `@KraftConverter` whose body touches JVM types will compile in `commonMain` only via expect/actual or platform-specific source sets. Keep converter bodies KMP-pure (`Uuid.parse`, `Uuid.toString`, `Instant.parse`, etc.).

---

## When NOT to use Kraft

- **One-shot mappers used in a single test** — write them inline as a private extension; the KSP cost isn't worth it.
- **Mappers that branch on field values** (e.g. "if status == X, set differently") — write by hand or use `@MapUsing` with whole-source mode.
- **Trivial 1:1 with identical types** — Kotlin already lets you copy with `.copy(...)`; no mapper needed.

If Kraft would add more boilerplate (lots of `@MapUsing` overrides per field) than a hand-written mapper, write it by hand and document the exception in a top-of-file comment.

---

## Common errors and where to look

| Error | Cause | Fix |
|---|---|---|
| `Type mismatch for property X` | Source field type ≠ target field type, no converter registered | Add `@KraftConverter` for the type pair, `@MapEnum` for enum pairs, or `@MapUsing` (whole-source) for composite/constant/default cases |
| `Required property X has no mapping source` | Target has a field with no matching source field | Add `@FieldMapping` rename, make target field nullable, supply a default, or use whole-source `@MapUsing` for constant injection |
| `Unresolved reference 'toXFromY'` in a generated file | Cross-package extension import missing in generated file | Open the generated file, add the FQN import; flag as an upstream Kraft bug |
| `FileAlreadyExistsException ... _EnumMapper.kt.kt` | Two `@MapEnum`s with same target simpleName (K-A1) | Upgrade to Kraft 0.8.x+; on older versions, replace one with hand-written `@KraftConverter` extensions |
| KSP succeeds but call site fails to compile | Stale generated files after annotation edits | `./gradlew clean` then rebuild |

---

## Verification before commit

1. Run KSP: `./gradlew :<module>:kspCommonMainKotlinMetadata` (or your module's KSP task) — must be green.
2. Open one generated file under `build/generated/ksp/.../generated/` and read the emitted code — does the field-by-field assignment look right?
3. If you added a `@MapEnum` with `@MapReverse`, verify both directions — Kraft generates them separately and one direction can be wrong while the other is fine.
4. Add or update a unit test that round-trips one example of every mapper you touched. Round-tripping catches asymmetric losses (e.g. nullable → non-null with default).
