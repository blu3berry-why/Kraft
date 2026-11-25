# Design

## Context

See proposal.md (Why). Current state on `main` @ da9664c:

- **Converter registry.** `AutoMapperProcessor.process` builds one `GlobalConverterRegistry` per round. Its eager `entries` hold same-module `@KraftConverter` functions (`ConverterEntry.Real`, source origin) merged with `@MapEnum`-derived enum mappers (`ConverterEntry.Synthetic`). Classpath delegates are not enumerated: `ClasspathConverterScanner.lookupByName` builds the delegate's deterministic FQN from a type pair and resolves it by name, memoising hits and misses in `byNameCache`. Package enumeration returns nothing for klib dependencies, so this is the only way to find them.
- **Origin is derivable.** `Synthetic` means "generated in this run". A `Real` entry whose function has `Origin.KOTLIN_LIB`/`JAVA_LIB` is a classpath delegate (the scanner only accepts those), otherwise it is same-module. The delegate's `@KraftConverterDelegate(moduleId = …)` names the producing module.
- **Enum pairs.** `declaredEnumMappings` (from `EnumMapScanner`) and the `AutoEnumMappingDeriver` result are concatenated in `process`. Both lists are available there separately. Their generated coordinates come from `enumMappingsToConverterEntries`.
- **Sides.** `SideRegistry` holds the parsed `SideConfig`s. `recordAlias(receiver, alias, mapperOrigin)` is called by `ExtensionMapperGenerator` and `EnumMapperGenerator` for each alias they emit, and only for those.
- **KRAFT-E005.** `detailedTypeMismatch` in `LoggerExtensions.kt` is called from `DirectMatchRule`, `ClassOverrideRule` and `ConfigOverrideRule`, each holding a `MappingContext` whose `globalConverters` is the merged registry. `GlobalConverterRule` runs before all three, so an E005 always means no registered converter fits the exact pair.
- **KSP output.** `CodeGenerator.createNewFileByPath(deps, path, extensionName)` routes any extension other than `kt`/`java`/`class` to the compilation's `resources` output directory (e.g. `build/generated/ksp/<target>/<sourceSet>/resources/`).

**Generated function signatures do not change.** No `PropertyMappingStrategy` is added or changed. The E005 change sits in the error path of `PropertyMappingStrategy.Direct`/`Renamed` resolution (the rules that would have produced them emit the error).

## Goals / Non-Goals

**Goals:**
- One report model that #34 can extend with a `mappers` section (per-mapper property coverage) without changing the existing keys.
- The report and the E005 listing use the same origin classification, so both say the same thing about a converter.

**Non-Goals:**
- Enumerating classpath delegates nobody asked for. That would need package enumeration, which fails on klib classpaths, the case this item is about.
- Per-mapper `@MapUsing` converters, constants and coverage. That is #34's coverage report.
- A human-readable text rendering. JSON pretty-printed one key per line is diffable and greppable. A text view can be generated from it later.

## Decisions

### D1. JSON, schema-versioned, sorted, hand-written
The report is a JSON object: `schemaVersion`, `moduleId`, then `converters`, `enumPairs`, `sides`, `sideAliases`. Each array is sorted by a stable key (converters: source, target, function; enum pairs: source, target; sides: slot; aliases: receiver, alias). Objects are written with a fixed key order, two-space indentation and one key per line, with `\n` line endings, so a diff between two builds shows exactly the entries that changed.

- **Extensibility:** sections are added as new top-level keys. Consumers must ignore unknown keys. `schemaVersion` only goes up when an existing key changes meaning or shape. #34 adds `"mappers": [{"mapper", "source", "target", "properties": [{"target", "strategy", ...}]}]` under schema 1.
- **No JSON dependency:** a ~60-line writer in `kraft-ksp` (strings escaped per RFC 8259) avoids adding a library to the processor's runtime classpath.
- *Alternative:* plain text table. Rejected: harder for agents and scripts to consume, and harder to extend with nested per-mapper data without inventing a format.
- *Alternative:* kotlinx.serialization. Rejected: a new runtime dependency and plugin for one file.

### D2. Location: `kraft/report.json` in the KSP resources output, through `CodeGenerator`
Written with `createNewFileByPath(Dependencies.ALL_FILES, "kraft/report", "json")`, so it lands in `build/generated/ksp/<target>/<sourceSet>/resources/kraft/report.json`, one per compilation (KMP gets one per target and source set, which is correct because each has its own classpath).

- Going through `CodeGenerator` means KSP owns the file. It is cleaned on rebuild, restored from the build cache with the task's other outputs, and never left stale when the option is turned off. `ALL_FILES` marks it aggregating, so any source change re-runs it.
- **Trade-off:** KSP adds its resources directory to the compilation's resources, so the report is packaged into the jar/klib/AAR of a build that has the option on. The option is off by default, and the docs recommend enabling it from a Gradle property for debugging runs (`report = providers.gradleProperty("kraft.report").isPresent`). Recorded as an open question.
- *Alternative:* `kraft.report=<path>` written with `java.io`. Rejected: the file is invisible to Gradle (no caching or cleaning, so a cached KSP task silently skips writing it), and every KMP target's KSP task would overwrite the same path.

