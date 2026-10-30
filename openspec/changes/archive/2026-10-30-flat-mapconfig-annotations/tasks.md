# Tasks

When this file and the dispatch brief conflict, this file and `design.md` win.

## Pre-cleared deviations

- `@MapConstant` is a **member** annotation (on a property or a zero-parameter function of
  the `@MapConfig` object), not the class-level annotation with a string value that
  ROADMAP #29 / #40 sketch. Decided with the owner on 2026-10-18, see design D4. Reviewers:
  do not flag this.
- Duplicate targets *within one form* stay last-wins, as today (design Non-Goals).

## 1. Annotations

> Agent: sonnet · effort low · scope: kraft-annotations/src/commonMain/** · verify: ./gradlew :kraft-annotations:compileKotlinMetadata :kraft-annotations:compileKotlinJvm :kraft-annotations:compileKotlinJs :kraft-ksp:jvmTest --tests "*configignore*" · after: —

- [x] 1.1 Add `@Target(AnnotationTarget.CLASS)`, `@Retention(AnnotationRetention.SOURCE)` and `@Repeatable` to `FieldMapping` and `MapIgnoreField` (design D1). Update their KDoc to show both forms. Verify that the compile tasks above succeed and the existing `configignore` tests stay green.
- [x] 1.2 Add `config/MapConstant.kt` exactly as in design D4, with KDoc that covers member placement, the target default, `val`-once vs `fun`-per-call, and AUTO direction. Verify that `:kraft-annotations` compiles for all targets.

## 2. Flat rules in the scanner

> Agent: sonnet · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md, docs/user-guide/configuration-objects.md, docs/user-guide/field-renaming.md, docs/user-guide/ignore-rules.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 1

- [x] 2.1 Split `extractFieldOverrides` / `extractIgnoredMappings` into an origin-tagged collector (array entries plus `classDeclaration.annotations` filtered by FQ name) and the existing per-annotation parser (design D2). Verify with a new jvmTest `flatconfig/FlatFieldMappingTest` covering spec scenarios "Two flat renames" and "Flat rename on a reverse mapper".
- [x] 2.2 Verify flat ignores with a new jvmTest `flatconfig/FlatMapIgnoreFieldTest` covering "Flat ignore with the default direction" and "Flat ignore with an explicit direction". The test must exercise a missing `direction` argument, which is absent from `arguments` (design Context).
- [x] 2.3 Verify array compatibility with a new jvmTest `flatconfig/ArrayFormCompatibilityTest` covering "Array form unchanged" (compare the generated text with a fixture captured from `origin/main` before 2.1) and "Mixed forms on different targets".
- [x] 2.4 Add `KraftDiagnostic` E066 and E071 with their `LoggerExtensions` messages (exact first lines from the spec), raised as in design D3. Verify with a new jvmTest `flatconfig/FlatRuleErrorsTest` covering "Same rename in both forms", "Same ignore in both forms" and "Flat rename on a @MapFrom class". Also assert that an array-only `FieldMapping` on a `@MapConfig` object does **not** trigger E071, which confirms `getSymbolsWithAnnotation` skips argument annotations.
- [x] 2.5 Add E066 and E071 entries to `docs/user-guide/errors.md`. Verify with a green `ErrorsReferenceCompletenessTest`.
- [x] 2.6 Lead with the flat form in `configuration-objects.md`, `field-renaming.md` and `ignore-rules.md`, keep the array form as "also supported", and recommend one form per object. Verify that every new snippet matches a jvmTest input from 2.1–2.3.

## 3. @MapConstant

> Agent: opus · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md, docs/user-guide/constant-values.md, mkdocs.yml · verify: ./gradlew :kraft-ksp:jvmTest · after: 2

- [x] 3.1 Add `ConstantDescriptor`, `ConfigObjectScanResult.constants`, `MappingContext.constants` (filtered per direction in `ConfigDescriptorBuilder` and `ReverseDescriptorBuilder`), `PropertyMappingStrategy.ConstantMember` with its `nestedDescriptors()` branch, the `CtorCallBuilder` emission (`%T.%N` / `%T.%N()`), and `ConstantRule` first in `PropertyResolver.default()` (design D5–D7). Verify with a new jvmTest `constant/ConstantPropertyTest` covering "Literal property, target from the member name" (the generated file compiles in the `generated` package), "const val", "Constant over a same-named source property" (no warnings) and "Overridden target on a property".
- [x] 3.2 Verify function constants with a new jvmTest `constant/ConstantFunctionTest` covering "Function with an explicit target": load the compiled class, call `toUser()` twice, and assert `0` then `1`.
- [x] 3.3 Add E067 (descriptor builders) and E068 (`ConstantRule`). Verify with a new jvmTest `constant/ConstantTypeAndTargetTest` covering "Misspelled target", "Wrong type", "Nullable member into a non-null target" and "Non-null member into a nullable target" (assert no `KRAFT-W009`).
- [x] 3.4 Add `ConstantMemberScanner` validation and orphan detection with E069 (design D5). Verify with a new jvmTest `constant/ConstantMemberRulesTest` covering "Private member", "Function with parameters", "Extension function" and "Not inside a @MapConfig object", each asserting exactly one error.
- [x] 3.5 Add direction resolution and the E070 conflict check in `ConfigObjectScanner` (design D3, D4). Verify with new jvmTests `constant/ConstantConflictTest` ("Constant and rename on one target", "Constant and converter on one target", "Two constants on one target", "Constant and ignore on one target") and `constant/ConstantDirectionTest` ("AUTO picks forward", "AUTO falls back to reverse", "Explicit REVERSE").
- [x] 3.6 Add a fix line `✓ Or set it from the mapper object: @MapConstant val <name> = ...` to the KRAFT-E004 "How to fix" list, after the config-override line, and to KRAFT-W009's list (ROADMAP #29 "When it ships"), in `LoggerExtensions.kt` (E004 around line 128, W009 around line 177) and in the matching `errors.md` entries. Show the E004 config-override line in its flat form, `@FieldMapping(source = "sourceName", target = "<name>")`. Verify with assertions in `RequiredFieldErrorRuleTest` and `missingsource/NullableWithoutDefaultTest`.
- [x] 3.7 Add E067–E070 entries to `errors.md` and a new `docs/user-guide/constant-values.md`: placement, target default, `val`-once vs `fun`-per-call with the `Clock.System.now()` pitfall, direction, and when to use `@MapUsing` instead. Add it to the `mkdocs.yml` nav after Ignore Rules. Verify with a green `ErrorsReferenceCompletenessTest`, and check that the snippets match the 3.1–3.5 test inputs.

## 4. KMP integration tests

> Agent: sonnet · effort medium · scope: integration-tests/kmp-consumer/** · verify: ./gradlew :integration-tests:kmp-consumer:jvmTest :integration-tests:kmp-consumer:compileTestKotlinJs · after: 3

- [x] 4.1 Add `FlatConfigModels.kt` (commonMain): one `@MapConfig` object with a flat `@FieldMapping`, a flat `@MapIgnoreField` and an untyped `@MapConstant val currency = "HUF"`. Add `FlatConfigTest.kt` (commonTest) asserting the rename, the ignored default and `"HUF"`. Verify that the spec scenarios "Flat rules in commonMain" and "Inferred-type constant in commonMain" pass on JVM and compile for JS. These two scenarios are tested here, not in `kraft-ksp/src/jvmTest`, because the KMP metadata compilation only runs in the integration build. Use `compileTestKotlinJs`, not `jsTest`: `jsTest` currently fails at `:kotlinStoreYarnLock` ("Lock file was changed"), as seen in the 2026-10-18 spike, unrelated to this change.

## 5. Docs sweep

> Agent: sonnet · effort medium · scope: docs/**, README.md, .claude/skills/kraft-mappers/SKILL.md, CHANGELOG.md (only if hand-edited; release-please owns it otherwise) · verify: grep re-run listed in 5.3 · after: 4

- [x] 5.1 Update `.claude/skills/kraft-mappers/SKILL.md`: add `@MapConstant` and the flat form to the decision tree, change the E004 table row from "whole-source `@MapUsing` for constant injection" to `@MapConstant`, add the E066–E071 rows, and make the flat form the default in examples. Verify that the wording matches the spec requirements.
- [x] 5.2 Update the `README.md` quick example to the flat form plus one `@MapConstant`. Verify that it matches a 2.1/3.1 test input.
- [x] 5.3 Sweep `docs/ROADMAP.md`, `docs/user-guide/`, `README.md` and `.claude/skills/kraft-mappers/SKILL.md` for `#29`, `#40`, `fieldMappings = [`, `ignoredMappings = [`, `constant`, `whole-source`, `not supported` and `nested arrays`. Update each stale hit. In `docs/ROADMAP.md`, set the headings of #29 and #40 to `done in 0.15 (flat-mapconfig-annotations)`, rewrite #29's body to describe the member-annotation design that actually shipped, and update the Tier 1 note and Suggested order "Features" lines that mention #29/#40. Verify by re-running the grep and listing the remaining hits, each with a reason it is correct.

## 6. Full gate

> Agent: sonnet · effort medium · scope: none (report only; fix only if a failure is in this change's files) · verify: the commands below · after: 5

- [x] 6.1 Run `./gradlew detekt :kraft-ksp:jvmTest :kraft-gradle-plugin:test` plus the KMP integration tests: `:integration-tests:kmp-consumer:jvmTest`, `:integration-tests:kmp-consumer:compileTestKotlinJs`, and the `kmp-consumer-ambiguous` fixture, which must still fail with exactly KRAFT-E030. All must be green. Verify by recording the test counts in this task. `jsTest` is excluded for the yarn-lock reason in 4.1. If it has been fixed on main by then, run it too. (gate 2026-10-18: detekt ✓, kraft-ksp 460/0, plugin 14/0, kmp-consumer jvm 8/0 + compileTestKotlinJs ✓, ambiguity fixture fails with exactly KRAFT-E030 ✓, mkdocs build --strict ✓; jsTest not run — yarn lock)

## Workflow follow-up

- Archive the change with `/opsx:archive` after the PR is merged.
