# Proposal

## Why

Roadmap: #40, #29 (docs/ROADMAP.md, target 0.15).

Every `@MapConfig` rule is nested in an array argument (`fieldMappings = [FieldMapping(...)]`,
`ignoredMappings = [MapIgnoreField(...)]`), which is hard to read, review and edit (#40). A
target property that should get a fixed or computed value has no direct way to get it: today
it needs a whole-source `@MapUsing` (#29). The two are designed together because both decide
what a mapper object looks like.

## What Changes

- `@FieldMapping` and `@MapIgnoreField` can also be written as flat, repeatable annotations
  on the `@MapConfig` object, one line per rule. The annotation classes stay the same; they
  gain `@Target(CLASS)`, `@Retention(SOURCE)` and `@Repeatable`.
- The array form (`fieldMappings = [...]`, `ignoredMappings = [...]`) keeps working
  unchanged. The two forms can be mixed on one object.
- The same target given in both forms (a `@FieldMapping` for one target, or a
  `@MapIgnoreField` for one name, in the array and as a flat annotation) is a compile error
  with a new code.
- New annotation `@MapConstant`, placed on a property or a zero-parameter function **inside**
  the `@MapConfig` object. The member's value is plain Kotlin, so any value or expression
  works and nothing is written as a string. The target defaults to the member's name, and
  `target = "..."` overrides it. A `val` is evaluated once and a `fun` on every call.
- New compile errors for `@MapConstant`: unknown target, type mismatch, unsupported member
  (private, has parameters, extension, not inside a `@MapConfig` object), and a conflict with
  another rule on the same target.
- **Deviation from the roadmap text:** #29 described `@MapConstant` as a class-level
  annotation with the value as a string. It is a member annotation instead (decided
  2026-10-18), so values are real Kotlin that the compiler checks.
- No generated function signatures change. Not breaking: `FieldMapping` and `MapIgnoreField`
  move from the default retention (`RUNTIME`) to `SOURCE`. Kraft only reads them at KSP
  time, and nothing reads them by reflection.

## Capabilities

### New Capabilities

- `annotations/flat-config-rules`: writing `@MapConfig` rules as repeatable class-level
  annotations, how they combine with the array form, and the both-forms conflict error.
- `mapping/constant-values`: assigning a target property from a `@MapConstant` member of the
  mapper object: target naming, type checking, direction, member rules and conflicts.

### Modified Capabilities

None. Each new code adds an entry to the errors reference that `diagnostics/diagnostic-codes`
already requires, and none of that spec's requirements change.

## Impact

- `kraft-annotations`: `FieldMapping.kt` and `MapIgnoreField.kt` get target, retention and
  repeatable meta-annotations. New `MapConstant.kt`.
- `kraft-core`: `ConfigObjectScanner` reads the flat annotations and `@MapConstant` members.
  `ConfigObjectScanResult` gains a `constants` list (the flat form adds no fields). There is a
  new resolver rule and a new `PropertyMappingStrategy`, and `KraftDiagnostic` gets new codes.
- `kraft-ksp`: `CtorCallBuilder` emits the new strategy. New jvmTests.
- `integration-tests/kmp-consumer`: one flat-form mapper and one `@MapConstant` mapper,
  checked during the KMP metadata compilation.
- Docs: `configuration-objects.md`, `field-renaming.md`, `ignore-rules.md`, `errors.md`, a
  new section on constant values, `README.md`, `.claude/skills/kraft-mappers/SKILL.md` and
  `docs/ROADMAP.md`.
- Feasibility was checked by a throwaway spike on 2026-10-18. Class-targeted
  `SOURCE`/`@Repeatable` annotations still compile as `@MapConfig` array elements on
  metadata, JVM and JS. KSP2 (2.3.3) returns each repeated annotation separately, arguments
  included, during `kspCommonMainKotlinMetadata`.
