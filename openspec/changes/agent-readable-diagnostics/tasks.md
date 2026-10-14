# Tasks

> Execution: dispatch — 8 groups, ~30 hand-written files, mostly pattern-following migration

> Branch: `feat/agent-readable-diagnostics` off `main`. Groups 1→2 are sequential. Groups 3, 4, 5 and 6 depend on group 1 only, but they share the `kraft-core`/`kraft-ksp` build root, so run them one at a time. `kraft-core` has no test source set: every test, including the unit-level ones, lives in `kraft-ksp/src/jvmTest`. Test command for each group: `./gradlew :kraft-ksp:jvmTest --tests '<pattern>'`.

## 1. Diagnostic catalogue and reporting path

> Agent: opus · effort high · scope: kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/diagnostics/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md, mkdocs.yml · verify: ./gradlew :kraft-ksp:jvmTest · after: none

- [x] 1.1 Add `kraft-core/.../processor/diagnostics/KraftDiagnostic.kt` (enum: `code`, `severity`, `title`), a `Severity` enum, and `KSPLogger.report(diagnostic, message, symbol)`, which prepends `[KRAFT-X###] `, keeps the existing banner, and appends `See: https://blu3berry-why.github.io/Kraft/user-guide/errors/#<code lower-cased>`. Reserve `W001` and `W002`. Verify with a jvmTest unit test that a fake logger receives the prefixed first line and the link as the last line.
- [x] 1.2 Add `MutedKspLogger` (discards all output) and `KSAnnotated.isKraftSuppressed(diagnostic)` (reads `kotlin.Suppress.names`, exact match). Verify with a jvmTest `SuppressVisibilityTest`: a `@Suppress("KRAFT-W001")` function in test source is reported as suppressed under the KSP runner, and `@Suppress("KRAFT-W002")` is not.
- [x] 1.3 Create `docs/user-guide/errors.md` with its intro (how to read a code, how to suppress a warning) and add it to the `mkdocs.yml` nav under User Guide. Verify that `mkdocs build --strict` passes, or if mkdocs isn't installed locally, that the nav entry resolves to an existing file.
- [x] 1.4 Add jvmTest `ErrorsReferenceCompletenessTest`. It reads `docs/user-guide/errors.md` from the root dir and fails, naming the offending code, when a `KraftDiagnostic` has no `{#kraft-x###}` heading or the page has an anchor with no enum entry. Verify it passes with only W001/W002 catalogued and documented as stubs, and fails when a stub heading is deleted.

## 2. Migrate every existing diagnostic to a code

> Agent: sonnet · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 1

- [x] 2.1 Migrate each helper in `processor/util/LoggerExtensions.kt` to `report(...)`, appending one `KraftDiagnostic` entry per helper (E001…, in file order), and write its `errors.md` entry (cause, minimal annotated input, fix). Verify that `ErrorsReferenceCompletenessTest` is green and the existing `kraft-ksp:jvmTest` suite passes after expectations are updated to `contains("[KRAFT-E…]")` plus the existing key phrase.
- [x] 2.2 Migrate the inline `logger.error` sites in `scanner/*`, `descriptor/*` and `propertyresolver/rules/*` the same way (continuing the E numbering, one code per distinct failure condition, never per call site) and document each one. Verify with `grep -rn 'logger\.error(\|\.error(' kraft-core/src/main` showing only `report`'s own implementation, plus a green completeness test.
- [x] 2.3 Migrate the `kraft-ksp` sites (`AutoMapperProcessor`, generators) and the existing warnings (`IgnoredPropertyAggregator`, `NestedRule`, `AutoMapperProcessor`, `ExtensionMapperGenerator`) starting at W003, and document each one. Verify with the same grep over `kraft-ksp/src/main` and `kraft-core/src/main` for `\.warn(`, plus a green completeness test.
- [x] 2.4 Write jvmTest `DiagnosticCodesTest` covering the `diagnostics/diagnostic-codes` scenarios: required-property error carries an E code; error ends with its `See:` link; the same input produces the same code (asserting the literal code, as a regression pin); nullable `String? → String` still contains `only nullability differs` and the `@MapUsing(target = ...)` snippet. Verify that the tests pass.

