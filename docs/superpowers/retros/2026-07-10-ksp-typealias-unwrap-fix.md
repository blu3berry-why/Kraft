# Session Retrospective — KSP typealias unwrap fix (PR #74)

**Date:** 2026-07-10
**Project:** Kraft
**Duration signals:** 4 commits (`9dd2b0d` → `43afd41`), squash-merged as `2d5963c` (#74), released as 0.10.1; ~6 user turns
**Scope:** conversation

## 1. Trial rule hits

| Rule | Fired? | Helped / friction / ignored | Note |
|---|---|---|---|
| Branch-base preflight | yes (1×) | helped | Session opened on `docs/maven-central-refresh`, work was a kraft-core code fix — one-word confirm → fresh `fix/ksp-typealias-unwrap` off main; docs branch untouched |
| Pre-select default in user-judgment steps | yes (2×) | helped | Branch question + "ship how?" both led with a recommended option; user confirmed in a word each time |
| Post-task prompting advice | yes | neutral | All (b) — user prompts carried full context (analysis doc, CI pointer, scoped review ask); quiet-when-established not reached (session too short) |
| Proactive suggestions | yes (2×) | helped | kraft-mappers skill staleness + parameterized-alias limitation tracking surfaced unprompted |
| Commit-message-after-change (memory) | yes | helped | Conventional commits throughout; 3-commit split on review turn kept concerns reviewable |
| superpowers:test-driven-development | yes | helped | Red-first caught 2 things a code-first pass would have shipped blind: opt-in-alias case is impossible in valid Kotlin (test-invalid → change reverted), and `List<Aliased>` identity already worked (kept as regression guard, no code) |
| superpowers:receiving-code-review | yes (2×) | helped | Verified CodeRabbit finding against code before acting; caught the second, verbatim re-submission as already-fixed (one grep, zero rework) |

(Threshold watch: only 1 prior retro exists. Branch-base preflight now at 2/2 retros, helped both — nowhere near the ~5-session decision point.)

## 2. Prompting advice (deduplicated)

- [repeated 2×] The same CodeRabbit finding was submitted twice (second time verbatim, after it was already fixed and pushed). When re-pasting reviewer findings, a one-line "may already be addressed — dedupe first" saves the assistant from re-implementing; this session the receiving-code-review skill caught it, but the cheaper guard is in the prompt.

## 3. Recurring gotchas

| Gotcha | Times seen | Central home candidate |
|---|---|---|
| kctfork `sourcesGeneratedBySymbolProcessor` order is filesystem-dependent — `generated.first()` grabs the delegate registry on Linux CI, the mapper on macOS. Any fixture that emits a registry (top-level `@KraftConverter` or `@MapEnum`) must select generated files by name, never positionally | 3 tests + 1 probe, single root cause | new doc: `docs/developer-guide/ksp-compile-testing-gotchas.md` |
| Test fixtures without a `package` declaration break converter codegen assertions — Kotlin cannot import top-level functions from the root package, so generated mapper/registry files fail to resolve the converter | 1 (but same test-authoring domain as above) | same doc |
| zsh expands/mangles unquoted special tokens in Bash tool args — this session `echo ====` died on `=`-expansion; prior session unquoted globs in `grep --include` | 2nd cross-session occurrence (new shape) | extend `memory/feedback_zsh_glob.md` |
| KSP typealias semantics surprises: KSP2 preserves aliases inside `X::class` annotation literals; compiler forbids aliasing `@RequiresOptIn` markers; alias RHS resolves without use-site type-arg substitution | 3 distinct surprises in one domain | already captured in `TypeAliasExtensions.kt` KDoc + PR #74 body; skill note pending (§5) |

## 4. Memory entries written this session

- `project_kraft_compat.md` — updated: 0.10.1 released with typealias fix; Re-Claw kmpgen-1.5.0 verification follow-up; parameterized-alias limitation. (Index line in `MEMORY.md` updated to match.)

## 5. Unresolved / deferred

- **Re-Claw verification** — bump kmpgen 1.5.0 + Kraft 0.10.1, compile `:feature:product-catalog:data` + `:feature:walk-through:data`. Tracked in `project_kraft_compat.md` memory + PR #74 body.
- **kraft-mappers skill staleness** — skill still documents kmpgen-alias gotchas as live; needs "fixed in Kraft ≥0.10.1" note. Needs tracker (action item below).
- **Parameterized type aliases** (`typealias X<T> = ...`) unsupported — clear error since 0.10.1, no feature tracker entry. Needs tracker.
- **19 other `.first()`-based tests** — safe today (single-file fixtures) but positionally fragile if fixtures ever gain converters. Deferred consciously; covered by the gotchas doc rather than a mass edit.

## 6. Action items (drafted — user reviews + applies)

- [ ] **(Pattern 1)** Create `docs/developer-guide/ksp-compile-testing-gotchas.md` consolidating the 2 test-authoring traps (generated-file ordering / select-by-name; root-package fixture imports). Reference it from `CONTRIBUTING.md`'s testing section.
- [ ] **(Pattern 4)** Add a backlog entry to `docs/tasks.md` for parameterized-type-alias support (F-series), noting the 0.10.1 error message as the current stopgap.
- [ ] **(generic — no pattern match)** Append a "Fixed in Kraft ≥0.10.1" note to the kraft-mappers skill's kmpgen-gotcha section (`~/.claude/skills/` or plugin equivalent) so the workaround guidance doesn't outlive the bug.
- [ ] **(gotcha extension)** Extend `memory/feedback_zsh_glob.md` to cover zsh special-token expansion generally (globs AND bare `=`/`====` tokens): quote any non-alphanumeric literal in Bash tool args.

## 7. Cost ledger

_Omitted — no subagent dispatches, no inline model switches this session._

## 8. Permission ledger

_Omitted — no approval-prompt friction observed. `git push` / `gh pr create` stay gated by design._
