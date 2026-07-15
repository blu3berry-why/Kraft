# Kraft Side Aliases — Design

**Status:** Draft (awaiting user review)
**Date:** 2026-05-08
**Branch:** `feat/side-aliases-design`

---

## Problem

Callers of Kraft-generated mappers want stable, short, layer-aware names like `.toDomain()` / `.toEntity()` / `.toDto()`. Today Kraft emits only verbose names of the form `to<Target>From<Source>()` (e.g. `toCategoryFromProductCategoryDto()`).

The verbose names are necessary for Kraft's own resolution (cross-package extension lookup, nested mapper recursion, disambiguation when a target is reachable from multiple sources), but they are awkward at call sites that don't care which "from" side they came from. Users currently work around this by hand-writing wrapper files such as:

```kotlin
fun ProductCategoryDto.toDomain(): Category = toCategoryFromProductCategory()
fun CategoryEntity.toDomain(): Category    = toCategoryFromCategoryEntity()
fun Category.toEntity(): CategoryEntity     = toCategoryEntityFromCategory()
fun ScaleProfileEntity.toDomain(): ScaleProfile = toScaleProfileFromScaleProfileEntity()
fun ScaleProfile.toEntity(): ScaleProfileEntity = toScaleProfileEntityFromScaleProfile()
```

Every clean-architecture KMP project repeats this pattern across every feature. It is pure boilerplate that drifts from generated names whenever Kraft is bumped or types are renamed.

## Goal

Let projects register **sides** (user-named layers like `Dto`, `Domain`, `Entity`) by package pattern in Gradle, so Kraft can emit short alias extensions (`toDomain()`, `toEntity()`, …) alongside the verbose mapper functions — eliminating the wrapper-file pattern.

## Non-goals

- Replacing the verbose `to<Target>From<Source>()` names. Those remain the canonical mapper functions and continue to be used internally by Kraft for nested resolution and cross-module imports. Aliases are additive.
- IDE / Gradle plugin DSL wrapping the KSP args (users can write a `build-logic` helper if they want one).
- Auto-generating `expect`/`actual` aliases for multiplatform splits.
- Wildcards or regex in **side names** (only in package patterns).
- A migration tool for projects already using hand-written wrapper files.

---

## Section 1 — Configuration model (Gradle)

Sides are registered as KSP processor arguments. Each side is a group of args sharing a slot key. The slot key is an internal ID (lowercase by convention); the user-facing label is `.name`.

```kotlin
ksp {
    arg("kraft.side.dto.name",           "Dto")
    arg("kraft.side.dto.packagePattern", "**.data.generated.models.**")
    arg("kraft.side.dto.template",       "to{side}")
    arg("kraft.side.dto.emitMode",       "BOTH")          // optional; default BOTH

    arg("kraft.side.domain.name",           "Domain")
    arg("kraft.side.domain.packagePattern", "**.domain.model.**")
    arg("kraft.side.domain.template",       "to{side}")

    arg("kraft.side.entity.name",           "Entity")
    arg("kraft.side.entity.packagePattern", "**.database.**.entity.**")
    arg("kraft.side.entity.template",       "to{side}")
}
```

### Field semantics

| Field | Required | Default | Meaning |
|---|---|---|---|
| `name` | yes | — | String substituted into `{side}` in the template. Verbatim, case preserved. |
| `packagePattern` | yes | — | Ant-style glob (`**` = any package segments, `*` = single segment). Matched against the **target** class's FQN to decide which side names this mapper. |
| `template` | no | `to{side}` | Alias function name. Variables: `{side}`, `{target}`, `{source}`. |
| `emitMode` | no | `BOTH` | `BOTH` or `FULL_NAME_ONLY`. Project-level default; per-mapper override goes on `@MapConfig`. |

### No registration → no aliases

If a project sets no `kraft.side.*` args, behaviour is identical to today (only `to<Target>From<Source>()` is emitted). The feature is purely additive and opt-in.

---

## Section 2 — Template variables

A side's `template` string is the entire alias function name. Three substitutions are supported:

| Variable | Expands to |
|---|---|
| `{side}` | The side's `name` value |
| `{target}` | Simple name of the target class |
| `{source}` | Simple name of the source class |

Substitution is verbatim — no auto-casing, no pluralization. The `to` prefix is **not** a hardcoded convention; the template is the entire function name. If you write `template = "convertToFancy{side}"`, the generated function is `convertToFancyDomain()`.

