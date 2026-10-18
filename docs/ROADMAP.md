# Kraft Roadmap

Findings from a library audit run against `main` at **0.13.0** (2026-08-22), plus the
prioritised plan that follows from them.

Scope of the audit: open GitHub issues (none — the tracker is empty), TODO/FIXME markers
(none — `CONTRIBUTING.md` forbids them), the CI matrix, test coverage, README/docs accuracy
against current behaviour, API rough edges, and four pain points reported by Kraft's main
consumer, the Re-Claw project.

Items are numbered once and never renumbered, so `Roadmap: #N` in a change or commit always
means the same item. Every item heading has this form:

```
### <N>. <Title> — <size> · <category> · <status>
```

- **size:** **S** (< half a day), **M** (1–3 days) or **L** (a week or more).
- **category:** **Bug**, **DX**, **Docs**, **Test**, **CI**, **Feature** or **Nice-to-have**.
- **status:** `open`, `partial (<what remains>)` or `done in <version> (<ref>)`, where `<ref>` is
  a PR (`#122`) or the OpenSpec change that shipped it.

Done items stay in place, so the reasoning behind them stays recorded. To see what is left, and
to check that no heading has lost its status (the second command must print nothing):

```
grep -nE '^### [0-9]+\..* · (open|partial)' docs/ROADMAP.md
grep -nE '^### [0-9]+\.' docs/ROADMAP.md | grep -vE ' · (open|partial \(.*\)|done in [^ ]+ \(.+\))$'
```

---

## Consumer pain-point verification

Re-Claw reported four recurring problems, re-tested against `main` at 0.13.0. The first
covers two separate features, so it is split into two checks below — five rows for four
reports. Two are real defects (both already fixed in code, both still mis-documented), and
two are discoverability failures where the code was correct all along.

| # | Reported | Reproduced? | Verdict |
|---|---|---|---|
| RC-1 | `List<X>? → List<Y>` auto-mapping "not supported" | **No** — works | Discoverability defect (see item 10) |
| RC-2 | `@MapEnum` "not supported" | **No** — works, including inside collections | Discoverability defect (see item 10) |
| RC-3 | `@OptIn` emission classpath-sensitive | **Yes**, as a historical bug | Code fixed in 0.13.0; docs still wrong (see item 8) |
| RC-4 | `@MapUsing` / `@KraftConverter` rules cost debugging time | **Yes** | Docs actively contradicted behaviour (see item 7); error messages did not name the rules (see item 4) |
| RC-5 | kmpgen gotchas K1 / K-A1 stale? | **No** | Both current and correctly version-stamped |

**RC-1 — nullable collections.** `CtorCallBuilder.addCollectionNestedLine` emits
`this.xs?.map { it.toY() } ?: emptyList()` for `List<X>?` → `List<Y>`, and the equivalent
`?.toSet() ?: emptySet()` for sets. `docs/user-guide/collection-mapping.md` documents every
nullable permutation correctly. The code and the reference docs were never the problem.

**RC-2 — `@MapEnum`.** Works, auto-derives by entry name, and — through
`GlobalConverterRule.findCollectionElementMatch` — is applied element-wise to
`List<S>`/`Set<S>` properties with no extra annotation.

**Why the misconception recurred across three retrospectives.** Re-Claw's authors are
coding agents reading `.claude/skills/kraft-mappers/SKILL.md`, which Kraft ships for exactly
that purpose. That file described the resolution order as *identical name+type → converter →
nested mapper → enum mapper* and **never mentioned collections at all** — not in the mental
model, not in the decision tree. An agent reading it had no reason to believe `List` mapping
existed, and correctly-documented behaviour on the docs site does not help a reader who is
looking at a different file. Fixing the reference docs alone would not have stopped this;
the shipped skill is the consumer-facing surface here, and it needs to be maintained as one.

