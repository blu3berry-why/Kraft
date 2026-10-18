# Design

## Context

See proposal.md (Why). The constraints:
- `docs/ROADMAP.md` is a narrative audit document: numbered `### N.` items under category sections (`## Bugs`, `## DX and diagnostics`, `## Docs`, `## Test and CI gaps`, `## Nice-to-have`), then `## Suggested order`. Item text explains reasoning and is meant to stay. Only headings and placement change.
- The config's docs-sweep task rule already greps `docs/ROADMAP.md`, so anything that moves into that file is swept from then on with no new process.
- Changes are written by agents that follow `openspec/config.yaml` rules, so a rule there reaches every future change. A note in a doc does not.
- This change does not alter generated functions and adds no `PropertyMappingStrategy`. It touches only docs and config.

## Goals / Non-Goals

**Goals:**
- Two grep commands answer "what's open?" and "is anything unmarked?"
- Nothing in `ROADMAP-draft.md` is lost before the file is deleted.

**Non-Goals:**
- Re-prioritising anything. The draft's order is kept, merged into "Suggested order".
- CI enforcement.
- Editing archived changes.

## Decisions

### D1. One global number sequence; the draft's tiers become sections
The draft's features continue the existing numbering from #28, so every item has exactly one ID, and changes and commits can say `Roadmap: #N` unambiguously.

A new `## Features` section is inserted before `## Suggested order`. Every item stays a `### N.` heading so the D2 grep finds it, which means the tiers can't be headings. Each tier is a bold lead-in paragraph, such as `**Tier 1: parity gaps (target 0.14).** Removes the "falls back to hand-written" moments.`, followed by its `### N.` items.

*Alternative considered:* tier-scoped IDs (`T1-3`). Rejected because a second ID scheme next to `#N` brings back the ambiguity the change exists to remove.

### D2. Heading status grammar
Every item heading has this form:

```
### <N>. <Title> — <size> · <category> · <status>
```

- **size:** one of `S`, `M` or `L`.
- **category:** one of `Bug`, `DX`, `Docs`, `Test`, `CI`, `Feature` or `Nice-to-have`. Today's mixed `S · DX` and plain `S` forms are normalised, and the category comes from the section the item sits in.
- **status:** one of
  - `open`;
  - `partial (<what remains>)`;
  - `done in <version> (<ref>)`, where `<ref>` is `#<PR>` or the OpenSpec change name. The change name is allowed because a docs-sweep task runs before the PR exists, and the archive keeps the name.

Strikethrough and `✅` are removed. They are only formatting, and they break title search.

The file's intro paragraph (today "Items marked ✅ were fixed…") is replaced by the grammar plus two commands:

```
grep -nE '^### [0-9]+\..* · (open|partial)' docs/ROADMAP.md            # what's left
grep -nE '^### [0-9]+\.' docs/ROADMAP.md | grep -vE ' · (open|partial \(.*\)|done in [^ ]+ \(.+\))$'   # must print nothing
```

### D3. Migration mapping (the deletion gate)

| Draft item | Goes to | Status |
|---|---|---|
| Reference frame + "Kraft already wins on" | new `## Positioning` section at the top of `## Features`, keeping the "checked 2026-10-11" date | — |
| T1 #1 Map + other collections | existing **#20**; merge its evidence line | done in 0.14 (#120) |
| T1 #2 Value classes | new **#28** | done in 0.14 (#114) |
| T1 #3, "use target default when source missing" part | existing **#27** | done in 0.14 (#122) |
| T1 #3, `@MapConstant` / expression part | new **#29**; drop the stale "Reserved for future use" evidence and cite `PropertyMappingStrategy.Constant`, now produced for null fills | open |
| T1 #4 `kraft-converters` module | existing **#19**; merge the kotlinx-datetime / `Uuid` / widening list into its scope | open |
| T2 #5–#9 (sealed, fallible, extra params, patch, unmapped policy) | new **#30–#34**, in draft order | open |
| T3 bullets (comparison page + migration guide, build-time benchmark, Swift ergonomics, Kotlin/KSP tracking) | new **#35–#38** | open |
| "Proposed order" | merged into `## Suggested order`. Step 1 is dropped (done). Steps 2–4 are restated with the new numbers: 0.14 has #29 left, 0.15 is #30 and #34, 0.16 is #31–#33 together because they change generated signatures | — |
| Parked bug `.kt.kt` | not migrated, since it's already fixed (#115) | — |
| Parked bug `agp9Test` on Windows | existing **#23**; merge the `testRepo.toURI()` fix hint if it's missing | open |

### D4. Corrections to existing items
- #6: done in 0.14 (#118).
- #3: `partial (opt-out flag)`. Its documentation half is done; the opt-out is still open.
- #27: move from after `## Suggested order` to the end of `## Bugs`.
- "Suggested order": drop the references to finished items (#6 among them) from its "Third" paragraph.
- Existing done items get their `<ref>` from `git log --oneline origin/main` (or `git log -S "<title phrase>" -- docs/ROADMAP.md` when the PR isn't obvious). The ✅ items (#4, #7–#10) were fixed in the audit PR itself, #113.

### D5. Config rules (the `chore:` commit)
Add to `openspec/config.yaml`:
- under `rules.proposal` (a new key): `Name the docs/ROADMAP.md items the change closes or advances as "Roadmap: #N" (or "Roadmap: none") in the Why section`
- under `rules.tasks`, extending the existing docs-sweep rule's intent: `The docs sweep task sets the status of every "Roadmap: #N" item from the proposal to done in <version> (<change-name or #PR>) or partial (<what remains>); never put roadmap updates in Workflow follow-up`

To verify, run `openspec instructions tasks --change single-source-roadmap --json` and check that the new rule appears in `rules`.

## Risks / Trade-offs

- [Deleting an untracked file is unrecoverable] → Deletion is a user gate after the D3 table is checked against the result. The controller first copies the draft to the session scratchpad.
- [Renumbering a heading breaks outside references to it] → No existing number changes. New items only append (#28–#38).
- [Rule wording drifts from the grammar] → The D5 rule text spells out the D2 status forms literally.
- [A huge single-file diff is hard to review] → Two commits: headings and corrections first (pure reformat plus four fixes), then the Features migration.