### Worked examples

For mapper `Category ← ProductCategoryDto`, side label `Domain`:

| Template | Generated extension |
|---|---|
| `to{side}` | `fun ProductCategoryDto.toDomain(): Category` |
| `to{side}{target}` | `fun ProductCategoryDto.toDomainCategory(): Category` |
| `to{target}` | `fun ProductCategoryDto.toCategory(): Category` |
| `from{source}` | `fun ProductCategoryDto.fromProductCategoryDto(): Category` |
| `to{side}From{source}` | `fun ProductCategoryDto.toDomainFromProductCategoryDto(): Category` |

### Validation

Performed at config load (KSP processor init):

- Template must yield a valid Kotlin identifier after substitution. Empty results, results starting with a digit, or containing illegal characters fail loudly.
- Unknown variable references (typos like `{traget}`) fail loudly — never kept as literal text.

---

## Section 3 — Emit modes and per-mapper override

### Project-level default

Set per-side via `kraft.side.<slot>.emitMode`:

- `BOTH` *(default)* — emit both the verbose `to<Target>From<Source>()` and the alias.
- `FULL_NAME_ONLY` — emit only the verbose form. Used when a side is registered for collision tracking or future tooling but aliases aren't wanted yet.

`ALIAS_ONLY` is intentionally **not supported**: Kraft's own generated code calls verbose names for nested resolution, so suppressing them would break internals.

### Per-mapper override

A new parameter on `@MapConfig`:

```kotlin
package com.blu3berry.kraft.config

enum class AliasEmitMode { INHERIT, BOTH, FULL_NAME_ONLY }

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapConfig(
    val source: KClass<*>,
    val target: KClass<*>,
    val fieldMappings: Array<FieldMapping> = [],
    val nestedMappings: Array<NestedMapping> = [],
    val ignoredMappings: Array<MapIgnoreField> = [],
    val useGlobalConverters: Boolean = true,
    val aliasEmitMode: AliasEmitMode = AliasEmitMode.INHERIT,
)
```

Resolution order:

1. `aliasEmitMode = INHERIT` *(default)* → use the matched side's `emitMode` from Gradle.
2. `BOTH` or `FULL_NAME_ONLY` → override the side's setting for this mapper only.
3. No matching side → no alias regardless of mode.

Default is `INHERIT` so most mappers don't need to think about aliasing — only mappers needing an exception carry the parameter.

### When the override is needed

- Two `@MapConfig`s targeting the same side that would otherwise collide on the alias name.
- Mappers between two types that both happen to be in the same registered side (e.g. Domain↔Domain transformations) where the alias name is misleading.
- Gradual project adoption: set project default to `FULL_NAME_ONLY`, opt individual mappers in to `BOTH` as call sites migrate.

---

## Section 4 — Side resolution and collision rules

### Resolving a side for a mapper

1. Take the target class FQN.
2. Match it against each registered side's `packagePattern`.
3. **Exactly one match** → use that side.
4. **Zero matches** → no alias emitted (full name only). Not an error.
5. **Multiple matches** → pattern overlap, treated as a Gradle config bug (see below).

### Pattern overlap is a Gradle config error

Pattern overlap is a project-wide misconfiguration of `build.gradle.kts`, not a per-mapper problem. Error messages and remediation point at the gradle config, never at any individual `@MapConfig`.

Detection happens in two phases because non-trivial glob disjointness is undecidable in general:

**Phase 1 — Config-load validation (eager).**
At KSP processor init, before any class scanning, fail loudly on:

- Identical patterns on two different sides.
- Strict subset relationships any matching class would always trigger (e.g. `**.data.**` vs `**.data.api.**`). Detected via segment analysis.
- Invalid glob syntax.
- Invalid template (unknown variables, would not produce a valid Kotlin identifier).
- Missing required field (`name` or `packagePattern`).

**Phase 2 — First-class-collision (deferred).**
For overlap that isn't structurally provable, the first class FQN matching two patterns triggers the error. The message is still framed as a Gradle bug:

```
Kraft side configuration error: package patterns overlap.

Class com.x.feature.data.api.UserDto matches two sides:
  - kraft.side.dto.packagePattern  = "**.data.**"            (in build.gradle.kts)
  - kraft.side.api.packagePattern  = "**.data.api.**"        (in build.gradle.kts)

Patterns must be disjoint. Tighten one of the two patterns so the
classes you intend each side to match no longer overlap.
```