**RC-3 — opt-in propagation.** The reported symptom (present with one kmpgen version,
silently dropped with another, no consumer source change) matches issue #106 exactly:
generators differ on emitting `@OptIn` on the declaration versus `@file:OptIn` on the file,
and `OptInMarkerCollector` only walked declaration annotations. Fixed in 0.13.0 (#107) —
`collectFrom` now walks `symbol.containingFile.annotations` too. Generated code owns its
opt-ins deterministically on 0.13.0+, and no module-wide `-opt-in` compiler argument should
be needed. The reference docs were never updated to say so.

**RC-4 — converter rules.** The standalone restriction (top-level, extension receiver, no
value parameters, no parameterized types) is documented and its error messages are specific,
including the parameterized case corrected in #110. The nullable-scalar lift is where the
time went: `X? → Y?` reuses a registered non-null `X → Y` bridge through a safe call as of
#108, but the docs asserted the exact opposite, and the error a consumer actually hits said
only "align nullability" without naming the rule, `@KraftConverter`, or `@MapEnum`.

---

## Bugs

### 1. Nullable enum elements in collections are not bridged — M · Bug · done in 0.14 (#120)

Shipped by the `map-and-collection-mapping` OpenSpec change (PR #120): an element lookup that
misses on the nullable key retries the non-null key, for synthetic enum entries as well as
`@KraftConverter`s. `List<E?>` → `List<E?>` keeps nulls, `List<E?>` → `List<E>` drops them,
and map values follow the same rule. Pinned by `NullableEnumElementTest`.

### 2. Hand-written `@KraftConverter`s are silently skipped for collection elements — M · Bug · done in 0.14 (#120)

Shipped by the same change: a hand-written converter is applied at element position under its
own name, at any nesting depth. `CollectionElementConverterTest`'s second case now asserts it,
and gotcha K6 in the shipped agent skill is marked fixed.

### 3. Nullable collection source silently becomes empty; the scalar equivalent errors — M · Bug · partial (opt-out flag)

`List<X>?` → `List<Y>` compiles to `?: emptyList()`, collapsing "absent" and "empty". The
single-object equivalent (`X?` → `Y`) is a hard error (KRAFT-E014), and the scalar converter
equivalent is deliberately not lifted (KRAFT-E005).

*Done:* `collection-mapping.md` now states the three rules side by side, with the reasoning and
the "keep the target nullable" escape hatch for codebases where null and empty differ.

*Open (M):* a `@MapConfig` flag that makes the nullable-collection case an error instead.

### 27. Mappers silently dropped for defaulted / nullable targets with no source — M · Bug · done in 0.14 (#122)

A mapper whose target had a defaulted or nullable property with no source was skipped with no diagnostic, so the missing `toXxx()` surfaced only at the call site. Defaulted targets are now left out of the constructor call, a nullable target without a default gets `null` plus the new KRAFT-W009, and the internal guard KRAFT-E065 ensures a mapper is never dropped without a diagnostic. Upgrade note in `upgrading.md`.

---

## DX and diagnostics

### 4. Type-mismatch error named neither `@KraftConverter` nor the nullability rule — S · DX · done in 0.14 (#113)

`detailedTypeMismatch` — the most-hit error in the processor — offered only "align
nullability / use `@MapUsing` / ensure both types are compatible". It never mentioned
`@KraftConverter` (the module-wide fix) or `@MapEnum`, so users reached for a per-mapper
`@MapUsing` override for pairs a single global converter resolves once. For a nullable
source with a non-null target it said "align nullability", hiding the actual rule: the pair
*is* bridged automatically when both sides are nullable.

**Fixed.** The message now names `@KraftConverter` and `@MapEnum` with a filled-in snippet,
and the nullable-source/non-null-target case gets dedicated text stating the both-sides rule,
why it exists, and the three ways out. Covered by two tests in `NullableScalarBridgeTest`.

### 5. Errors do not report what the processor actually discovered — M · DX · open

Every "is my converter being seen?" investigation — especially cross-module, where discovery
runs off the klib classpath — currently ends in reading generated output or bisecting the
build. Nothing in the processor will tell you what it registered.

*Scope:* a `kraft.report` KSP option that writes the resolved converter table (with each
entry's origin: same-module, classpath delegate, or synthetic), derived enum pairs, and
resolved side aliases. Extend item 4 to list registered converters whose source or
target matches the failing property.

### 6. Errors carry no stable codes — M · DX · done in 0.14 (#118)

Messages are well-formatted prose with no identifier, so they cannot be searched, linked to a
docs anchor, or asserted against in consumer test suites without matching on prose that
changes (as it does in this very change).

*Scope:* assign `KRAFT-Ennn` codes in `LoggerExtensions.kt`, print them in the header block,
and add a docs page listing each code with its cause and fix.

---

## Docs

### 7. `custom-converters.md` contradicted shipped nullable-lift behaviour — S · Docs · done in 0.14 (#113)

The Restrictions section asserted "`Uuid → String` does not auto-lift to `Uuid? → String?`;
declare the nullable variant separately". #108 made that false in 0.13.0 and the page was not
updated — so a consumer following the docs would declare a redundant converter, or conclude
the feature was broken when the single declaration turned out to be enough. This is the
single highest-cost item in the audit: wrong documentation is worse than missing
documentation.

**Fixed.** The bullet now states the real rule, including why the nullable-source/non-null-target
pair is deliberately excluded and what to use instead.

### 8. Opt-in propagation section omitted `@file:OptIn` — S · Docs · done in 0.14 (#113)

The section listed only declaration sites (source class, target class, `@MapConfig` object,
`@MapUsing`, `@KraftConverter`). File-level `@file:OptIn` — the form that made this
classpath-sensitive in the first place (RC-3) — went unmentioned even after #107 added support
for it, leaving a reader to conclude it was unsupported.

**Fixed.** The section now covers both placements, names the pre-0.13.0 symptom, and states
the contract: generated code owns its opt-ins; a module-wide `-opt-in` argument should not be
necessary, and needing one on 0.13.0+ is a bug report.

### 9. README and Getting Started understated CI coverage — S · Docs · done in 0.14 (#113)

Both claimed "other KSP 2 releases are not CI-tested". That predates #102, which added a KSP
matrix (2.3.3, 2.3.9), a Gradle matrix (8.13, 9.5.1, 9.6.1), a newest-stack leg (Kotlin
2.4.10 / KSP 2.3.9 / Gradle 9.6.1), and a weekly probe against the newest KSP on Maven
Central. The docs were talking consumers *out* of upgrades the project actually tests.

**Fixed** in both places, including the previously undocumented Gradle floor.

### 10. The shipped agent skill had no collections coverage — S · Docs · done in 0.14 (#113)

Root cause of RC-1 and RC-2 (see above).

**Fixed.** `SKILL.md` gains a "what is already automatic" table (element-wise `List`/`Set`,
`List<A>?` → `List<B>`, `List<A?>` → `List<B>`, `A?` → `B?` scalar bridging), a decision-tree
branch stating that collections need no annotation and that the *element* pair is what gets
annotated, gotchas K4 (opt-in) and K5 (the one non-lifted nullable pair), three new
common-error rows, and a corrected version reference. `ai-integration.md`'s summary of the
skill was updated to match.

### 11. No stated opt-in contract in the user guide — S · Docs · open

The generated-code opt-in guarantee is now described inside the `@KraftConverter` section,
which is not where someone hitting an opt-in error will look.

*Scope:* a short "Experimental APIs and opt-in" page covering what propagates, from where,
what to do when a marker still leaks, and why compiler-level `-opt-in` is a workaround rather
than the intended setup.

### 12. Version drift in example snippets — S · Docs · open

`docs/index.md` hardcodes `id("com.google.devtools.ksp") version "2.3.3"` while every other
page uses a `<ksp-version>` placeholder. It will go stale silently.

*Scope:* use the placeholder consistently, or add a docs build check that flags hardcoded
dependency versions in snippets.

### 13. No migration note for the 0.13.0 behaviour change — S · Docs · done in 0.14 (#121)

`docs/user-guide/upgrading.md` lists the changes that can affect a working build, per release:
the 0.13.0 nullable-scalar bridging and `@file:OptIn` propagation, and the 0.14.0 warnings,
diagnostic codes, new error codes and frozen deprecated annotations. Add a section there for
every release that changes behaviour.

---

## Test and CI gaps

### 14. The sample app is never compiled on pull requests — S · CI · open

`test.yml` runs detekt, `:kraft-ksp:jvmTest`, the Gradle plugin suites, and the KMP
integration tests. It never builds `:composeApp`, the sample that exercises the annotations
end to end. `./gradlew build` only runs in the Sonar job, which is `push`-to-`main` only — so
a PR that breaks the sample merges green and fails afterwards on the default branch.

*Scope:* add `:composeApp:compileKotlinJvm` (or the metadata compile) to the test job.

### 15. Kover aggregation omits the Gradle plugin — S · CI · open

The root `build.gradle.kts` aggregates `:kraft-core` and `:kraft-ksp` only. `:kraft-gradle-plugin`
has a substantial functional-test suite (wiring, DSL, AGP 9, error paths) whose coverage is
invisible to Sonar, making the reported number describe a subset of the shipped code.

*Scope:* add `kover(project(":kraft-gradle-plugin"))`; confirm TestKit-run coverage is
attributed correctly before trusting the delta.

### 16. The pinned KSP matrix leg trails the probe — S · CI · open

`test.yml` pins `2.3.9`, commented "newest known-good at 2026-07-21". Maven Central's newest
release is now **2.3.11**, and the weekly `ksp-latest` probe was green on 2026-08-13. The
probe exists precisely to license this bump; nothing currently closes the loop from a green
probe to an updated pin.

`./gradlew :kraft-gradle-plugin:test -Pkraft.test.kspVersion=2.3.11 -PkraftExcludeTags=android`
was run during this audit and passed (10 tests). The Android-tagged tests were *not* covered —
they need an Android SDK, which the audit environment did not have — so the bump is left here
rather than applied, since the matrix leg it would change runs the Android tests too.

*Scope:* re-run that command without `-PkraftExcludeTags=android` on a machine with an SDK, then
bump the leg to 2.3.11 and refresh the date in the comment. Separately, consider having the
weekly probe open an issue when it passes against a version newer than the pin, so closing the
loop does not depend on someone remembering.

### 17. No cross-module test for opt-in propagation — M · Test · open

`OptInPropagationTest` covers `@file:OptIn` well, but in-module only. RC-3 bit in the
cross-module case, where converters resolve as classpath delegates from a klib — the one
configuration the existing tests do not reach.

*Scope:* extend `integration-tests/kmp-producer` with a `@file:OptIn`-annotated converter over
an experimental type and assert the consumer's generated mapper carries the marker. The
harness already exists; this is a fixture addition, not new infrastructure.

### 18. Generated code is asserted as text, never executed — M · Test · open

The KSP suite asserts on the *source* of generated mappers. Nothing runs a mapper and checks
the resulting value, so a mapper that compiles but assigns the wrong field passes. The one
place that could catch it, `composeApp/src/commonTest`, is not in the CI test job (item 14).

*Scope:* a round-trip suite over the sample models — map, map back, assert equality — covering
nested objects, collections, enums, and each nullable permutation. This is the coverage that
would most cheaply catch a regression in `CtorCallBuilder`.

---

## Nice-to-have

### 19. No built-in converter set for stdlib pairs — L · Nice-to-have · open

The docs already state that every module must declare its own `Int → Long`, `Uuid → String`,
`Instant → Long`. It is the most-repeated boilerplate in a library whose purpose is removing
boilerplate.

*Scope:* needs a design decision first — an opt-in artifact of `@KraftConverter` declarations
raises questions about ambiguity against user-declared converters (local should win, which the
registry already models) and about the "no runtime dependency" promise. Worth an RFC before
any code.

*Candidate first set* (for the RFC): an optional `kraft-converters`
module with kotlinx-datetime `Instant` ↔ `String`/`Long`, `Uuid` ↔ `String`, and numeric
widening. It saves writing the same five `@KraftConverter`s in every project.

*Evidence (Re-Claw, 2026-10-18):* `Long → Int` is declared in 4 modules and `Int → Long` in 2,
all with identical bodies, even though `core/data/SharedKraftConverters.kt` already declares
`Long → Int`. First find out whether that's the missing built-ins or a cross-module discovery
gap (the feature modules may not see the shared copy); the answer may change this item's priority.

### 20. `Map<K, V>` and arrays are unsupported — L · Nice-to-have · done in 0.14 (#120)

Shipped by the `map-and-collection-mapping` OpenSpec change (PR #120): `Map`/`MutableMap`,
`Array`, `Collection`, `Iterable` and the `Mutable*` kinds, lossless conversions between them,
one-to-one key mapping (KRAFT-E064 otherwise) and nesting to any depth.

*Before:* `CollectionKind { LIST, SET }` and the "non-List collections are not supported"
error meant any other container fell back to a hand-written mapper. It was Tier 1 #1 of the
feature roadmap.

### 21. Remove the deprecated nested-mapping annotations at 1.0 — S · Nice-to-have · open

`@MapNested` and `@NestedMapping` are deprecated in favour of auto-detection and produce four
deprecation warnings in Kraft's own build (`KraftKspConstants.kt`) on every compile.

*Scope:* schedule removal for 1.0; until then suppress at the reference sites so the build
output stays clean and real warnings stay visible.

### 22. Action version drift in workflows — S · Nice-to-have · open

`docs-deploy.yml` uses `actions/checkout@v4`; every other workflow uses `@v6`.

*Scope:* align, and consider Dependabot for `github-actions` so this does not recur.

### 23. `agp9Test` fails on Windows — S · Test · open

Found 2026-10-15 while gating the `map-and-collection-mapping` change. `KraftGradlePluginFunctionalTest.kt`
(lines 37 and 45) builds its test repositories as `maven(url = "file://$testRepo")`, so a
drive-letter path fails with `Cannot convert URI 'file://V:/…' to a file`. Linux CI is
unaffected, which is why it went unnoticed.

*Scope:* use `testRepo.toURI()` / `libsRepo.toURI()`; ship as its own `fix:` PR.

### 24. One cause, three errors for KRAFT-E015 — S · DX · open

A `@MapNested` property whose element type is not a mappable class (e.g. an interface)
reports KRAFT-E015, then a follow-on KRAFT-E005 and KRAFT-E004 for the same property. 0.14
added `RuleOutcome.Reported` so a rule can stop the chain after reporting; E063 and E064
already use it.

*Scope:* return `RuleOutcome.Reported` after `nestedTypeNotMappable` in `NestedRule`, and
assert in the existing E015 test that no `[KRAFT-E005]` follows.

### 25. Broken docs links never fail a build — S · CI · open

Found 2026-10-18 while writing `upgrading.md`. `docs-deploy.yml` runs `mkdocs build` without
`--strict`, so a broken link or anchor deploys silently, and no PR check builds the site at
all. mkdocs is not installed on the dev machine either, so new links are checked by hand.

*Scope:* use `mkdocs build --strict` in `docs-deploy.yml`, and add the same strict build as a
PR check that runs when `docs/**` or `mkdocs.yml` changes.

### 26. Docs deploy before the stable release is on Maven Central — S · CI · open

`docs-deploy.yml` fires on the GitHub Release, but stable publishing is `USER_MANAGED`
(`settings.gradle.kts`): artifacts wait in the Central Portal until someone clicks Publish.
Between the two, the docs site describes a version consumers cannot resolve.

*Scope:* deploy stable docs by `workflow_dispatch` after clicking Publish, or switch stable
releases to `AUTOMATIC` now that every release is tested as an RC first.

## Positioning

*Checked 2026-10-11. Re-check before relying on the timing.*

"Best cross-platform mapper" is judged against two kinds of competitor:

- **Konvert** (KSP, the feature leader on JVM): data/enum/**value** classes, POJOs, **Map**
  element mapping, reusable `@Konverter`s, built-in type converters. **KMP status:** not in any
  release (latest is v4.5.1, 2026-07-24). The maintainer has a draft PR open, mcarleio/konvert#219
  (`feat/multiplatform`, 33 commits, last updated 2026-08-10), and a contributor PR fixing
  `kspCommonMainKotlinMetadata` crashes (#223) was merged into that branch. **Kraft's KMP lead is
  real but temporary**, likely a matter of months. Close Tier 1 before Konvert ships KMP.
- **Small KMP mappers** (komap, kMapper, KMapper, MapKt, kautoMapper): KMP-native but shallow.
  KMapper's selling point is a `Result<T>` return for fallible mapping.

Kraft already wins on: KMP-first design, a Gradle plugin and `kraft { }` DSL, side aliases
(`.toDomain()`), cross-module `@KraftConverter` discovery, a CI version matrix, a docs site and
the AI skill. Its gap is **type coverage**: users drop a mapper library the first time it can't
map a field, not over a missing advanced feature.

---

## Features

Sizes for #29–#38 are first estimates.

**Tier 1: parity gaps (target 0.14).** Removes the "falls back to hand-written" moments. Already
covered elsewhere: Map and other collections (#20, done), "use the target default when the source
is missing" (#27, done). The built-in converter module (#19) is deferred: it needs an RFC
first (see Suggested order, "Later"). #29 moves to 0.15 to be designed with #40.

### 28. Value classes are not wrapped or unwrapped automatically — M · Feature · done in 0.14 (#114)

`UserId(val value: String)` ↔ `String` now maps without a converter: plain, nullable (safe call)
and element-wise, including Map keys and values. Typed IDs are standard in domain models.

### 29. No way to set a target property to a constant or expression — M · Feature · open

Today a constant (`isActive = true`, `currency = "HUF"`) needs a whole-source `@MapUsing`.
`PropertyMappingStrategy.Constant` already exists and generates `name = <expr>`; since #122 it
also carries the KRAFT-W009 null fill. What's missing is the annotation (`@MapConstant` or an
expression form) and the rule that produces it.

Moved to 0.15 so it is designed together with #40: with flat annotations, `@MapConstant` is a
class-level annotation on the mapper object rather than a fourth array in `@MapConfig`.

*When it ships:* add it to KRAFT-W009's fix list.

**Tier 2: differentiators (0.15–0.16).** Makes the "best" claim possible.

### 30. No sealed hierarchy mapping — L · Feature · open

Subtype-to-subtype `when` (OpenAPI `oneOf`). Few KSP mappers do this well, and it's where
API-first clients hurt most.

### 31. No fallible mapping — M · Feature · open

A `toDomainOrNull()` / `Result`-returning variant for `X?` → `X`. Matches KMapper's pitch
without giving up compile-time strictness.

### 32. Mappers cannot take extra parameters — M · Feature · open

`fun Dto.toDomain(now: Instant)`. Removes the main reason for whole-source converters.

### 33. No update/patch mappers — M · Feature · open

`fun Patch.applyTo(t: T): T` via `copy`, for PATCH endpoints and form state.

### 34. No unmapped-target policy — M · Feature · open

`strict` / `warn` / `ignore` in the DSL, plus a per-mapper coverage report. Matches MapStruct's
`unmappedTargetPolicy`, which JVM users expect. It would also be the project-wide knob for
KRAFT-W009.

**Tier 3: trust and adoption (alongside).**

### 35. No comparison page or Konvert migration guide — M · Docs · open

Kraft vs Konvert vs MapStruct, plus a Konvert → Kraft migration guide.

### 36. No build-time benchmark or verified incremental-processing story — M · Test · open

### 37. Generated extensions are unchecked from Swift — S · DX · open

Check how the generated extensions look from iOS (is `@ObjCName` needed?).

### 38. New Kotlin and KSP releases are tracked by hand — S · CI · open

Track new Kotlin 2.x and KSP releases in the existing version matrix.

### 39. Coding agents searching for a KMP mapper don't find Kraft — M · Docs · open

Parked 2026-10-18; not yet explored. Agents tend to find libraries through search results,
READMEs and docs pages, Maven Central / package metadata and `llms.txt`-style files more than
through video comments. Work out where they actually look before choosing channels.

### 40. `@MapConfig` rules can only be written as nested arrays — M · Feature · open

`fieldMappings = [FieldMapping(...)]`, `ignoredMappings = [MapIgnoreField(...)]` and friends
nest every rule inside one annotation, which is hard to read, review and edit. Allow the same
rules as flat, repeatable annotations on the mapper object, one line per rule:

```kotlin
@MapConfig(source = UserDto::class, target = User::class)
@FieldMapping(source = "name", target = "fullName")
@MapIgnoreField("internalNotes")
@MapConstant(target = "currency", value = "\"HUF\"")   // #29
object UserMapper
```

*Scope:* reuse the existing annotation classes (add `@Target(CLASS)` and `@Repeatable`) so there
are no new names; keep the array form working; the same rule given both ways is a compile error.
Both forms feed the existing `ConfigObjectScanResult`, so nothing after the scanner changes.
*First step:* a spike confirming KSP2 sees repeatable `SOURCE` annotations in the KMP metadata
compilation, and that a class-targeted annotation can still be used inside an array argument.
Designed together with #29.

---

## Suggested order

**First — correctness of what we tell people.** Item 11 is the last one (S); like the
finished docs items, it removes a way for a consumer to reach a wrong conclusion about
working code. The audit's clearest lesson is that
Kraft's defect rate is low, and that its *explanation* rate is what has been costing consumers
days.

**Second — close the CI gaps.** Items 14, 15, 16, 22, 25 and 26 are all S and mechanical. 14 and 16
are the two that would have caught real problems.

**Third — diagnostics.** Item 5 is the structural fix behind most of what this audit
found: today the only way to learn what the processor decided is to read its output. Items 17
and 18 belong here too — both close the gap between "the generated text looks right" and "the
mapper does the right thing".

**Features.** 0.14 ships what is done; nothing in Features is left for it. 0.15 is #40 (flat
annotations) with #29 (constants) designed together, then #30 (sealed) and #34 (unmapped policy). 0.16 is #31,
#32 and #33 together, because they change the generated function signatures. Tier 3 (#35–#38)
runs alongside. Watch Konvert's KMP branch (see Positioning): Tier 1 should be closed before it
ships.

**Later.** Item 19 is genuine feature work and should not jump the queue ahead of the
diagnostics that make feature work debuggable. Item 21 rides along with 1.0.