### D3. Collected per round, written once at the end
KSP may call `process` more than once, and a second `createNewFileByPath` for the same path throws. A `KraftReportCollector` held by `AutoMapperProcessor` takes each round's registry entries, resolved classpath delegates, enum pairs and recorded aliases, keyed so a repeat is idempotent. It writes the file in `finish()`, and in `onError()` too, so the report exists precisely when the user is debugging a failing build. When `kraft.report` is not `true`, the collector is never created and no code path changes.

### D4. Converter origin is a property of `ConverterEntry`
`ConverterEntry.origin: ConverterOrigin` (`SAME_MODULE`, `CLASSPATH_DELEGATE`, `SYNTHETIC`, each with its report label) lives in `kraft-core`, derived as described in Context, so the report and E005 cannot disagree. `ConverterEntry.callFqName` gives the function name the generated code calls. For a classpath delegate this is the generated trampoline in `com.blu3berry.kraft.generated.registry`, which is the honest answer ("this is what Kraft resolved"). The `module` field says where it came from.

### D5. `GlobalConverterRegistry.knownEntries()` = eager entries + resolved delegates
`ClasspathConverterScanner.resolvedEntries()` returns the non-null cache entries. The registry gains a `resolvedClasspathEntries` supplier (default empty, carried through `silent()`), and `knownEntries()` merges it under the eager entries (eager wins, as in `lookup`). Both the report and E005 read this view. E005 therefore lists the delegates resolved *so far* in the round. The docs say so, and point at `kraft.report` for the end-of-round table.

### D6. E005 matching: leaf types, nullability ignored
From each property type, collect the qualified names of the type and, recursively, its type arguments, so `List<Money>` yields `kotlin.collections.List` and `app.Money`. A known converter is listed when its source FQ is in the source set or its target FQ is in the target set. Generic converters are rejected at registration, so container names never match by accident. Lines are sorted like the report, capped at 10 with `… and N more`. The section goes between "Types must match exactly…" and the existing hints and "How to fix", so current guidance is not displaced. The "none" text names only the property's own top-level types (`kotlin.Int`, `kotlin.String`). For containers it names the element types, which is what a converter would target.

`detailedTypeMismatch` gains a `converters: GlobalConverterRegistry` parameter (default `EMPTY`), passed by the three rules.

### D7. `SideRegistry` remembers emitted aliases
`recordAlias` gains optional `targetFqn` and `sideName` parameters (defaults keep the collision unit tests unchanged), and `emittedAliases()` returns the records. Both generators already call `recordAlias` only after deciding to emit, so the list is exactly what was emitted.

### D8. Gradle DSL: `report: Property<Boolean>`
`kraft { report = true }` emits `kraft.report=true` through the existing `emitArg`, so the raw-vs-DSL conflict warning applies. Unset emits nothing, so the processor default (off) stands, as with `functionNameFormat`.

## Risks / Trade-offs

- [Report packaged into published artifacts when left on] → Off by default. The docs show the Gradle-property toggle. Open question below.
- [E005 lists only delegates resolved so far, which can confuse a cross-module user] → The text says "registered", and the docs explain resolution order and send them to `kraft.report`, which is written at the end of the round.
- [Tests asserting E005 text] → The new section is additive and existing tests use `contains`. The full suite is the check.
- [`onError` writing is not guaranteed to survive in every KSP host] → A compile-test pins the failing-build scenario. If a KSP version drops it, the test fails rather than the feature silently breaking.

## Migration Plan

Additive, shipped in a `feat:` minor release (0.15). No consumer action is needed. Rollback is a revert.

## Deviations during apply

- **Spec scenarios corrected to observed coordinates (group 3).** Enum mappers are generated in `<package>.generated`, so the synthetic and enum-pair scenarios expect `app.generated.toStatus`, not `app.toStatus`. A classpath delegate records the producing module's sanitised id (`_core` for `:core`, or a content hash when unset), so the report's `module` is that value. Both were spec mistakes. The implementation reports what the processor actually calls and reads.
- **`process` was split (group 6).** Adding the report call pushed `AutoMapperProcessor.process` over detekt's 60-line limit. The deferred-symbol query and the per-round report collection moved into private helpers. Behaviour is unchanged.

## Open Questions

- Should the report be kept out of packaged artifacts, for example by the Gradle plugin excluding `kraft/report.json` from the resources of a compilation, or by moving it to a KSP-unmanaged path once KSP offers a non-packaged output kind? It does not change the format or the option, so it can be decided after this lands.
