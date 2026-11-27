# Proposal

## Why

Nothing in the processor tells you what it registered. Every "is my converter being seen?" investigation, especially across modules where converters are discovered from the klib classpath, ends with reading generated output or bisecting the build. The type-mismatch error (KRAFT-E005) says to register a converter, but not which converters Kraft already knows for the two types, so a user whose converter was not picked up cannot tell why. This change makes the processor report what it discovered. It also sets the reporting model that the unmapped-target policy (#34) will extend with a per-mapper coverage report.

Roadmap: #5 (closes). Advances #34 (sets the report format that its coverage report extends; #34 itself is not implemented here).

## What Changes

- **New KSP option `kraft.report`.** When it is `true`, the processor writes `kraft/report.json` to the KSP resources output of the compilation. The file is deterministic, sorted and pretty-printed, so two builds can be diffed. It contains:
  - the resolved converter table: every converter the processor can call, with its source and target types (including nullability), the function it calls, and its origin: `same-module` (a `@KraftConverter` in this compilation), `classpath-delegate` (a `@KraftConverterDelegate` published by an upstream Kraft module, with that module's id) or `synthetic` (an enum mapper Kraft generates in this run);
  - the enum pairs: each `@MapEnum`-declared or auto-derived enum mapping, with the generated function and how it came to exist (`declared` or `auto`);
  - the configured sides and the side aliases Kraft emitted (receiver, alias name, target, side, originating mapper);
  - a `schemaVersion`, so later sections (the per-mapper coverage report of #34) are additive.
- **`kraft { report = true }` in the Gradle plugin DSL**, emitted as the raw `kraft.report` KSP argument like the other DSL properties.
- **KRAFT-E005 lists the converters that touch the failing types.** The type-mismatch error gains a section listing every registered converter whose source matches the source property's type or whose target matches the target property's type (element and value types included for collections and maps), each with its function and origin. When there are none, it says so and points at `kraft.report`.
- Without `kraft.report`, generated code and output files are unchanged.
- **Not in this change:** the unmapped-target policy and per-mapper coverage (#34); a report for `@MapUsing` per-mapper converters; listing classpath delegates that no mapper asked for (they cannot be enumerated on klib classpaths, see design.md).

No generated function signature changes. Not breaking: the new E005 text is additive, and the report is opt-in.

## Capabilities

### New Capabilities
- `diagnostics/processor-report`: the opt-in report file of what the processor discovered: converter table with origins, enum pairs, sides and emitted side aliases, and its format and stability rules.

### Modified Capabilities
- `diagnostics/type-mismatch-hints`: the type-mismatch error additionally lists the registered converters whose source or target matches the failing property's types.

## Impact

- **Code:**
  - `kraft-core`: a converter-origin classification on `ConverterEntry`, a "known entries" view on `GlobalConverterRegistry` (eager entries plus classpath delegates resolved so far), `ClasspathConverterScanner` exposing its resolved delegates, `SideRegistry` remembering emitted aliases, the KRAFT-E005 message in `LoggerExtensions.kt` and its three call sites, and the `kraft.report` option constant.
  - `kraft-ksp`: a report model and JSON writer, collected per round in `AutoMapperProcessor` and written once at the end of processing; the two alias-recording sites pass target and side.
  - `kraft-gradle-plugin`: a `report` property on `KraftExtension`, emitted by `KraftKspArgEmitter`.
- **Tests:** new compile-tests in `kraft-ksp/src/jvmTest/.../diagnostics/`, a functional test in `kraft-gradle-plugin`.
- **Docs:** `docs/user-guide/errors.md` (E005), `docs/user-guide/ksp-options.md` (`kraft.report` section), a new `docs/user-guide/processor-report.md` page in the mkdocs nav, `docs/user-guide/ai-integration.md`, and the kraft-mappers skill.
- **Consumers:** none unless they opt in. A build with `kraft.report=true` gets one extra resource file in its KSP output (see design.md D2 on packaging).
