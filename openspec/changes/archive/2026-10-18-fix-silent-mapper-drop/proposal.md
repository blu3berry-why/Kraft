# Proposal

## Why

When a target property has no matching source property and is either defaulted (`val score: Int = 0`) or nullable (`val nickname: String?`), Kraft drops the **whole mapper**. It generates no file and reports no error or warning. The user's first sign of trouble is an unresolved `toXxx()` call, far from the cause.

`docs/user-guide/basic-mapping.md` ("Properties with Default Values") promises the opposite: the defaulted property is omitted and the mapper is generated. The existing `RequiredFieldErrorRuleTest` "no error" cases pass only because they check the exit code, not that a mapper exists. This was confirmed on 2026-10-18 by running that test: its compilation output holds only the user's two classes.

Root cause: `RequiredFieldErrorRule` returns `null` without reporting for non-required targets, `PropertyResolver.resolve` turns that into `null`, and all three descriptor builders (`ClassDescriptorBuilder`, `ConfigDescriptorBuilder`, `ReverseDescriptorBuilder`) treat a `null` property as "abort this mapper".

## What Changes

- **Defaulted target, no source:** the property is omitted from the constructor call, so Kotlin uses its default. The mapper is generated with no diagnostic, as the docs already say.
- **Nullable target with no default, no source:** the generated call passes `<name> = null`, and Kraft emits the new warning **`KRAFT-W009`** naming the property. The warning keeps this case visible, because a renamed source property would otherwise turn into a silent `null`. It lists the fixes:
  - give the target property `= null`, which is the explicit opt-in and produces no warning;
  - map it explicitly with a whole-source `@MapUsing(target = "<name>")`;
  - add `@Suppress("KRAFT-W009")` on the target property or on the mapping declaration (the `@MapConfig` object or the `@MapFrom` / `@MapTo` class).
- **No silent drops, ever:** if a property still fails to resolve and nothing was reported for it, Kraft reports a new error **`KRAFT-E065`** ("Mapper dropped without a diagnostic") instead of skipping the mapper. This guards the whole bug class, not just this instance.
- The fallback never adds a `null` or an omission on top of an error that is already reported. For example, a nullable target whose same-named source has the wrong type still fails with the type-mismatch error only.
- The KRAFT-E004 fix hint "Or make the property nullable" stays accurate, and now mentions KRAFT-W009.
- **Upgrade note (behaviour change, not a signature change):** mappers that were silently missing are now generated. A consumer who worked around the gap with a hand-written extension of the same name (`fun Src.toDto()`) gets a conflicting-overloads error and must delete the hand-written function. This goes on the Upgrading page.
- Commit type is `fix:`. The project config skips OpenSpec for fixes, but this one adds a diagnostic and changes generated output, so it goes through a spec on request.

Out of scope: an explicit constant annotation (`@MapConstant`, roadmap Tier 1 #3). KRAFT-W009's fix list gains that option when the annotation ships. A configurable unmapped-target policy (roadmap Tier 2 #9) is also out of scope.

## Capabilities

### New Capabilities
- `mapping/missing-source-properties`: what Kraft generates, or reports, for a target property that has no source property, override or converter. Covers defaulted, nullable and required targets, the KRAFT-W009 warning and its suppression, and the guarantee that a mapper is never dropped without a diagnostic (KRAFT-E065).

### Modified Capabilities
- None. `diagnostics/diagnostic-codes` already requires every new code to carry its prefix and link and to have an errors-page entry. W009 and E065 follow it without changing its requirements.

## Impact

- `kraft-core`: `PropertyResolver` (rule chain, per-property error tracking), new fallback rule before `RequiredFieldErrorRule`, `MappingContext` (mapping declaration for suppression), `KraftDiagnostic` (W009, E065), `LoggerExtensions` (two messages, E004 hint), the three descriptor builders (E065 guard).
- `kraft-ksp`: no codegen change. `CtorCallBuilder` already renders `Ignored` (omit) and `Constant` (`name = null`).
- Generated function signatures: unchanged.
- Tests: new `kraft-ksp/src/jvmTest/.../missingsource/` suite. The three `RequiredFieldErrorRuleTest` "no error" cases are tightened to assert that the mapper exists.
- Docs: `basic-mapping.md`, `errors.md` (W009, E065, E004 hint), `upgrading.md`, `docs/ROADMAP.md` (new bug entry), `.claude/skills/kraft-mappers/SKILL.md`.
