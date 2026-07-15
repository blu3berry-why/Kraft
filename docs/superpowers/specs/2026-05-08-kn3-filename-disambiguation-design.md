# K-N3: Generated Mapper Filename Disambiguation

**Date:** 2026-05-08
**Status:** Approved (pending user review of this spec)
**Type:** Bug fix

## Problem

Kraft's KSP processor emits one Kotlin file per generated mapper. Filenames today are derived from source/target **simple names only**:

- Enum mappers: `<sourceSimpleName>_To_<targetSimpleName>_EnumMapper.kt`
  (`kraft-ksp/.../codegen/generator/EnumMapperGenerator.kt:73`,
  via `CodeGenUtils.buildFileName` at `kraft-ksp/.../processor/util/CodeGenUtils.kt`)
- Extension mappers: `<sourceSimpleName>To<targetSimpleName>Mapper.kt`
  (`kraft-ksp/.../codegen/generator/ExtensionMapperGenerator.kt:38`)

When two declarations target enums (or classes) whose **simple names are identical but FQNs differ** — typically nested types like `AuthMe200Response.Role` and `AuthResponse.User.Role` — KSP throws:

```
kotlin.io.FileAlreadyExistsException:
.../Role_To_UserRole_EnumMapper.kt
```

The user has no in-Kraft escape hatch other than abandoning `@MapEnum`/`@MapConfig` for those pairs and hand-writing converters with disambiguated function names — exactly the K-A1 workaround documented in the `kraft-mappers` skill.

This bites every codebase that uses an OpenAPI generator (e.g. kmpgen) which inlines `$ref`'d schemas as nested classes, producing N parallel copies of the same logical enum.

## Decision

**Always include the parent-class chain in the generated filename when the source or target is a nested class.** Top-level classes keep their current naming.

Rejected alternatives:
- *Collision-only disambiguation* — requires two-pass codegen and an orchestrator change for negligible cosmetic benefit.
- *FQN hash suffix* — adds zero safety over parent-chain (parent chain + simple name + package = FQN, which is unique by Kotlin's own guarantees), and produces unstable, unreadable filenames.

## Filename format

Define `qualifiedSegments(decl)` as the chain of simple names from the outermost enclosing class down to the declaration itself, joined by `_`:

| Declaration | `qualifiedSegments` |
|---|---|
| `com.foo.Status` (top-level) | `Status` |
| `com.foo.AuthResponse.Role` (1-level nested) | `AuthResponse_Role` |
| `com.foo.AuthResponse.User.Role` (2-level nested) | `AuthResponse_User_Role` |

New filename rules:

- **Enum mapper:** `${qualifiedSegments(source)}_To_${qualifiedSegments(target)}_EnumMapper.kt`
- **Extension mapper:** `${qualifiedSegments(source)}To${qualifiedSegments(target)}Mapper.kt`

For top-level types this is identical to the current output (backward compatible). For nested types it expands to disambiguate.

## Why this is provably sufficient

Two filenames collide iff:
1. Source `qualifiedSegments` + simple name are identical, and
2. Target `qualifiedSegments` + simple name are identical, and
3. Generated packages match (derived from source package).

(1) + (3) ⇒ identical source FQN ⇒ same Kotlin class. Same for target. So a collision implies the two declarations are *the same pair*, which is either a duplicate `@MapEnum`/`@MapConfig` (a separate user error Kraft handles independently) or impossible.

A hash would only help if filenames encoded data **not** derivable from declaration FQNs (e.g. annotation argument values). They don't. K-N3 is purely about (source, target) pair disambiguation.

## Scope

**In scope:**
1. Add a parent-chain-aware filename helper.
2. Use it in `EnumMapperGenerator` (replaces line 73 path).
3. Use it in `ExtensionMapperGenerator` (replaces line 38 path).
4. Update existing tests that assert on filenames *for nested-type fixtures only* — top-level fixture filenames must remain unchanged (regression guard).

**Out of scope:**
- Function names — extension function names stay as today (Kotlin resolves overloads by receiver type, no collision at call site).
- Imports — generated `import` statements are unaffected; this is purely a file-on-disk concern.
- Cross-module shared registries (K-N5).
- `@MapUsing` extensions (K-N1/N2/N4).
- Refactoring `CodeGenUtils.buildFileName` signature beyond what's needed for this fix.

## Testing strategy (TDD)

Test ordering — write failing tests first, confirm they fail, then implement.

1. **Failing test 1 — enum collision:**
   `kraft-ksp/src/jvmTest/.../mapenum/MapEnumNestedSimpleNameCollisionTest.kt`
   Two `@MapEnum` declarations whose source enums are nested classes sharing the simple name `Role` (different parents), both targeting the same domain enum. Expect KSP to compile cleanly and emit two distinct files. On unfixed code this test must throw `FileAlreadyExistsException`.

2. **Failing test 2 — extension collision:**
   `kraft-ksp/src/jvmTest/.../basic/MapConfigNestedSimpleNameCollisionTest.kt`
   Same shape but with `@MapConfig` on data-class pairs. Expect two files emitted with parent-chain-prefixed names.

3. **Regression test — top-level filenames unchanged:**
   Add an assertion in an existing top-level mapper test (e.g. `EnumByNameAutoTest`) that the emitted filename is exactly `Status_To_StatusDto_EnumMapper.kt` — explicit guard so future filename refactors don't silently break the common case.

4. **Existing tests:**
   Run the full `:kraft-ksp:test` suite. Any test that asserts on a *nested-type* generated filename must be updated to the new naming. Tests asserting top-level names should remain untouched.

5. **Verification command:**
   `./gradlew :kraft-ksp:test detekt` must pass.

## Implementation outline

1. Add `KSClassDeclaration.qualifiedSegments(): String` extension (or a helper in `CodeGenUtils`) — walks `parentDeclaration` chain up to file/package level, joining simple names with `_`.
2. `CodeGenUtils.buildFileName(...)`: change signature to accept `KSClassDeclaration` (or pre-resolved segment strings) instead of bare `simpleName: String`. Keep behavior for top-level identical.
3. Update both generator call sites.
4. Update test assertions for any nested-fixture tests.

Concrete migration path for `CodeGenUtils.buildFileName` is a single function — no need to introduce parallel APIs. One commit, one mechanical refactor.

## Risk and rollback

- **Risk:** A downstream consumer asserts on generated filenames in their own tests. Mitigation: top-level naming is unchanged, so only consumers using nested types see different filenames. Note in the changelog.
- **Rollback:** Single revert of the implementation commit. The change is mechanical and self-contained to two generators + one util.

## Commit message

`fix(kraft-ksp): disambiguate generated mapper filenames when target simple names collide`

## Out of scope (deferred to future sessions)

- K-N1 + K-N2 (extend `@MapUsing` to accept a literal expression — one merged annotation surface)
- K-N4 (`@MapDecompose` / `@MapCompose`) — gated on a 10-min spike confirming `@MapUsing` doesn't already cover the case
- K-N5 (docs + clearer error messages for missing cross-module converters)
