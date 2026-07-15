# Docs — Side Aliases + Audit Fixes — Design

**Status:** Approved (user confirmed scope 2026-05-09)
**Date:** 2026-05-09
**Branch:** `feat/side-aliases-design`

---

## Problem

Two concrete documentation gaps:

1. The side-aliases feature (implemented on this branch) is undocumented. Its KSP options (`kraft.side.<slot>.{name,packagePattern,template,emitMode}`), the `AliasEmitMode` enum, and the `@MapConfig(aliasEmitMode = …)` parameter exist in code but appear nowhere in `docs/user-guide/`.
2. `IgnoreSide.kt` carries stale KDoc claiming `SOURCE` is "reserved for future use" / "stored but not applied". `@MapReverse` shipped (tasks.md F-7) and `SOURCE` is now the active reverse-direction selector. The user-facing `ignore-rules.md` is already correct; only the source KDoc is stale.

`docs/tasks.md` also has no entry tracking the side-aliases work even though it is substantially built on this branch.

## Goal

Ship side-aliases documentation as part of the feature branch so the feature is usable when it merges, and clean up the two specific staleness issues found by the audit. Do **not** expand into a broader docs rewrite.

## Non-goals

- No changes to `developer-guide/architecture.md` or `developer-guide/custom-code-generator.md`. The audit found no concrete inaccuracies — only thinner side-alias coverage, which is a feature-doc concern handled elsewhere.
- No additions to `ai-integration.md`. Side-alias agent guidance is a follow-up once the feature is in user hands.
- No "nice to have" cross-references in `custom-converters.md` or `configuration-objects.md`. Those were suggestions, not gaps.
- No restructuring of existing user-guide pages.

---

## Section 1 — New file: `docs/user-guide/side-aliases.md`

Full feature page modeled on the existing `enum-mapping.md` / `reverse-mapping.md` shape. Outline:

