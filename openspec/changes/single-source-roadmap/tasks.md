# Tasks

When this file and a dispatch brief conflict, this file and `design.md` win.

Every group runs inline in the controller. The change touches 3 files (`docs/ROADMAP.md`, `openspec/config.yaml`, `ROADMAP-draft.md`), which is under the dispatch threshold, and group 3 is a user gate.

## 1. Status grammar and corrections (`docs:` commit 1)

- [ ] 1.1 Replace the intro paragraph of `docs/ROADMAP.md` ("Items marked ✅ were fixed…") with the D2 grammar and its two grep commands. Verify by reading the rendered intro: it names the three status forms and both commands.
- [ ] 1.2 Rewrite every existing `### 1.`–`### 27.` heading into `<Title> — <size> · <category> · <status>`. Strip the `~~` and `✅` markers, and look up each done item's `<ref>` as D4 describes. Verify that the "must print nothing" grep from D2 prints nothing, and that `git diff --word-diff` shows only heading lines changed in this task.
- [ ] 1.3 Apply the D4 corrections: set #6 to `done in 0.14 (#118)` and #3 to `partial (opt-out flag)`, move #27 under `## Bugs`, and remove finished items from the "Suggested order" text. Verify that the "what's left" grep lists neither #6 nor any item marked done, and that #27 now sits between #3 and `## DX and diagnostics`.
- [ ] 1.4 Commit as `docs: one greppable status per roadmap item`. Verify that `git show --stat` lists only `docs/ROADMAP.md`.

## 2. Features migration (`docs:` commit 2)

- [ ] 2.1 Copy `ROADMAP-draft.md` to the session scratchpad as a backup, since the file is untracked. Verify that the copy exists and is byte-identical (`cmp`).
- [ ] 2.2 Add `## Features` before `## Suggested order`, with a `## Positioning` part first. Add items #28–#38 under bold tier lead-ins, merge the evidence into #19, #20 and #23, and drop the stale `Reserved for future use` claim, all per the D3 table. Verify by walking the D3 table row by row against the file. Every row has a home, and both D2 greps still pass (#29–#38 show as open).
- [ ] 2.3 Merge the draft's "Proposed order" into `## Suggested order`, using the new numbers (D3, last row). Verify that the section mentions #29–#34 and nothing marked done.
- [ ] 2.4 Commit as `docs: move the feature roadmap into docs/ROADMAP.md`. Verify that `git show --stat` lists only `docs/ROADMAP.md`.

## 3. Retire the draft

- [ ] 3.1 [USER-GATE / controller-handled] Show Matyas the D3 table with the `docs/ROADMAP.md` line each row landed on. Delete `ROADMAP-draft.md` only after he confirms. Verify that `git status --short` no longer lists it and the scratchpad backup from 2.1 still exists.

## 4. Config rules (`chore:` commit)

- [ ] 4.1 Add the D5 `rules.proposal` entry and the `rules.tasks` entry to `openspec/config.yaml`, using the D2 status forms word for word. Verify that `openspec instructions proposal --change single-source-roadmap --json` and `openspec instructions tasks --change single-source-roadmap --json` both return the new rule in `rules`.
- [ ] 4.2 Commit as `chore(openspec): track roadmap updates as tasks, not follow-ups`. Verify that `git show --stat` lists only `openspec/config.yaml`.

## 5. Full gate

- [ ] 5.1 Run `openspec validate single-source-roadmap --strict` and both D2 greps, then `./gradlew detekt :kraft-ksp:jvmTest`. No code changed, so Gradle should report up-to-date; this only confirms nothing outside docs moved. Verify that validate passes, the greps behave as D2 says, and Gradle is green. Record the counts here.

## Workflow follow-up

- Open one PR from `docs/archive-fix-silent-mapper-drop` covering the archive commit plus commits 1.4, 2.4 and 4.2. Ask first, because it's an outward action.
- Archive this change with `/opsx:archive` after merge.
