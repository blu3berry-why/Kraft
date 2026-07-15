# Docs — Side Aliases + Audit Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Document the side-aliases feature in the user guide (new page + KSP-options reference), surface it in `README.md` / `docs/index.md` feature lists, fix stale `IgnoreSide.kt` KDoc, and log the work in `docs/tasks.md`.

**Architecture:** Three commits — (1) side-aliases doc bundle (new page + ksp-options append + nav + feature-list updates), (2) `IgnoreSide.kt` KDoc fix, (3) no third commit; `docs/tasks.md` is local-only per project convention. Plus one local-only edit to `docs/tasks.md`.

**Tech Stack:** Markdown (mkdocs-material), Kotlin source comments. No production code paths touched.

**Spec:** `docs/superpowers/specs/2026-05-09-docs-side-aliases-and-audit-design.md`

**When this plan and the spec conflict, the spec wins.**

---

## Pre-cleared deviations

- **Verbose mapper name in examples is `to<Target>()`, not `to<Target>From<Source>()`.** The original side-aliases spec (`2026-05-08-kraft-side-aliases-design.md`) describes the verbose form as `toCategoryFromProductCategoryDto()`, but the actual default `kraft.functionNameFormat` is `to${target}` and the generator emits `toCategory()`. Verified against `composeApp/build/generated/ksp/metadata/commonMain/kotlin/.../CategoryDtoToCategoryMapper.kt.kt` line 11. All examples in this plan use the actual emitted shape.
- **`docs/tasks.md` is not committed.** Per user feedback memory `feedback_docs_not_committed`, project-management docs (tasks.md and `docs/superpowers/`) live in the working tree but are never staged or pushed. Task 6 updates the file in place; no commit step.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `docs/user-guide/side-aliases.md` | Create | Full feature guide (intro, quick start, fields, templates, emit modes, override, `@MapReverse`, errors, glob syntax) |
| `docs/user-guide/ksp-options.md` | Modify | Append `## kraft.side.*` reference section after the existing `kraft.moduleId` section |
| `mkdocs.yml` | Modify | Add `Side Aliases: user-guide/side-aliases.md` to nav under User Guide |
| `docs/index.md` | Modify | Insert one Features bullet for side aliases |
| `README.md` | Modify | Insert one Features bullet for side aliases; add row to Quick Reference table for `aliasEmitMode` |
| `kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/IgnoreSide.kt` | Modify | Replace stale "future use" KDoc with current behaviour description |
| `docs/tasks.md` | Modify (local only, no commit) | Add F-10 in-progress entry |

---

## Task 1: Create the side-aliases user-guide page

**Files:**
- Create: `docs/user-guide/side-aliases.md`

- [ ] **Step 1.1: Write the new file**

Create `docs/user-guide/side-aliases.md` with the following exact content:

````markdown
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

Register sides as KSP processor arguments in the module that applies the KSP plugin:

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
    label = this.label,
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
````

- [ ] **Step 1.2: Verify the file lives in the user-guide directory**

Run: `ls -la docs/user-guide/side-aliases.md`
Expected: file exists, ~200 lines.

---

## Task 2: Append `kraft.side.*` reference to KSP Options

**Files:**
- Modify: `docs/user-guide/ksp-options.md` (append after line 134)

- [ ] **Step 2.1: Append the new section**

Open `docs/user-guide/ksp-options.md`. After the final line of the existing file (currently line 134, ending with `the default hash is sufficient.`), append a blank line then the following:

````markdown

## kraft.side.*

Register named layers (`Dto`, `Domain`, `Entity`, …) so Kraft emits short alias extensions like `.toDomain()` alongside the verbose mappers. See [Side Aliases](side-aliases.md) for the full feature guide; this section is a reference of the four KSP keys.

### Default

Unset. No sides are registered, no aliases are emitted, and behaviour is identical to a build with no side configuration.

### Keys

