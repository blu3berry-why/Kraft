# Session Retrospective — user-guide audit + Re-Claw PR #57 refresh

**Date:** 2026-07-13
**Project:** Kraft (background job, worktree `docs-user-guide-audit`)
**Duration signals:** Kraft commit `3c715a2` (PR #82) + Re-Claw commit `f158ed7` (pushed to PR #57); ~8 user turns
**Scope:** conversation

## 1. Trial rule hits

| Rule | Fired? | Helped / friction / ignored | Note |
|---|---|---|---|
| Name the reference frame for comparisons/audits | yes (1×) | helped | Stated oracle ("live source on this branch") before the 14-page audit; no re-run needed. Cross-retro: this rule was *born* from the 2026-06-25 retro's repeated advice — first session where it fired as a rule |
| Branch-base preflight | yes (1×) | helped | Checked user-guide diff main↔dev-guide-refresh before basing worktree on main — identical, so no wrong-base risk. Now 3/3 retros |
| Proactive suggestions | yes (3×) | helped | Optional enhancements (reverse+enum section, auto-derive example) — user took both; Re-Claw branch-name discrepancy flagged; follow-up bump-session recommended. Now 3/3 retros |
| Post-task prompting advice | yes | neutral | 1× (a) "name the oracle in the prompt", 1× (a) "say 'body too'", 1× (b). Now 3/3 retros |
| Pre-select defaults in user-judgment steps | yes (1×) | helped | PR #57 verdict led with "merge as-is" + one-line reason, alternative second. Now 3/3 retros |

_Threshold watch: 3 retros total exist — Branch-base preflight, Proactive suggestions, Post-task prompting advice at 3/3; none at the ~5-session decision point yet._

## 2. Prompting advice (deduplicated)

- [repeated 2×] Bound the target of a fix/audit request explicitly — which oracle to audit against ("check docs against current source"), and which artifacts are in scope ("fix the stale text — PR body too, or just the branch files?"). Both judgment calls this session traced to an unstated scope boundary. Note: the audit-oracle half is already a CLAUDE.md rule (fired, helped); the artifact-scope half is new.

## 3. Recurring gotchas

| Gotcha | Times seen | Central home candidate |
|---|---|---|
| zsh command failures on unquoted special tokens (`echo ===` → `== not found`; `ls .lefthook*` → `no matches found`) despite existing `feedback_zsh_glob` memory | 2 | already in memory — still biting; internalize: quote every `*`/`=` token in Bash calls, or `setopt no_nomatch` prefix |
| Planning docs referencing local-only branches that can't be verified remotely (Re-Claw gotchas §2/§5 say `chore/kmpgen-1-5-0`, NEXT.md says `chore/kmpgen-1-4-1`) | 2 files | Re-Claw repo concern — flagged in PR #57 comment for user to resolve locally |

## 5. Unresolved / deferred

- Re-Claw: bump `kraft` 0.10.0→0.10.1 + `kmpgen` →1.5.x, run gotchas §Verification checklist — tracked in Re-Claw `planning/features/NEXT.md` (updated this session) + gotchas §4 "verification pending" line
- Re-Claw: resolve `chore/kmpgen-1-5-0` vs `chore/kmpgen-1-4-1` local branch-name discrepancy — tracked in PR #57 comment
- Kraft PR #82 (user-guide fixes) is draft — needs review + ready + merge; tracked on GitHub
- Memory `project_kraft_compat` "Re-Claw kmpgen-1.5.0 verify pending" — update after the Re-Claw verify session lands

## 6. Action items (drafted — user reviews + applies)

- [x] **(Pattern 2)** Promote the artifact-scope half of §2 — applied 2026-07-13 as a fix-scope clause in the CLAUDE.md reference-frame rule (defaulted to CLAUDE.md over memory: same rule family, fires at the same moment).
- [x] **(no pattern — hygiene)** zsh gotcha — applied 2026-07-13: `feedback_zsh_glob.md` updated with the bare-glob-as-direct-arg shape (`ls .lefthook*`) + noted the `===` separator recurrence despite the memory (that shape was already documented).

(§4 omitted — no memory entries written. §7 omitted — zero subagent dispatches, no model switches. §8 omitted — no permission prompts; one transient Bash-classifier outage mid-session, resolved on retry, not a permission/hook issue.)
