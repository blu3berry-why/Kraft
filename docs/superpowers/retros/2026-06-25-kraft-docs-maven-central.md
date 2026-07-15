# Session Retrospective — Kraft docs: qmd index, Konvert comparison, Maven Central + legacy docs

**Date:** 2026-06-25
**Project:** Kraft
**Duration signals:** 1 tracked commit (`0723fa5`); branch `docs/maven-central-refresh` off main; ~8 user turns
**Scope:** conversation

## 1. Trial rule hits

| Rule | Fired? | Helped / friction / ignored | Note |
|---|---|---|---|
| Branch-base preflight | yes (2×) | helped | Off-theme docs caught at session start AND re-confirmed before commit → clean standalone PR off main, enum-aliases PR untouched |
| Grep before bulk doc updates | yes | helped | Found `tasks.md` is gitignored before assuming it'd commit; swept all stale `2.0+` / "GitHub Packages" refs |
| Proactive suggestions | yes (several) | helped | Surfaced qmd MCP wiring, memory save, Map/discoverability follow-ups |
| Pre-select default in user-judgment steps | yes | helped | Branch question via AskUserQuestion led with recommended "fresh off main" |
| Post-task prompting advice | yes (each completion) | neutral | Mostly (b) "clear"; 2 genuine (a) hits (see §2) |
| Commit-message-after-change (memory) | yes | helped | Suggested conventional message each code change |

(Threshold watch skipped — no prior retros in `docs/superpowers/retros/` to count cross-session occurrences.)

## 2. Prompting advice (deduplicated)

- [repeated 2×] When asking for a **comparison or audit**, name the axis or oracle to evaluate against — which competitor dimension matters (KMP? Maven Central? migration parity?), or which branch to audit against. Both detours this session traced to an unstated reference frame: the Konvert comparison swept broadly before the deciding factor was known, and the staleness audit chased qmd's feat-branch index before "vs main" was established.

## 3. Recurring gotchas

| Gotcha | Times seen | Central home candidate |
|---|---|---|
| zsh fails unquoted globs in tool args (`grep --include=*.kt`, `find -name "*.qmd"` patterns) with "no matches found" — kills the whole command | 2 | `memory/feedback_zsh_glob.md` (new) |

## 4. Memory entries written this session

_None written. Two offered and pending user approval (see §5)._

## 5. Unresolved / deferred

- **Discoverability (#2)** — kmp-awesome PR + GitHub repo topics + klibs.io listing. No tracker entry yet (`tasks.md` is the local backlog but gitignored).
- **`Map<K,V>` (#3, F-5)** — already tracked in `docs/tasks.md` backlog.
- **Project memories offered, not written** — (a) Kraft build-time/runtime split + real version floor (Kotlin 2.2.21 / KSP 2.3.3 / JDK 17 build); (b) qmd "Kraft" collection reference for searching docs/plans.
- **qmd index drifts per-branch** — built on `feat/map-enum-aliases`; reindexed against this branch but will mislead again on branch switch.

## 6. Action items (drafted — user reviews + applies)

- [ ] **(Pattern 2)** Promote the §2 advice "name the axis/oracle when requesting a comparison or audit" to either (a) a `~/.claude/CLAUDE.md` Always-on rule, or (b) a `feedback_*.md` memory. User picks a or b.
- [ ] **(new pattern candidate)** Write `memory/feedback_zsh_glob.md`: in zsh, quote glob patterns passed to `grep --include`/`find -name` (`--include='*.kt'`) so they don't expand/fail with "no matches found". Append one-line index entry to `MEMORY.md`.
- [ ] **(Pattern 4)** Add an `F-11` discoverability entry to `docs/tasks.md` backlog (kmp-awesome PR, GitHub repo topics, klibs.io) so it isn't lost.
- [ ] **(Proactive — offered)** Write the two project memories from §5: Kraft build-time/runtime + version-floor facts, and the qmd Kraft-collection reference.

## 8. Permission ledger

_Omitted — no approval-prompt friction observed this session. `git push` / `gh pr create` are outward actions and stay gated by design regardless._
