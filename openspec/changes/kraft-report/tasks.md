# Tasks

> Branch: `claude/kraft-report` off `main`. Test-first: every group writes its failing compile-tests before the code. `kraft-core` has no test source set, so all tests live in `kraft-ksp/src/jvmTest`. Per-group command: `./gradlew :kraft-ksp:jvmTest --tests '<pattern>'`.

## 1. Converter origin and known-entries view

- [ ] 1.1 Add `ConverterOrigin` (`SAME_MODULE`, `CLASSPATH_DELEGATE`, `SYNTHETIC` with labels `same-module`, `classpath-delegate`, `synthetic`) plus `ConverterEntry.origin` and `ConverterEntry.callFqName` (D4), `ClasspathConverterScanner.resolvedEntries()`, and `GlobalConverterRegistry.knownEntries()`, with the supplier carried through `silent()` (D5). Verify with the group 2 and 3 tests that consume them.

## 2. KRAFT-E005 lists converters touching the failing types

- [ ] 2.1 Write `ConverterCandidatesInTypeMismatchTest` (jvmTest, `diagnostics/`) covering every `diagnostics/type-mismatch-hints` scenario added by this change: right source but wrong target; list element type; unrelated converter not listed; classpath delegate named with its origin (`compileWithUpstream`); no converters at all (exact "No registered converter…" and "Set the KSP option kraft.report=true…" wording). Verify that it fails before 2.2.
- [ ] 2.2 Implement D6: give `detailedTypeMismatch` a `converters` parameter, pass `ctx.globalConverters` from `DirectMatchRule`, `ClassOverrideRule` and `ConfigOverrideRule`, and render the section (cap 10). Verify that 2.1 passes and that `DuplicatedGeneratedTypeHintTest`, `DiagnosticCodesTest` and `NullableScalarBridgeTest` stay green.

## 3. kraft.report

- [ ] 3.1 Write `ProcessorReportTest` (jvmTest, `diagnostics/`) covering every `diagnostics/processor-report` scenario: option enabled and absent (the generated `.kt` files are identical); empty module with `moduleId`; byte-identical across two runs with sorted converters; same-module, nullable, classpath-delegate (with `module`) and synthetic converter entries; unused upstream delegate absent; declared and auto enum pairs; side and alias reported; `FULL_NAME_ONLY` gives no alias; report written on a failing (E005) compilation. Verify that it fails before 3.2.
- [ ] 3.2 Add `OPTION_REPORT = "kraft.report"` to `KraftKspConstants`, add the optional `targetFqn`/`sideName` parameters and `emittedAliases()` to `SideRegistry.recordAlias`, and pass them from `ExtensionMapperGenerator` and `EnumMapperGenerator` (D7). Verify that `SideRegistryCollisionTest` stays green.
- [ ] 3.3 Add the report model, the JSON writer and `KraftReportCollector` in `kraft-ksp` (D1, D3), feed it from `AutoMapperProcessor.process`, and write it in `finish()` and `onError()` through `CodeGenerator` at `kraft/report.json` (D2). Verify that 3.1 passes.

## 4. Gradle plugin DSL

- [ ] 4.1 Add a functional test to `KraftGradlePluginFunctionalTest`: `kraft { report = true }` emits `kraft.report=true`, and leaving it unset emits no `kraft.report`. Verify that it fails before 4.2.
- [ ] 4.2 Add `report: Property<Boolean>` to `KraftExtension` (KDoc and the class example) and emit it in `KraftKspArgEmitter` (D8). Verify with `./gradlew :kraft-gradle-plugin:test`.

## 5. Docs

- [ ] 5.1 Add `docs/user-guide/processor-report.md` (enable it with the DSL, a raw arg or a Gradle property; file location per target; every key; how to answer "is my converter seen?"; classpath-delegate caveat; packaging caveat; stability and `schemaVersion`) and its `mkdocs.yml` nav entry. Add a `kraft.report` section to `ksp-options.md`, a "Converters Kraft knows for these types" subsection under KRAFT-E005 in `errors.md`, a pointer from `ai-integration.md`, and a line in `.claude/skills/kraft-mappers/SKILL.md`'s debugging guidance. Verify that `ErrorsReferenceCompletenessTest` passes and that every nav entry resolves to a file (`mkdocs build --strict` if available).
- [ ] 5.2 Docs sweep: grep `docs/ROADMAP.md`, `docs/user-guide/` and `.claude/skills/kraft-mappers/SKILL.md` for `kraft.report`, "is my converter", "#5", "not seen" and "registered", and update each hit the change makes stale. Set roadmap item #5's heading status to `done in 0.15 (#<PR>)`. Verify that the roadmap status-check greps at the top of `docs/ROADMAP.md` print nothing for #5.

## 6. Full gate

- [ ] 6.1 Run `./gradlew detekt :kraft-ksp:jvmTest :kraft-gradle-plugin:test` and the KMP integration tests (`./gradlew -p integration-tests build`, or record why they cannot run in this environment). Verify that everything passes and record the results in the PR body.

## Workflow follow-up

- Review and merge the PR, then archive with `/opsx:archive kraft-report`.
- #34 extends the report with a `mappers` section under `schemaVersion` 1 (design.md D1).