Each side is identified by a slot key (your choice of lowercase identifier — e.g. `dto`, `domain`). All four keys share the prefix `kraft.side.<slot>.`:

| Key | Required | Default | Type |
|---|---|---|---|
| `kraft.side.<slot>.name` | yes | — | string — substituted into `{side}` in the template, verbatim |
| `kraft.side.<slot>.packagePattern` | yes | — | Ant-style glob matched against the target class FQN |
| `kraft.side.<slot>.template` | no | `to{side}` | string — alias function name; variables: `{side}`, `{target}`, `{source}` |
| `kraft.side.<slot>.emitMode` | no | `BOTH` | `BOTH` or `FULL_NAME_ONLY` |

### Configuration

```kotlin
// build.gradle.kts
ksp {
    arg("kraft.side.dto.name",           "Dto")
    arg("kraft.side.dto.packagePattern", "com.example.**.dto.**")

    arg("kraft.side.domain.name",           "Domain")
    arg("kraft.side.domain.packagePattern", "com.example.**.domain.**")
}
```

For a `@MapConfig(source = CategoryDto::class, target = Category::class)` mapper where `Category` lives under `com.example.feature.domain`, this generates:

```kotlin
public fun CategoryDto.toCategory(): Category = /* … */

/** Alias generated for side Domain (template = to{side}) */
public fun CategoryDto.toDomain(): Category = toCategory()
```

### Per-mapper override

