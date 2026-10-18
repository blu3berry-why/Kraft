# Tasks

When this file and the dispatch brief conflict, this file and `design.md` win.

## 1. Pin the bug

> Agent: sonnet · effort medium · scope: kraft-ksp/src/jvmTest/** · verify: ./gradlew :kraft-ksp:jvmTest --tests "*RequiredFieldErrorRuleTest*" · after: —

- [x] 1.1 Tighten the two "no error" cases in `RequiredFieldErrorRuleTest` to also assert that a generated mapper file exists and contains `fun PersonSource.toPersonDto()` (use `compileAndReturnGenerated` or `sourcesGeneratedBySymbolProcessor`). Verify that both cases now **fail** on the current code, which is the confirmed silent drop, and commit them as `@Disabled("fix-silent-mapper-drop 2.x")` only if the group lands separately from group 2.

## 2. Resolver fallback and error tracking

> Agent: opus · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/jvmTest/** · verify: ./gradlew :kraft-ksp:jvmTest · after: 1

- [x] 2.1 Add `ErrorTrackingLogger` (delegating `KSPLogger` that counts `error()` calls) next to `MutedKspLogger`, and run the chain in `PropertyResolver.resolve` on `ctx.copy(logger = tracker)` (design D1). Verify that `probeShadowedNative` is untouched and the existing W001 tests stay green.
- [x] 2.2 Add the post-chain fallback in `PropertyResolver.resolve` (design D2): no strategy plus zero errors gives `Ignored` for defaulted targets and `Constant(target, "null")` for nullable targets without a default. Verify with a new jvmTest `missingsource/DefaultedTargetTest`, covering spec scenarios "Non-null property with a default", "Nullable property with a null default" and "Calling the generated mapper" (load the compiled class and invoke `toPersonDto()`), plus the tightened group-1 tests, now green and un-`@Disabled`.
- [x] 2.3 Verify that the fallback reaches every mapper kind. Add jvmTest `missingsource/MapperKindsTest`, covering spec scenarios "@MapFrom target with a default" and "Reverse mapper with a defaulted source-side property".
- [x] 2.4 Verify that the fallback never masks an error. Add jvmTest `missingsource/FallbackDoesNotMaskErrorsTest`, covering spec scenarios "Same-named source with the wrong type" (asserts no `KRAFT-W009`) and "Invalid @MapField override on a defaulted property".
- [x] 2.5 Fix `docs/user-guide/basic-mapping.md` "Properties with Default Values" so the generated-output snippet matches what 2.2 produces, and add a nullable-without-default example pointing at KRAFT-W009. Verify that the snippet matches the `DefaultedTargetTest` assertion text.

## 3. KRAFT-W009 and suppression

> Agent: opus · effort high · scope: kraft-core/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 2

- [x] 3.1 Add `KraftDiagnostic` W009 and its `LoggerExtensions` message with the exact first line and the three fix lines from the spec, emitted by the nullable branch of the fallback on `target.declaration`. Verify with jvmTest `missingsource/NullableWithoutDefaultTest`, covering spec scenarios "Nullable property without a default" (generated `nickname = null`, all quoted strings) and "Warning fails a warnings-as-errors build" (`warningsAsErrors = true`).
- [x] 3.2 Add `PropertyInfo.parameter: KSValueParameter? = null` (set in `TargetPropertyExtractor`) and `MappingContext.mappingDeclaration: KSAnnotated? = null` (set by all three descriptor builders), then check suppression on property, parameter and mapping declaration (design D4). Verify with jvmTest `missingsource/W009SuppressionTest`, covering spec scenarios "Suppressed on the mapping declaration", "Suppressed on the target property" and "Wrong code does not suppress", and assert that generated sources are byte-identical with and without suppression.
- [x] 3.3 Add the `KRAFT-W009` entry to `docs/user-guide/errors.md` (cause, minimal input, the three fixes). Verify with a green `ErrorsReferenceCompletenessTest`.

## 4. KRAFT-E065 guard and E004 hint

> Agent: sonnet · effort medium · scope: kraft-core/src/main/**, kraft-ksp/src/jvmTest/**, docs/user-guide/errors.md · verify: ./gradlew :kraft-ksp:jvmTest · after: 3

- [x] 4.1 Add `KraftDiagnostic` E065 and its message (exact spec wording), reported from the fallback's last branch (design D3). Verify with jvmTest `missingsource/NoSilentDropTest` scenario "Resolution yields nothing and nothing was reported", using `PropertyResolver(rules = listOf(MappingRule { _, _ -> null }))` on a hand-built `MappingContext`, which asserts the first line and the `See:` link.
- [x] 4.2 Add the `KRAFT-E065` entry to `errors.md`. Verify with a green `ErrorsReferenceCompletenessTest`.
- [x] 4.3 Change the E004 fix hint to `Or make the property nullable (Kraft then passes null and warns KRAFT-W009)` in `LoggerExtensions` and in the E004 entry of `errors.md` (design D5). Verify with a `RequiredFieldErrorRuleTest` assertion covering spec scenario "Required property without a source" (first line and new hint).

## 5. User-facing docs and skill

> Agent: sonnet · effort medium · scope: docs/**, .claude/skills/kraft-mappers/SKILL.md · verify: mkdocs build --strict (if available) · after: 4

- [x] 5.1 Add an Upgrading entry to `docs/user-guide/upgrading.md`: "Mappers that were silently missing are now generated". Name the symptom (a conflicting overload or redeclaration of a hand-written `toXxx()`) and the fix (delete the hand-written function), plus the new KRAFT-W009 for nullable fields without a default. Verify that the entry links to `errors.md#kraft-w009`.
- [x] 5.2 Update `.claude/skills/kraft-mappers/SKILL.md` "what Kraft does automatically": defaulted targets are omitted, nullable targets with no default get `null` plus W009, and how to suppress W009. Verify that the wording matches the spec requirements.
- [x] 5.3 Docs sweep: grep `docs/ROADMAP.md`, `docs/user-guide/` and `.claude/skills/kraft-mappers/SKILL.md` for `default value`, `make the property nullable`, `is omitted`, `KRAFT-E004` and `Constant`, and update each stale hit. Add a `docs/ROADMAP.md` Bugs entry ("Mappers silently dropped for defaulted / nullable targets with no source") marked done in 0.14. Verify by re-running the grep and listing the remaining hits with a reason each is correct.

## 6. Full gate

> Agent: sonnet · effort medium · scope: none (report only; fix only if a failure is in this change's files) · verify: the commands below · after: 5

- [ ] 6.1 Run `./gradlew detekt :kraft-ksp:jvmTest :kraft-gradle-plugin:test` plus the KMP integration-test tasks, all green. `agp9Test` may fail on Windows from the known unrelated `file://` URI bug (ROADMAP #23). Verify by recording the test counts in this task.

## Workflow follow-up

- Mark the bug done in `ROADMAP-draft.md`, and correct Tier 1 #2 (value classes) to done (#114). #3 shrinks to `@MapConstant` only.
- Archive the change with `/opsx:archive` after the PR is merged.
