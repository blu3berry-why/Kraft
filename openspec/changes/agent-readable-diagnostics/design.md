# Design

## Context

See proposal.md (Why). Current state, as observed on `main` @ 59c0d89:

- **Diagnostics are spread out.** `processor/util/LoggerExtensions.kt` has about 20 formatted helpers built on a private `err()` that wraps the message in a "Kraft KSP Error" banner. About 45 more `logger.error`/`logger.warn` calls in scanners, rules and generators bypass it. There are 67 sites in total, none of which carries an identifier.
- **Annotation arguments.** `AnnotationExtensions.getStringArgOrNull` logs `missingAnnotationArgument` whenever an argument is absent from `KSAnnotation.arguments`, even when the annotation declares a default. The hint always prints `MyClass::class`. `ConfigObjectScanner.parseConverterAnnotation` already treats a null `source` as whole-source, but by then the error has been logged.
- **Resolution order.** `PropertyResolver.default()` runs `ConverterRule` (`@MapUsing`) first, so a `@MapUsing` always wins. Nothing checks whether a later rule would have claimed the property anyway.
- **Converter registry.** `GlobalConverterRegistry` keys on `ConverterTypeKey(sFq, sNullable, tFq, tNullable)`, so `S → T` and `S? → T?` are distinct entries and can be compared directly. Classpath converters (`ClasspathConverterScanner`) are merged in `AutoMapperProcessor`.
- **Type mismatches.** `detailedTypeMismatch` already switches to qualified names when simple names collide. `typeMismatchGuidance` chooses the guidance branch.
- **Docs.** mkdocs has `attr_list` enabled, so headings can carry explicit `{#kraft-e001}` anchors.

**Generated function signatures do not change.** No new `PropertyMappingStrategy` is introduced. W001 lands in the existing `PropertyMappingStrategy.ConverterFunction` path.

## Goals / Non-Goals

**Goals:**
- One catalogue of diagnostics, from which codes, the docs-completeness test and severity all derive.
- Warnings that print the exact code Kraft would have generated, so the reader can check equivalence without opening generated sources.

**Non-Goals:**
- Inspecting converter function bodies. KSP has no body AST, so W001 compares outcomes (would native resolution claim this property?), not code.
- A module-wide switch to disable warnings. Per-declaration `@Suppress` is enough until someone asks for more.
- Rewording diagnostics beyond adding the code and link, except the two messages the specs change.
- An MCP server. The errors page plus codes cover the lookup use case with no install step.

## Decisions

### D1. A `KraftDiagnostic` enum is the single catalogue
`kraft-core/.../processor/diagnostics/KraftDiagnostic.kt` holds `enum class KraftDiagnostic(val code: String, val severity: Severity, val title: String)`. A single `KSPLogger.report(diagnostic, message, symbol)` prepends `[KRAFT-X###] ` to the first line, appends `See: <site>/user-guide/errors/#<code lower-cased>`, and routes to `error` or `warn` by severity. The existing banner is kept. Every helper in `LoggerExtensions.kt` and every inline site migrates to `report`.

- *Alternative:* string constants at each call site. Rejected because that gives no enumerable set, so a docs-completeness test can't be written.
- *Numbering:* `W001` and `W002` are reserved for the two new warnings (they are already named in the specs). Existing errors are numbered `E001…` in the order of the migration task list. Existing warnings start at `W003`. The enum declaration order is frozen. New codes are appended and retired ones stay as `@Deprecated` entries.

### D2. Docs completeness is a test, not a convention
A `kraft-ksp` jvmTest reads `docs/user-guide/errors.md` (located from the Gradle `rootDir` system property) and asserts that every `KraftDiagnostic` has a heading ending in `{#kraft-x###}`. A second assertion makes sure no anchor on the page lacks a matching enum entry (unless it is marked retired). This test is what enforces the "documents every code" requirement.

### D3. W001: run the rest of the resolver with a muted logger after `ConverterRule` wins
`PropertyResolver.resolve` already returns the first non-null strategy. When that strategy is a property-source `ConverterFunction`, the resolver runs the remaining rules in order, without `ConverterRule` and `RequiredFieldErrorRule`, against the same `MappingContext` and a `MutedKspLogger` that discards output. If any rule returns non-null, that strategy is stored on the result as `ConverterFunction.shadowedNative: PropertyMappingStrategy?`. The new field defaults to null and only `kraft-core` and `kraft-ksp` use it. It is not public API.

`ExtensionMapperGenerator` renders strategies to expressions, so it emits W001 by rendering `shadowedNative` with the same code path it uses for real assignments. The printed expression is therefore exactly what Kraft would generate.

- *Alternative:* a shape heuristic on the converter's parameter and return types (`List<A>? → List<B>` where `A → B` is registered). Rejected: every native strategy would need its own copy of that heuristic, and the copies would drift from the real rules.
- *Alternative:* emit in `kraft-core` without the expression. Rejected because the expression is the most useful part for an agent.
- **Rule side effects:** the second pass must not register nested descriptors or other state twice. Before relying on it, the task audits `NestedRule`, `GlobalConverterRule` and `ValueClassRule` for mutations of `MappingContext`. If any rule mutates state, the probe runs on a context copy.

