# Proposal

## Why

Kraft is increasingly driven by coding agents, and an agent only sees what the compiler prints. Re-Claw's 0.13.0 field report shows the cost. One `@MapUsing` hint suggests the wrong fix. Duplicate generated DTO types get blamed on Kraft. Hand-written list helpers and nullable-twin converters re-implement what Kraft already generates, and nothing tells the author. Once a diagnostic names its fix and links to stable reference docs, any reader (human or agent) can act on it without first loading a skill.

## What Changes

- **Error codes on every diagnostic.** Every Kraft error and warning gets a stable code (`KRAFT-E###` / `KRAFT-W###`) in its first line and a link to its anchor in a new `docs/user-guide/errors.md`. That page lists each code with its cause, a minimal reproduction and the fix.
- **New warning W001, redundant `@MapUsing`.** It fires when a property-source `@MapUsing` claims a property that Kraft could map natively: element-wise `List`/`Set`, nullable collection to `emptyList()`, a registered converter lifted through `?.`, a value-class wrap or unwrap, or a direct match. (Field report item C1.)
- **New warning W002, nullable-twin `@KraftConverter`.** It fires when a `@KraftConverter` maps `S? → T?` in a module that also registers `S → T`. Kraft already lifts the non-null converter through a safe call. (Field report item C2.)
- **Both warnings can be suppressed per declaration** with `@Suppress("KRAFT-W001")` / `@Suppress("KRAFT-W002")`, for converters whose bodies intentionally differ from the native mapping (filtering, sorting, logging).
- **Type-mismatch hint for duplicated generated types.** When source and target types share a simple name, differ in their parent-class chain, and have the same shape, `detailedTypeMismatch` adds: *"types differ only by enclosing class; this is usually a code generator duplicating an inlined `$ref` (e.g. kmpgen). Fix it in the generator, or bridge the pair with `@MapEnum` / `@KraftConverter`."* (Field report item C5.)
- **Defaulted annotation arguments that KSP leaves out no longer error.** In KMP metadata compilations KSP can omit default-valued arguments from `KSAnnotation.arguments`. Kraft then reports `Missing required argument 'source'` for `@MapUsing(target = "x")` and suggests `MyClass::class`, which is wrong for a `String` argument. Optional arguments now fall back to their declared default. The missing-argument hint now matches the argument's kind. (Field report item B1.)
- **Not in this change:**
  - The silent mapper drop (A1), the import collision (A2), the split-field `@MapReverse` error (A3), the duplicate DSL warning (A4) and stale KSP output (A5). Each is a `fix:` PR of its own.
  - B2. The parameterized-converter message on `main` already tells the user that `List`/`Set` properties map element-wise. This change only gives it a code.
  - C3, C4 and C6 (hand-written enum `when`, call-site imports of nested mappers, JVM-only APIs in converter bodies). None of them involves an annotation, so the processor never sees them. They go into the skill and user guide as anti-patterns in a `docs:` PR.

**Possibly BREAKING:** some builds treat KSP warnings as errors, and W001/W002 can fail those builds. No generated function signature changes.

## Capabilities

### New Capabilities
- `diagnostics/diagnostic-codes`: every processor diagnostic carries a stable code and a link to the errors reference page. Codes are never reused or renumbered.
- `diagnostics/redundant-mapping-warnings`: warnings for user-written converters that duplicate a mapping Kraft already generates, and how to suppress them.
- `diagnostics/type-mismatch-hints`: context-specific guidance attached to the type-mismatch error, starting with duplicated generated types.
- `annotations/argument-defaults`: how Kraft reads annotation arguments KSP omits, and what the missing-argument error tells the user.

### Modified Capabilities
<!-- none: openspec/specs/ is empty; 0.13 behaviour is not back-filled -->

## Impact

- **Code:**
  - `kraft-core`: `processor/util/LoggerExtensions.kt` (`err()` wrapper, all helpers), every inline `logger.error`/`logger.warn` site (67 today), `AnnotationExtensions.kt`, `ConfigObjectScanner.kt`, `GlobalConverterScanner.kt`.
  - `kraft-core`: a new diagnostic catalogue (`processor/diagnostics/`).
  - The converter-resolution path in `PropertyResolver` / `ConverterRule`, which needs a "would anything else claim this?" probe.
  - `kraft-ksp` warning sites in `AutoMapperProcessor.kt` and the generators.
- **Tests:** existing tests that assert on message text may need their expected strings updated because of the new code prefix. New compile-tests go in `kraft-ksp/src/jvmTest`.
- **Docs:** new `docs/user-guide/errors.md` plus its `mkdocs.yml` nav entry, a pointer from `ai-integration.md`, and an update to the common-errors table in `.claude/skills/kraft-mappers/SKILL.md` so it keys on codes.
- **Consumers:** any build that greps Kraft's message text, or treats KSP warnings as errors.