The `@MapConfig` that triggered the scan is not mentioned — it's irrelevant; the user fixes `build.gradle.kts` and the error goes away for every mapper.

### Alias-name collisions

Two `@MapConfig`s producing the same `(receiverFqn, aliasName)` pair:

```kotlin
@MapConfig(source = Category::class, target = CategoryEntityV1::class)
object MapperA   // would emit: fun Category.toEntity(): CategoryEntityV1

@MapConfig(source = Category::class, target = CategoryEntityV2::class)
object MapperB   // would emit: fun Category.toEntity(): CategoryEntityV2  ← collision
```

Kraft tracks `(receiverFqn, aliasName)` pairs across all `@MapConfig`s in the same compilation. On collision, fail with a message naming both declarations and suggesting two fixes:

1. Disambiguate via template (e.g. change side template to `to{side}{target}`).
2. Set `aliasEmitMode = FULL_NAME_ONLY` on one of the colliding mappers.

**Cross-module alias collisions are not detected by Kraft** — KSP processes one module at a time. Callers will hit Kotlin's "ambiguous extension" error at the call site, which is a sufficiently clear signal. Documented as a known limitation.

### `@MapReverse` interaction

Each direction is resolved independently against its own target's package:

```kotlin
@MapReverse
@MapConfig(source = ProductCategoryDto::class, target = Category::class)
object CategoryMapper
```

With Dto and Domain sides registered, this emits:

- Forward: `fun ProductCategoryDto.toDomain(): Category` (target is in Domain).
- Reverse: `fun Category.toDto(): ProductCategoryDto` (target is in Dto).

No special configuration needed.

---

## Section 5 — Generated code shape

Aliases are plain top-level extension functions emitted in the **same generated file** as the verbose mapper. No new file lifecycle.

After this feature (template `to{side}`, side `Domain` matches `Category`):

```kotlin
package hu.blu3berry.reclaw.productcatalog.data.generated.models.generated

import hu.blu3berry.reclaw.productcatalog.domain.model.Category

fun ProductCategoryDto.toCategoryFromProductCategoryDto(): Category =
    Category(/* field assignments */)

// Alias generated for side Domain (template = to{side})
fun ProductCategoryDto.toDomain(): Category =
    toCategoryFromProductCategoryDto()
```

### Decisions

- **One-line delegate, not a duplicated body.** Zero risk of behavioural drift. Trivial for the JIT/R8 to flatten — runtime cost is nothing.
- **Plain `fun`, not `inline`.** Kotlin's `inline` is designed for higher-order functions (lambda parameters, `reified` types). On a delegating extension with no lambdas, the compiler emits an "expected performance impact is insignificant" warning and adds bytecode bloat at every call site plus binary-compatibility constraints. JIT (JVM) and R8 (Android) already inline these trivially. Kraft mappers regenerate often; locking them into ABI via `inline` would cost more than it gains. (This decision applies to **all** generated mappers, not just aliases — see "Out of scope" for revisiting this case-by-case if profiling later shows a real hotspot.)
- **Comment header on each alias** so generated-code readers know which side config produced it. Format kept terse — single line, machine-greppable as `// Alias generated for side`.

For `@MapReverse`, both directions get their own alias resolved independently, each in its own generated file/section based on its own target side.

---

## Section 6 — Implementation overview

This is enough to scope the work. Detailed steps belong in the implementation plan.

### Modules touched

| Module | Change |
|---|---|
| `kraft-annotations` | Add `AliasEmitMode` enum. Add `aliasEmitMode` parameter to `@MapConfig`. |
| `kraft-ksp` | New `SideRegistry` loader (reads KSP args at processor init). New alias-emission step in mapper-generation pipeline. Glob matcher utility. Validation errors. |
| `kraft-core` | No changes expected. |
| `composeApp` (sample) | Add a small example exercising side aliases for documentation/testing. |

### Pipeline integration (`kraft-ksp`)

Today's flow per `@MapConfig`:

```
collect → resolve fields → emit verbose mapper file
```

After this feature:

```
collect → resolve fields → emit verbose mapper
                       ↓
                    side resolution (target FQN vs SideRegistry)
                       ↓
                    if side matches AND emit mode allows:
                       emit alias delegate in the same file
```