1. **Intro** — one paragraph: what aliases are (short layer-aware names like `.toDomain()`) and what they replace (the hand-written wrapper-file boilerplate from the design spec).
2. **Quick start** — minimal Gradle config registering `Dto` and `Domain` sides, the resulting `@MapConfig`, and the generated output (verbose function + alias delegate).
3. **Side configuration fields** — table covering `name`, `packagePattern`, `template`, `emitMode` with required/default/meaning columns. Same shape as the spec's Section 1 table.
4. **Template variables** — table of `{side}` / `{target}` / `{source}` plus the worked-examples table from the spec's Section 2.
5. **Emit modes** — `BOTH` (default) vs `FULL_NAME_ONLY`. Note that `ALIAS_ONLY` is intentionally unsupported (Kraft's nested resolution calls verbose names internally).
6. **Per-mapper override** — `aliasEmitMode = AliasEmitMode.{INHERIT, BOTH, FULL_NAME_ONLY}`, resolution order, when to use it.
7. **`@MapReverse` interaction** — each direction resolves against its own target's package; example with the `CategoryMapper` from the sample app.
8. **Pattern overlap & alias collisions** — short subsection: what the user sees on each error, with one-line "fix it by …" guidance. Not a deep dive — just enough that a user hitting the error knows how to react.
9. **Glob syntax** — `*` = single segment, `**` = any-or-zero segments. Two or three example patterns.

Length target: ~200 lines, in line with `enum-mapping.md` (148) and `reverse-mapping.md` (284).

## Section 2 — Append section to `docs/user-guide/ksp-options.md`

A new `## kraft.side.*` section after the existing `kraft.moduleId` block. Reference-style, terse:

- Lists each key (`kraft.side.<slot>.name`, `…packagePattern`, `…template`, `…emitMode`) with required/default/value-type.
- One short worked example (single side, single mapper) showing the Gradle block and the resulting generated function name.
- Cross-link out to `side-aliases.md` for "see full guide for emit modes, overrides, collisions".

This duplicates a small amount of content with the dedicated page on purpose — users browsing KSP options should find these without leaving the page.

## Section 3 — Nav and feature-list updates

| File | Change |
|---|---|
| `mkdocs.yml` | Add `- Side Aliases: user-guide/side-aliases.md` under User Guide, placed after `KSP Options`. |
| `docs/index.md` | Insert one bullet in the Features list (after the `Reverse mapping` bullet): *"Layer-aware aliases — register `Dto` / `Domain` / `Entity` sides via Gradle to emit short `.toDomain()` / `.toEntity()` extensions"*. |
| `README.md` Features list | Same bullet, same placement (after Reverse mapping). |
| `README.md` Quick Reference table | New row: `AliasEmitMode` (enum) / "Alias emission control" / "On `@MapConfig.aliasEmitMode`". |

## Section 4 — Stale KDoc on `IgnoreSide.kt`

File: `kraft-annotations/src/commonMain/kotlin/com/blu3berry/kraft/config/IgnoreSide.kt`

Two edits:

1. Class-level KDoc currently says: *"When the config object generates only a single direction today, `SOURCE` entries are stored but not applied; they will activate automatically once reverse-mapping generation is introduced."* — replace with: *"`SOURCE` applies in reverse mappings; pair it with `@MapReverse` to ignore properties only on the reverse direction."*
2. The per-entry KDoc on `SOURCE` says *"reserved for future use"* — replace with the same wording the user-facing `ignore-rules.md` line 83 uses: *"Honored by `@MapReverse`."*

No other code or test changes.

## Section 5 — `docs/tasks.md` entry (local-only, not committed)

`docs/tasks.md` is a local project-tracking file and is **not committed to git** (per user preference; consistent with `docs/superpowers/` also being untracked). The update is therefore an in-place local edit only — no commit, no staging.

Add an entry following the existing F-N / R-N pattern. Place it as in-progress now:

```
| **F-10** Side aliases — short layer-aware names | High | Implementation built on `feat/side-aliases-design` (annotations, processor, tests, sample); docs landing in this PR. Design: `docs/superpowers/specs/2026-05-08-kraft-side-aliases-design.md`. |
```

Once the branch merges, move the entry to the "Completed" table:

```
| F-10 | Side aliases | `AliasEmitMode` + `aliasEmitMode` on `@MapConfig`; `kraft.side.*` KSP args; `SideRegistry`, `SideConfig`, `AliasTemplate`; sample in composeApp |
```

Both updates are local-only edits — they never appear in a commit.

---

## Implementation order

The five sections are mostly independent and order-insensitive, but a sensible execution order is:

1. Section 1 — write `side-aliases.md` first (the dependency for the link in Section 2).
2. Section 2 — append KSP-options section (links into Section 1).
3. Section 3 — nav + feature lists (links into Section 1; trivial once Section 1 exists).
4. Section 4 — IgnoreSide KDoc (independent, can go any time).
5. Section 5 — tasks.md entry (independent, can go any time).

Sections 1–3 are the side-aliases doc bundle and form one logical commit. Section 4 is a separate commit (annotation source change, separate concern). Section 5 is a local-only edit, no commit. Two commits total.

## Acceptance criteria

- A user landing on the docs site after this PR can find `User Guide → Side Aliases` in the nav and read a self-contained guide that, combined with `ksp-options.md`, lets them register sides and emit aliases without consulting the design spec or the source code.
- `README.md` Features list and `docs/index.md` Features list both include side aliases. The two feature lists remain consistent.
- `IgnoreSide.kt` source KDoc no longer claims `SOURCE` is "future use" — it matches the behaviour shipped in F-7.
- `docs/tasks.md` lists side-aliases work in the appropriate state.
- No edits to developer-guide files, `ai-integration.md`, `custom-converters.md`, `configuration-objects.md`, or any other user-guide page beyond `ksp-options.md`.

## Out of scope (for follow-up if useful)

- Agent guidance for side aliases in `ai-integration.md` — wait until the feature is in users' hands.
- Migration tooling / docs for projects with hand-written wrapper files.
- Cross-reference cleanup elsewhere in the user guide.
