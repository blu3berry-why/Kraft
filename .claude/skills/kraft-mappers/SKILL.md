---
name: kraft-mappers
description: |
  Authoring guide for the Kraft KSP automapper library (com.blu3berry.kraft) in Kotlin/KMP. Covers the decision tree for @MapConfig, @MapEnum, @KraftConverter, and @MapUsing (including whole-source mode for decompose/compose/constant/default patterns); reverse mapping with @MapReverse; the Gradle plugin and kraft { } DSL (side aliases, functionNameFormat, moduleId); placement and naming conventions; what Kraft already does automatically (element-wise List/Set mapping, nullable collection and nullable-scalar bridging); and the kmpgen-DTO interaction gotchas (K1, K-A1, K3, K4, K5, K6). Use this skill whenever writing or reviewing mappers in a project that depends on Kraft. Trigger on phrases like "Kraft mapper", "@MapConfig", "@KraftConverter", "@MapEnum", "@MapReverse", "DTO mapper", "auto-mapper", "side alias", "kraft plugin", or anytime a `*Mappers.kt` file is being authored.
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
- **Collection element mapping** — `List<A>`/`Set<A>` → `List<B>`/`Set<B>` maps element-wise, using the nested mapper or the `@MapEnum` pair for `A → B`

If none resolves, Kraft fails compilation with a `Type mismatch` or `Required property has no mapping source` error.

### What is already automatic (do not hand-write these)

These recur as "Kraft can't do this" and all four are native. Reach for an annotation only after checking this list:

| Shape | Emitted for you |
|---|---|
| `List<A>` → `List<B>`, `Set<A>` → `Set<B>` | `.map { it.toB() }`, plus `.toSet()` for sets — where `A → B` is a nested mapper (two mappable classes) or a `@MapEnum` pair |
| `List<A>?` → `List<B>` | `this.xs?.map { it.toB() } ?: emptyList()` — the nullable source needs **no** `@MapUsing` |
| `List<A?>` → `List<B>` | `this.xs.mapNotNull { it?.toB() }` |
| `A?` → `B?` with only a non-null `A → B` converter registered | `this.x?.toB()` — the non-null bridge is threaded through a safe call (Kraft 0.13.0+) |

The one nullable shape that is **not** automatic is `A?` → `B` (nullable source, non-null target): a safe call would yield a null the target cannot hold, and a scalar has no `emptyList()` equivalent. Supply the fallback with a whole-source `@MapUsing` (see Pattern 4 below). Collections are the exception — `List<A>?` → `List<B>` *is* handled, via the empty-collection fallback.

---

## Build setup (Gradle plugin, Kraft 0.13.0+)

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
│     ↑ registers the pair globally — List<S>/Set<S> properties of that enum
│       are then converted element-wise with no extra annotation
│
├── Both are List<…> or Set<…> of types that themselves map
│   → nothing. Element-wise mapping is automatic, nullable sources included.
│     Annotate the ELEMENT pair (@MapConfig for classes, @MapEnum for enums),
│     never the collection — a converter for List<A> → List<B> is rejected.
│     Caveat: a hand-written @KraftConverter is NOT applied at element position (K6).
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
- Cross-module `@KraftConverter` discovery works on Kraft **0.11.0+** (delegates resolved by name from the classpath): a converter declared in module A IS visible to module B's KSP run, as long as every module on the classpath uses the **same Kraft version**. On older Kraft (<0.11.0), re-declare or use `@MapUsing` on the consumer side. If the same converter pair exists in two classpath modules, the build fails with an ambiguity error; resolve it by deleting the duplicate, declaring a same-module `@KraftConverter` (local converters win over classpath delegates), or using a per-property `@MapUsing` on the affected mapper.

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

**Fix:** upgrade Kraft to ≥0.10.1 — aliases resolve to their underlying type everywhere (property types, converter signatures, `Alias::class` annotation arguments, collection elements, aliases whose expansion is itself nullable), with use-site nullability preserved. **Remaining limitation:** parameterized aliases (`typealias X<T> = ...`) are unsupported; Kraft reports a clear error — declare the property with the underlying type.

**Generator versions checked:** kmpgen 1.5.0 and 1.6.0-RC01. The alias mechanism is identical in both; 1.6.0-RC01 adds nullable-expansion aliases (`typealias NullableRefTypealias = NullableInlineObject?`) for nullable `$ref`s, covered by the fix. kmpgen's one parameterized alias (`SerializableImmutableList<T>`) is not used for generated model properties.

### K4 — `@OptIn` on generated DTOs was classpath-sensitive *(fixed in Kraft 0.13.0)*

**Status:** Fixed in Kraft 0.13.0 (PRs #107/#108, issues #104/#106).

Pre-fix, Kraft collected opt-in markers only from the *declaration* (`@OptIn(ExperimentalUuidApi::class)` written on the class, converter, or config object). A file-level `@file:OptIn(...)` — the form some generator versions emit instead — was silently dropped, so the generated mapper compiled under one kmpgen version and failed with an opt-in error under another, with no change to your own source. The symptom is a mapper that stops compiling after a *generator* upgrade.

**Post-fix:** both placements propagate (declaration and containing file), deduplicated, on the mapper and on the delegate registry. The generated code owns its opt-ins — you should **not** need a module-wide `freeCompilerArgs += "-opt-in=kotlin.uuid.ExperimentalUuidApi"` to compile Kraft output. If you still do on 0.13.0+, that is a bug worth reporting, not a workaround to keep.

### K5 — Nullable scalar with a non-null target is the one pair never auto-bridged

`A? → B?` reuses a registered non-null `A → B` converter through a safe call (0.13.0+). `A? → B` does not, by design — see the mental-model table above. This is the single most common "my `@KraftConverter` is registered but Kraft says type mismatch" report; the fix is a whole-source `@MapUsing` supplying the default, or making the target nullable.

### K6 — `@KraftConverter` is not applied at collection element position

A registered `@KraftConverter fun A.intoB(): B` converts an `A` *property*, but a `List<A>` → `List<B>` property does not use it: the element-position path accepts only auto-derived `@MapEnum` mappers and synthesised nested mappers, because rendering the element call assumes a `to<Target>` name that a hand-written converter need not have. The property fails with a plain type mismatch, with nothing to indicate a converter was found and declined.

**Workaround:** if `A` and `B` are both classes with primary constructors, drop the converter and let `@MapConfig` synthesise the nested mapper (which *is* applied element-wise). Otherwise handle the whole collection with a whole-source `@MapUsing`:

```kotlin
@MapUsing(target = "items")
fun SrcDto.itemsMapped(): List<B> = items.map { it.intoB() }
```

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
| `Type mismatch` where the ONLY difference is `?` on the source | Nullable source, non-null target — never auto-lifted (K5) | Make the target nullable, or add a whole-source `@MapUsing` that supplies the default |
| `Type mismatch` on a `List`/`Set` property | The ELEMENT pair has no mapping — the collection itself is never the problem | Register `@MapConfig` (classes) or `@MapEnum` (enums) for the element types; do not write a `List → List` converter (parameterized receivers are rejected). A plain `@KraftConverter` does not cover element position — see K6 |
| Opt-in error (`This declaration needs opt-in`) in a generated mapper | Kraft < 0.13.0 dropped file-level `@file:OptIn` (K4) | Upgrade to 0.13.0+; do not paper over it with module-wide `-opt-in` compiler args |
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