### `SideRegistry` responsibilities

- Parse KSP args once at processor init: scan all keys matching `kraft.side.<slot>.<field>`.
- For each slot, build a `SideConfig(name, packagePattern, template, emitMode)`.
- Run config-load validation (Phase 1 from Section 4).
- Expose `resolveSide(targetFqn): SideConfig?` for the emitter.
- Track `(receiverFqn, aliasName)` pairs to detect alias-name collisions across mappers in the same compilation.

### Glob matcher

Small Ant-style implementation (`*` = single segment, `**` = any-or-zero segments). Plain Kotlin, no external dependency. Internal utility in `kraft-ksp`.

### Error reporting

All new errors flow through KSP's `Resolver.logger.error()`:

- Alias-name collisions: source location of the offending `@MapConfig`.
- Config-load and pattern-overlap errors: `null` location with a `build.gradle.kts` hint in the message body.

### Out of scope (deliberately deferred)

- Per-module override of side templates beyond what KSP arg layering already provides.
- IDE plugin / Gradle plugin DSL wrapping the args. Users can write a `build-logic` helper.
- Auto-generation of `expect`/`actual` aliases for multiplatform splits.
- Wildcards in side **names** themselves (only in package patterns).
- Marking mappers `inline` globally. May be revisited per-mapper if profiling later shows a real hotspot.
- Migration tool for projects already using hand-written wrapper files.

---

## Section 7 — Testing strategy

Kraft already has KSP-processor compilation tests (compile annotated sources, assert on generated output / compile errors). New tests follow the same pattern.

### Unit tests (`kraft-ksp` test sourceset)

| Area | Cases |
|---|---|
| **Glob matcher** | `*` matches single segment; `**` matches any-or-zero segments; mid-pattern wildcards; case sensitivity; edge cases (leading `**`, trailing `**`, only-`**`). |
| **Template substitution** | All three variables substitute correctly; unknown variable fails loudly; substitution result validated as Kotlin identifier; `{side}` / `{target}` / `{source}` case preserved. |
| **`SideRegistry` parsing** | Valid multi-side config; missing `name`; missing `packagePattern`; identical patterns → fail; strict-subset patterns → fail; invalid glob → fail; invalid template → fail; default values applied (`template`, `emitMode`). |

### Processor integration tests (compile-and-assert)

| Scenario | Assertion |
|---|---|
| No sides registered | Output identical to today (regression guard). |
| Single side, target matches | Verbose function + alias both present, alias delegates to verbose. |
| Single side, target doesn't match | Only verbose function emitted. |
| Two sides, `@MapReverse` with each direction in a different side | Forward alias + reverse alias both emitted under correct templates. |
| `aliasEmitMode = FULL_NAME_ONLY` on `@MapConfig` | Only verbose, even with side match. |
| Project-level `emitMode = FULL_NAME_ONLY`, per-mapper `BOTH` | Alias emitted (per-mapper wins). |
| Template `to{side}{target}` with two same-side mappers | Both aliases emit, no collision. |
| Template `to{side}` with two same-side mappers from same source | Compile error naming both `@MapConfig`s. |
| Pattern overlap, runtime detection | Compile error pointing at gradle config, not at any `@MapConfig`. |
| Generated alias call site | Synthetic test compiles user code calling `dto.toDomain()` and links it. |

### Sample app coverage

`composeApp` gains a small "side aliases" example mapper pair so the docs/sample stay in sync and the feature gets at-least-one round-trip in the released artifact's testbed.

### No runtime tests needed

The alias is a one-line delegate with no logic — if it compiles and the verbose mapper is correct, the alias is correct.

---

## Open questions

None. All design decisions captured above are confirmed.

## Acceptance criteria

- A project that registers `Dto`, `Domain`, `Entity` sides via Gradle KSP args, with `template = "to{side}"` for each, can delete its hand-written wrapper file (per the example in Problem) and rely entirely on Kraft-generated aliases.
- Projects that don't register any sides see no behavioural change.
- All new failure modes (config validation, pattern overlap, alias collision) produce compile errors with messages naming the relevant Gradle slot keys and/or `@MapConfig` declarations.
- `kraft-annotations` adds exactly one new public API surface: `AliasEmitMode` enum + `aliasEmitMode` parameter on `@MapConfig` (default `INHERIT`, source-compatible with existing callers).