A `@MapConfig` can opt out of (or into) alias emission via `aliasEmitMode = AliasEmitMode.{INHERIT, BOTH, FULL_NAME_ONLY}`. See [Side Aliases — Per-Mapper Override](side-aliases.md#per-mapper-override).
````

- [ ] **Step 2.2: Verify the file**

Run: `wc -l docs/user-guide/ksp-options.md`
Expected: file has grown by ~45 lines (was 134, now ~180).

Run: `grep -n "kraft.side" docs/user-guide/ksp-options.md | head -5`
Expected: at least one match showing the new section was added.

---

## Task 3: Update nav + feature lists

**Files:**
- Modify: `mkdocs.yml` (line 60 area)
- Modify: `docs/index.md` (Features list)
- Modify: `README.md` (Features list + Quick Reference table)

- [ ] **Step 3.1: Add nav entry to mkdocs.yml**

Open `mkdocs.yml`. Find the line:

```yaml
      - KSP Options: user-guide/ksp-options.md
```

Insert immediately after it:

```yaml
      - Side Aliases: user-guide/side-aliases.md
```

Final ordering under User Guide should be: KSP Options → Side Aliases → AI Integration.

- [ ] **Step 3.2: Add Features bullet to docs/index.md**

Open `docs/index.md`. Find the existing bullet:

```markdown
- **Reverse mapping** — `@MapReverse` generates the inverse mapper automatically
```

Insert immediately after it:

```markdown
- **Layer-aware aliases** — register `Dto` / `Domain` / `Entity` sides via Gradle to emit short `.toDomain()` / `.toEntity()` extensions alongside the verbose mappers
```

- [ ] **Step 3.3: Add Features bullet to README.md**

Open `README.md`. Find the existing bullet:

```markdown
- **Reverse mapping** — `@MapReverse` generates the inverse mapper automatically
```

Insert immediately after it:

```markdown
- **Layer-aware aliases** — register `Dto` / `Domain` / `Entity` sides via Gradle to emit short `.toDomain()` / `.toEntity()` extensions alongside the verbose mappers
```

- [ ] **Step 3.4: Add Quick Reference row to README.md**

Open `README.md`. In the Quick Reference table (currently lines 83–97), find the row:

```markdown
| `@MapReverse` | Generate inverse mapper | On class or `@MapConfig` object |
```

Insert immediately after it:

```markdown
| `aliasEmitMode` | Per-mapper alias control (`AliasEmitMode` enum) | Parameter on `@MapConfig` |
```

- [ ] **Step 3.5: Verify all four files**

Run:
```bash
grep -n "side-aliases\|Side Aliases" mkdocs.yml docs/index.md README.md
grep -n "aliasEmitMode" README.md
```
Expected:
- `mkdocs.yml` has one Side Aliases nav entry
- `docs/index.md` has one Layer-aware aliases bullet
- `README.md` has one Layer-aware aliases bullet and one `aliasEmitMode` table row

- [ ] **Step 3.6: Optional — verify mkdocs renders cleanly**

Run (only if Python + pip available locally):
```bash
pip install --quiet mkdocs-material 2>/dev/null && mkdocs build --strict 2>&1 | tail -20
```
Expected: build completes without `WARNING` or `ERROR`. The `--strict` flag promotes any warning (broken link, missing nav target) to a non-zero exit code. If `mkdocs` isn't installed locally, skip this — CI's `docs-deploy.yml` workflow will catch issues on push.

- [ ] **Step 3.7: Commit Tasks 1–3 as one bundle**

```bash
git add docs/user-guide/side-aliases.md \
        docs/user-guide/ksp-options.md \
        mkdocs.yml \
        docs/index.md \
        README.md
git commit -m "docs: add side-aliases user guide and surface in feature lists

Adds dedicated docs/user-guide/side-aliases.md covering the kraft.side.*
KSP options, AliasEmitMode override, @MapReverse interaction, and error
modes. Cross-references from ksp-options.md, README.md Quick Reference
table, README and docs/index.md feature lists, and mkdocs.yml nav."
```

Expected: commit succeeds; pre-commit hooks (lefthook) pass.

---

## Task 4: Fix stale `IgnoreSide.kt` KDoc

**Files:**
- Modify: `kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/IgnoreSide.kt`

- [ ] **Step 4.1: Replace the class-level KDoc**

Open `kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/IgnoreSide.kt`.

Replace lines 3–9 (the existing class-level KDoc):

```kotlin
/**
 * Controls which mapping direction(s) a [MapIgnoreField] applies to.
 *
 * When the config object generates only a single direction today, [SOURCE] entries
 * are stored but not applied; they will activate automatically once reverse-mapping
 * generation is introduced.
 */
```

With:

```kotlin
/**
 * Controls which mapping direction(s) a [MapIgnoreField] applies to.
 *
 * Use [SOURCE] together with [MapReverse] to ignore a property only on the reverse
 * direction; [TARGET] applies to the forward direction; [BOTH] (the default) applies
 * to whichever direction the named property exists in.
 */
```

- [ ] **Step 4.2: Replace the per-entry KDoc on `SOURCE`**

In the same file, replace line 14:

```kotlin
    /** Apply only when mapping `to → from` (reverse direction, reserved for future use; name is a **source**-side constructor parameter). */
```

With:

```kotlin
    /** Apply only when mapping `to → from` (reverse direction, honored by `@MapReverse`; name is a **source**-side constructor parameter). */
```

- [ ] **Step 4.3: Verify the edits**

Run: `grep -n "future use\|will activate" kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/IgnoreSide.kt`
Expected: no matches.

Run: `grep -n "honored by" kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/IgnoreSide.kt`
Expected: one match on the `SOURCE` line.

- [ ] **Step 4.4: Run a quick build to confirm no compile regression**

The KDoc is a documentation-only change, but the file is part of the published `kraft-annotations` artifact. Run:

```bash
./gradlew :kraft-annotations:compileKotlinMetadata --quiet
```

Expected: BUILD SUCCESSFUL. (If the toolchain is unavailable, this step can be skipped — the change is comment-only and can't break compilation; CI's `test.yml` will catch anything unexpected.)

- [ ] **Step 4.5: Commit**

```bash
git add kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/IgnoreSide.kt
git commit -m "docs(kraft-annotations): refresh IgnoreSide KDoc for shipped @MapReverse

The class-level and SOURCE-entry KDocs described SOURCE as 'reserved for
future use' / 'stored but not applied'. @MapReverse shipped (tasks F-7)
and SOURCE is the active reverse-direction selector — KDoc now matches
the user-facing docs/user-guide/ignore-rules.md description."
```

Expected: commit succeeds.

---

## Task 5: Update `docs/tasks.md` (local-only, NO commit)

**Files:**
- Modify: `docs/tasks.md`

> **Important:** `docs/tasks.md` is a local project-tracking file and is **never committed to git** (per project convention; `docs/superpowers/` and `docs/tasks.md` both stay untracked). This task updates the file in place only — no `git add`, no commit.

- [ ] **Step 5.1: Add the F-10 entry to the Backlog table**

Open `docs/tasks.md`. In the "Backlog" table (lines 8–16), insert a new row after the `M-1` row (currently the last row before `---` at line 17):

```markdown
| **F-10** Side aliases — short layer-aware names | High | Implementation built on `feat/side-aliases-design` (annotations, processor, tests, sample); docs landing in this PR. Design: `docs/superpowers/specs/2026-05-08-kraft-side-aliases-design.md`. |
```

- [ ] **Step 5.2: Verify the entry exists and is NOT staged**

Run:
```bash
grep -n "F-10" docs/tasks.md
git status --short docs/tasks.md
```
Expected:
- One match on the new F-10 line.
- `git status` shows `M docs/tasks.md` (modified but unstaged) OR `??` if `docs/tasks.md` was untracked. Either is fine; the file must NOT appear under "Changes to be committed".

- [ ] **Step 5.3: Confirm no commit happens for this task**

This is a passive step. Do NOT run `git add docs/tasks.md`. Do NOT run `git commit` for this task. The next commit you create (or the user creates) must not include `docs/tasks.md` — verify with `git status` before any commit you make later in this session.

---

## Self-Review

After completing all tasks, verify:

1. **Spec coverage** — every section of `docs/superpowers/specs/2026-05-09-docs-side-aliases-and-audit-design.md` maps to a task:
   - Spec Section 1 (new side-aliases.md) → Task 1 ✓
   - Spec Section 2 (ksp-options.md append) → Task 2 ✓
   - Spec Section 3 (nav + feature lists) → Task 3 ✓
   - Spec Section 4 (IgnoreSide.kt KDoc) → Task 4 ✓
   - Spec Section 5 (tasks.md, local-only) → Task 5 ✓

2. **Final state check** — run:
   ```bash
   git log --oneline -3
   git status --short
   ```
   Expected:
   - Top two commits are the docs-bundle commit (Task 3.7) and the IgnoreSide commit (Task 4.5).
   - `git status` shows at most one modified file: `docs/tasks.md` (unstaged) or no changes if `docs/tasks.md` was already tracked-and-edited cleanly.

3. **Generated example accuracy** — open `docs/user-guide/side-aliases.md` and confirm the Quick Start "Generated" code block matches the structure in `composeApp/build/generated/ksp/metadata/commonMain/kotlin/com/blu3berry/kraft/sample/dto/generated/CategoryDtoToCategoryMapper.kt.kt` (verbose name = `to<Target>()`, alias has the `Alias generated for side …` KDoc).

4. **Cross-link integrity** — every `[…](…)` link inside `side-aliases.md` and `ksp-options.md` resolves to a real anchor or file. Quick check:
   ```bash
   grep -oE "\[[^]]+\]\(([^)]+)\)" docs/user-guide/side-aliases.md docs/user-guide/ksp-options.md
   ```
   Expected: all relative paths and anchors look valid (no obvious typos).

If anything fails, fix inline and re-run the affected verification steps.

---

## Out of scope

The audit suggested these items but they are **deliberately not** in this plan (per spec):

- `ai-integration.md` mention of side aliases — defer until feature is in user hands.
- Cross-references from `custom-converters.md` and `configuration-objects.md` to `ksp-options.md` — "nice-to-have", not gaps.
- Any developer-guide file changes (`architecture.md`, `custom-code-generator.md`).