## 3. Annotation argument defaults (B1)

> Agent: sonnet · effort medium · scope: kraft-core/src/main/**, kraft-ksp/src/jvmTest/**, integration-tests/kmp-consumer/**, docs/user-guide/errors.md, docs/user-guide/custom-converters.md, .claude/skills/kraft-mappers/SKILL.md · verify: ./gradlew :kraft-ksp:jvmTest :integration-tests:kmp-consumer:jvmTest · after: 2

- [ ] 3.1 Add the `DEFAULT_*` constants to `KraftKspConstants` and `getStringArgOrDefault` / `getEnumArgOrDefault`. Switch `ConfigObjectScanner.parseConverterAnnotation`'s `source` and `direction` reads, and every other read of a defaulted argument in `kraft-core`, to the `OrDefault` variants. Verify with a jvmTest unit test that a synthetic `KSAnnotation` missing `source` yields `""` and logs nothing.
- [ ] 3.2 Add the test that pins constants to annotations: instantiate `MapUsing(target = "x")`, `MapConfig(...)` etc. and assert that each defaulted property equals its `DEFAULT_*` constant. Verify that it fails when a constant is edited.
- [ ] 3.3 Give `missingAnnotationArgument` a `kind` (`CLASS | STRING | ENUM`) that selects the example value, and pass it at every call site. Verify with jvmTest `AnnotationArgumentDefaultsTest`, covering both spec scenarios for missing `String` / `KClass` (no `::class` in the String case) and the omitted-`direction` → AUTO scenario.
- [ ] 3.4 Add a KMP case to `integration-tests`: a `commonMain` `@MapConfig` with whole-source `@MapUsing(target = "inputs")` and no `source`. Verify that it compiles under the KMP integration task, and that before the fix it reproduced `Missing required argument 'source'`. If it didn't reproduce, record that in the task and keep the case as a regression guard.
- [ ] 3.5 Update `errors.md` for the missing-argument code, and remove any `source = ""` workaround advice from `docs/user-guide/custom-converters.md` and `.claude/skills/kraft-mappers/SKILL.md` (check with grep). Verify that grepping both files for `source = ""` returns nothing outside an explanation of the fix.

## 4. W001: redundant property-source `@MapUsing` (C1)

> Agent: opus · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md, docs/user-guide/collection-mapping.md, docs/user-guide/custom-converters.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 3

- [ ] 4.1 Audit `NestedRule`, `GlobalConverterRule` and `ValueClassRule` for mutations of `MappingContext` or shared state during `tryResolve`, and record the finding as a code comment on the shadow probe. If any rule mutates, make the probe run on a context copy. Verify that the audit note exists and the existing jvmTest suite passes unchanged.
- [ ] 4.2 Add `shadowedNative: PropertyMappingStrategy? = null` to `PropertyMappingStrategy.ConverterFunction`. In `PropertyResolver.resolve`, when a property-source `ConverterFunction` wins, run the remaining rules (excluding `ConverterRule` and `RequiredFieldErrorRule`) with `MutedKspLogger` and store the first non-null strategy. Verify with a jvmTest unit test that a direct-match property claimed by `@MapUsing` has `shadowedNative` of type `Direct`.
- [ ] 4.3 In `ExtensionMapperGenerator`, when `shadowedNative != null` and the converter function isn't suppressed, emit W001 with the rendered native assignment, using the same rendering path as real assignments. Verify with jvmTest `RedundantMapUsingWarningTest` covering all four requirement scenarios: list helper (message text and `rows = this.rows?.map { it.toRow() } ?: emptyList()`), identity converter on a direct match, real type gap (no warning), and whole-source (no warning).
- [ ] 4.4 Add the suppression and byte-identity scenarios to `RedundantMapUsingWarningTest`: `@Suppress("KRAFT-W001")` silences the warning, `@Suppress("KRAFT-W002")` does not, and generated files are identical with and without suppression. Verify that the tests pass.
- [ ] 4.5 Write the full W001 entry in `errors.md`, and add a "Kraft warns when a converter duplicates native mapping" note to `docs/user-guide/collection-mapping.md` and `custom-converters.md`. Verify with the completeness test plus a manual read that the doc examples match the test inputs.

## 5. W002: nullable-twin `@KraftConverter` (C2)

> Agent: sonnet · effort medium · scope: kraft-ksp/src/main/**, kraft-core/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md, docs/user-guide/custom-converters.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 4

- [ ] 5.1 After the same-module and classpath converter registries are merged in `AutoMapperProcessor`, emit W002 for each same-module `(S?, T?)` entry whose `(S, T)` twin exists, unless it is suppressed. Verify with jvmTest `NullableTwinConverterWarningTest` covering both scenarios: the twin is warned with the exact first line and fix text; a nullable-only converter is not warned.
- [ ] 5.2 Add cross-module and suppression cases: the non-null converter upstream with the nullable twin local produces a warning; the nullable twin upstream with the non-null converter local produces none; `@Suppress("KRAFT-W002")` silences it. Verify with `compileWithUpstream`-based tests that pass.
- [ ] 5.3 Write the W002 entry in `errors.md` and update the nullable-scalar section of `docs/user-guide/custom-converters.md`. Verify with the completeness test.

## 6. Duplicated-types hint on type mismatch (C5)

> Agent: sonnet · effort medium · scope: kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/util/LoggerExtensions.kt, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 5

- [ ] 6.1 Add the duplicated-types branch to `typeMismatchGuidance`: equal simple names, different qualified names, at least one nested, same enum entry names or the same constructor parameter names. Prepend the hint to whichever fix branch would already be chosen, with `@MapEnum` advice for enums and `@KraftConverter`/`@MapConfig` advice for classes. Verify with jvmTest `DuplicatedGeneratedTypeHintTest` covering all four spec scenarios: kmpgen enum, different shape (no hint), duplicated data class, and nullable source keeping its nullability guidance.
- [ ] 6.2 Document the hint under the type-mismatch code in `errors.md`, cross-linking `docs/user-guide/api-first-client-design.md`. Verify with the completeness test plus a link check (`mkdocs build --strict` when available).

## 7. Agent-facing docs

> Agent: haiku · effort low · scope: .claude/skills/kraft-mappers/SKILL.md, docs/user-guide/ai-integration.md · verify: every KRAFT- code in SKILL.md is present in kraft-core/src/main/kotlin/com/blu3berry/kraft/processor/diagnostics/KraftDiagnostic.kt (grep) · after: 6

- [ ] 7.1 Re-key the common-errors table in `.claude/skills/kraft-mappers/SKILL.md` on `KRAFT-…` codes, linking each one to `errors.md`. Add W001/W002 as "delete your helper" rows, and add an anti-patterns subsection for C3 (hand-written enum `when` → `@MapEnum`), C4 (call-site imports of nested mappers are unnecessary) and C6 (keep converter bodies KMP-pure). Verify that every code in the table exists in `KraftDiagnostic` (grep each one).
- [ ] 7.2 Update `docs/user-guide/ai-integration.md` with a "When a build fails" section: grep the `KRAFT-` code and open its `errors.md` entry. Verify that the page renders in the mkdocs nav.

## 8. Full gate

> Agent: inline (controller full gate)

- [ ] 8.1 Run `./gradlew detekt :kraft-ksp:jvmTest :kraft-gradle-plugin:test` and the KMP integration tests (`./gradlew -p integration-tests build` or the CI integration task). Verify that all pass, recording test counts in the PR body.

## Workflow follow-up

- [USER-GATE / controller-handled] Review and merge the PR, then archive with `/opsx:archive agent-readable-diagnostics`.
- Release notes: call out the `[KRAFT-…]` message prefix and the two new warnings (relevant to builds that treat KSP warnings as errors).
