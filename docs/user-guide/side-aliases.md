# Side Aliases

Register **sides** (named layers like `Dto`, `Domain`, `Entity`) in your build script so Kraft emits short, layer-aware extensions (`.toDomain()`, `.toEntity()`, …) alongside the verbose mapper functions. Aliases are additive and opt-in — projects that don't register any sides keep the same generated output as before.

This eliminates the hand-written wrapper-file pattern that clean-architecture KMP projects repeat per feature:

```kotlin
// Boilerplate this feature replaces:
fun ProductCategoryDto.toDomain(): Category    = toCategory()
fun CategoryEntity.toDomain(): Category        = toCategory()
fun Category.toEntity(): CategoryEntity         = toCategoryEntity()
```

## Quick Start

With the [Kraft Gradle plugin](getting-started.md#the-gradle-plugin-recommended), register sides through the typed `kraft { }` DSL (the side name defaults to the slot capitalized — `dto` → `Dto`):

```kotlin
// build.gradle.kts
kraft {
    side("dto")    { packagePattern = "com.example.**.dto.**" }
    side("domain") { packagePattern = "com.example.**.domain.**" }
}
```

Without the plugin, register the same sides as raw KSP processor arguments in the module that applies the KSP plugin:

```kotlin
// build.gradle.kts
ksp {
    arg("kraft.side.dto.name",           "Dto")
    arg("kraft.side.dto.packagePattern", "com.example.**.dto.**")

    arg("kraft.side.domain.name",           "Domain")
    arg("kraft.side.domain.packagePattern", "com.example.**.domain.**")
}
```

Given the mapper:

```kotlin
package com.example.feature.domain
data class Category(val id: Int, val label: String)

package com.example.feature.dto
data class CategoryDto(val id: Int, val label: String)

@MapConfig(source = CategoryDto::class, target = Category::class)
object CategoryMapper
```

Kraft generates both the verbose mapper and a short alias:

```kotlin
public fun CategoryDto.toCategory(): Category = Category(
  id = this.id,
  label = this.label
)

/**
 * Alias generated for side Domain (template = to{side})
 */
public fun CategoryDto.toDomain(): Category = toCategory()
```

Call sites use the short form: `dto.toDomain()`. The verbose form (`toCategory()`) remains available — it's the canonical mapper that Kraft's nested resolution uses internally.

## Configuration Fields

Each side is a group of KSP args sharing a slot key (`dto`, `domain`, `entity` in the example above). The slot key is internal; the user-facing label comes from `name`.

| Field | Required | Default | Meaning |
|---|---|---|---|
| `name` | yes | — | Substituted into `{side}` in the template. Verbatim, case preserved. |
| `packagePattern` | yes | — | Ant-style glob matched against the **target** class's FQN to decide which side names this mapper. |
| `template` | no | `to{side}` | Alias function name. Variables: `{side}`, `{target}`, `{source}`. |
| `emitMode` | no | `BOTH` | `BOTH` or `FULL_NAME_ONLY`. Project-level default; per-mapper override goes on `@MapConfig`. |

> **Note:** `packagePattern` is matched against the target class only. For `@MapReverse`, each direction resolves against its own target — see the [`@MapReverse` interaction](#mapreverse-interaction) section below.

## Template Variables

A side's `template` is the entire alias function name. Three substitutions are supported:

| Variable | Expands to |
|---|---|
| `{side}` | The side's `name` value |
| `{target}` | Simple name of the target class |
| `{source}` | Simple name of the source class |

Substitution is verbatim — no auto-casing, no pluralization. The `to` prefix is **not** a hardcoded convention; the template is the entire function name.

### Worked examples

For the mapper `Category ← ProductCategoryDto` with side label `Domain`:

| Template | Generated extension |
|---|---|
| `to{side}` *(default)* | `fun ProductCategoryDto.toDomain(): Category` |
| `to{side}{target}` | `fun ProductCategoryDto.toDomainCategory(): Category` |
| `to{target}` | `fun ProductCategoryDto.toCategory(): Category` |
| `from{source}` | `fun ProductCategoryDto.fromProductCategoryDto(): Category` |
| `to{side}From{source}` | `fun ProductCategoryDto.toDomainFromProductCategoryDto(): Category` |

### Validation

Templates are validated at processor init:

- The substituted result must be a valid Kotlin identifier — empty results, results starting with a digit, or containing illegal characters fail loudly.
- Unknown variable references (typos like `{traget}`) fail loudly — never kept as literal text.

## Emit Modes

Set per-side via `kraft.side.<slot>.emitMode`:

| Mode | Behaviour |
|---|---|
| `BOTH` *(default)* | Emit both the verbose mapper and the alias. |
| `FULL_NAME_ONLY` | Emit only the verbose mapper. Useful when a side is registered for collision tracking but aliases aren't wanted yet. |

`ALIAS_ONLY` is intentionally **not** supported — Kraft's own generated code calls verbose names for nested resolution, so suppressing them would break internals.

## Per-Mapper Override

A `@MapConfig` can override the side's emit mode for a single mapper:

```kotlin
import com.blu3berry.kraft.config.AliasEmitMode

@MapConfig(
    source = CategoryDto::class,
    target = Category::class,
    aliasEmitMode = AliasEmitMode.FULL_NAME_ONLY,
)
object CategoryMapper
```

Resolution order:

1. `aliasEmitMode = INHERIT` *(default)* → use the matched side's `emitMode` from Gradle.
2. `BOTH` or `FULL_NAME_ONLY` → override the side's setting for this mapper only.
3. No matching side → no alias regardless of mode.

The default is `INHERIT` so most mappers don't need to think about aliasing; only mappers needing an exception carry the parameter.

### When to use the override

- Two `@MapConfig`s targeting the same side that would otherwise collide on the alias name (see [Alias collisions](#alias-collisions) below).
- Mappers between two types that both happen to be in the same registered side (e.g. a `Domain ↔ Domain` transformation) where the alias name is misleading.
- Gradual project adoption: set the project default to `FULL_NAME_ONLY`, then opt individual mappers in to `BOTH` as call sites migrate.

## `@MapReverse` Interaction

Each direction resolves independently against its own target's package:

```kotlin
@MapReverse
@MapConfig(source = CategoryDto::class, target = Category::class)
object CategoryMapper
```

With `Dto` and `Domain` sides registered, this emits two aliases:

- Forward: `fun CategoryDto.toDomain(): Category` (target `Category` is in the Domain side).
- Reverse: `fun Category.toDto(): CategoryDto` (target `CategoryDto` is in the Dto side).

No special configuration required.

## Errors

### Pattern overlap

If two sides' `packagePattern` values both match the same target class, Kraft fails with a Gradle-config-shaped error:

```
Kraft side configuration error: package patterns overlap.

Class com.example.feature.data.api.UserDto matches two sides:
  - kraft.side.dto.packagePattern  = "**.data.**"
  - kraft.side.api.packagePattern  = "**.data.api.**"

Patterns must be disjoint. Tighten one of the two patterns so the
classes you intend each side to match no longer overlap.
```

The fix is in `build.gradle.kts`, not in any individual `@MapConfig`. Tighten one pattern.

### Alias collisions

Two `@MapConfig`s producing the same `(receiverFqn, aliasName)` pair fail with an error naming both declarations and suggesting two fixes:

1. Disambiguate via template (e.g. change the side template to `to{side}{target}`).
2. Set `aliasEmitMode = FULL_NAME_ONLY` on one of the colliding mappers.

> **Cross-module limitation:** Kraft processes one module at a time, so cross-module alias collisions are not detected at generation time. Callers will hit Kotlin's "ambiguous extension" error at the call site, which is a sufficiently clear signal.

## Glob Syntax

`packagePattern` supports Ant-style globs:

| Token | Meaning |
|---|---|
| `*` | Matches a single package segment (no dots). |
| `**` | Matches any number of segments, including zero. |

Examples:

| Pattern | Matches |
|---|---|
| `com.example.**.dto.**` | Any class under any sub-package whose path includes `…dto…`. |
| `com.example.feature.*.api` | `com.example.feature.users.api.UserDto` but not `com.example.feature.users.v1.api.UserDto`. |
| `**.entity.**` | Any class under any package containing an `entity` segment. |

## See also

- [KSP Options — kraft.side.*](ksp-options.md#kraftside) — reference card for the four side fields.
- [Reverse Mapping](reverse-mapping.md) — how `@MapReverse` interacts with side aliases.