### D4. W002 is checked once, after the converter registries are merged
In `AutoMapperProcessor`, after same-module and classpath converters are merged, Kraft iterates over registry entries whose key is `(S, true, T, true)`. Each one whose `(S, false, T, false)` twin exists, and whose declaration is in the current module, gets W002. An upstream nullable converter can't carry a suppression the user controls, so it is never reported.

### D5. Suppression reads `kotlin.Suppress` from the KSP symbol
`@Suppress` has SOURCE retention, but KSP sees source annotations on same-module declarations, which is the only place W001 and W002 fire. A helper `KSAnnotated.isKraftSuppressed(diagnostic)` checks for the annotation's `names` containing the code. Matching is exact and case-sensitive. A single test confirms the annotation is visible in both KSP1 and KSP2 test runners. *Alternative:* a Kraft-specific `@KraftSuppress`. Rejected because it adds annotation API surface for something Kotlin already has.

### D6. The duplicated-types hint is one more branch in `typeMismatchGuidance`
Kraft resolves both property types to `KSClassDeclaration`s. The hint fires when all of the following hold:
- the simple names are equal,
- the qualified names differ,
- at least one declaration is nested (`parentDeclaration != null`),
- the shapes match: equal enum entry name sets for `ClassKind.ENUM_CLASS`, or equal primary-constructor parameter name lists otherwise.

The hint text is prepended to whichever "How to fix" branch would already be chosen, so the nullability guidance still appears. The fix it names depends on the kind: `@MapEnum` for an enum, `@KraftConverter` / `@MapConfig` for a class.

### D7. Optional arguments carry an explicit default at the read site
`getStringArgOrNull` / `getEnumArgOrNull` gain a sibling, `getStringArgOrDefault(name, default)`, which never logs when the argument is absent. Default values live as constants in `KraftKspConstants`, next to the argument names (`DEFAULT_MAP_USING_SOURCE = ""`, `DEFAULT_DIRECTION = "AUTO"`, …). A test instantiates each annotation in Kotlin (`MapUsing(target = "x").source`) and asserts it equals the constant, so the constants can't drift from `kraft-annotations`.

`missingAnnotationArgument` gains a `kind` parameter (`CLASS | STRING | ENUM`) that selects the example value.

- *Alternative:* read `KSValueParameter.hasDefault` on the annotation class and its default value. Rejected because KSP doesn't expose default *values* for klib-sourced declarations, and that is exactly the failing case.

## Risks / Trade-offs

- [Builds that turn KSP warnings into errors break on new W001/W002 hits] → Release notes call it out and the errors page documents `@Suppress`. Generated code is unchanged, so the warning itself is the only new failure.
- [W001 false positives when a converter deliberately differs, such as a list helper that filters] → The warning text explicitly says "if its body intentionally differs" and gives the suppression. Kraft cannot read bodies (non-goal).
- [The second resolver pass costs time on every `@MapUsing` property] → It only runs for property-source converters, which are rare per mapper. It does no I/O and only rule evaluation.
- [Tests that assert exact message text break because of the code prefix] → The migration task updates expectations in bulk. New assertions use `contains` on the code plus the key phrase, not full-message equality.
- [The B1 reproduction depends on KMP metadata compilation, which the jvmTest runner doesn't exercise] → A unit test covers the helper with a synthetic `KSAnnotation` missing the argument, plus an `integration-tests` KMP case with `@MapUsing(target = …)` in `commonMain`.

## Migration Plan

Additive. It ships in a `feat:` minor release, and the release notes list the code prefix and the two new warnings. Rollback is a revert. Generated code and annotations don't change, so consumers need no migration.

## Pre-cleared deviations

- **3.3: missing-required-argument scenarios are tested with a synthetic `KSAnnotation`, not a compile test.** The Kotlin frontend rejects an omitted required argument before Kraft's processor sees it, so `TestKspRunner` cannot reach that path. Approved by the controller during apply (group 3).
- **`getEnumArgOrNull` still returns null without logging when absent.** No Kraft enum argument is required, so `ArgumentKind.ENUM` is only exercised through the logger. Approved during apply (group 3).
- **`SuppressVisibilityTest` covers KSP2 only (D5 says "both runners").** The KSP version in use (2.3.3) has no KSP1. Approved during apply (group 1).
- **W001 probe filters (group 4).** The probe has three filters. (a) A native strategy that reads a different source property than the converter is not a shadow. (b) An `Ignored` result counts as "no native mapping". (c) A `NestedMapper` shadow whose mapper was never built is dropped.
- **W001 skips exact global-converter overrides (user decision, 2026-10-14).** When the shadow would apply a `@KraftConverter` directly to the same scalar type pair, without element-wise mapping or a null lift, the `@MapUsing` is a deliberate per-mapper override and is not reported. Element-wise and null-lifted uses are still reported.
