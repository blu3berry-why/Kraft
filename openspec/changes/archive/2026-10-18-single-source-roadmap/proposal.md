# Proposal

## Why

Kraft's roadmap is split across two files, and the split keeps the feature roadmap stale. `docs/ROADMAP.md` (tracked) holds the audit items. `ROADMAP-draft.md` (untracked) holds the feature tiers. Every workflow that keeps a roadmap current reads only the tracked file: the docs-sweep task rule in `openspec/config.yaml` and the config's own "priorities live in docs/ROADMAP.md".

The feature updates were written as plain `## Workflow follow-up` bullets. Those aren't tracked tasks, so they never got done. Both `map-and-collection-mapping` and `fix-silent-mapper-drop` had "mark it done in the roadmap" bullets, and neither was done.

The 2026-10-18 audit found these stale items:
- **Draft:** Tier 1 #1 and #2 still open after #120 and #114. Tier 1 #3 isn't split into its done and open parts. Tier 1 #3 still cites "`Constant` … Reserved for future use". "Proposed order" step 1 was done 2026-10-11. Two items duplicate `docs/ROADMAP.md` (#19, #23).
- **`docs/ROADMAP.md`:** #6 is shipped (#118) but unmarked. #27 is filed after "Suggested order" instead of under Bugs. Done items use three markers (`✅`, `~~title~~ — done in 0.14`, and #3's mixed form), so "what is still open?" can't be answered with a grep.

## What Changes

- **One roadmap.** The draft's feature tiers, positioning notes and proposed order move into `docs/ROADMAP.md`. They're numbered in the same global sequence and de-duplicated against existing items. Once you've checked the migration, `ROADMAP-draft.md` is deleted.
- **One greppable status per item.** Every `### N.` heading ends with exactly one status:
  - `open`
  - `partial (<what remains>)`
  - `done in <version> (<ref>)`, where `<ref>` is a PR number or an OpenSpec change name.

  Strikethrough and `✅` markers go away. The top of the file gives the grep commands that list open items and catch headings that have no status.
- **Corrections from the audit:** #6 is done (#118), #27 moves under Bugs, and the draft items get their real statuses.
- **The roadmap update becomes a tracked task.** New `openspec/config.yaml` rules:
  - a proposal names the roadmap items it closes;
  - the docs-sweep task (already a checkbox) sets those items' status;
  - a roadmap update must never be a Workflow follow-up bullet.

  This lands as its own `chore:` commit.

Out of scope:
- Automated enforcement (a CI or test check). The grep commands come first; add enforcement only if headings without a status come back.
- Rewriting the text of any item.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- None. This change edits project docs and workflow config only, and Kraft's generated code and diagnostics stay the same. The change sets `skip_specs: true`.

## Impact

- `docs/ROADMAP.md`: restructured headings, a new Features section, corrected statuses.
- `ROADMAP-draft.md`: deleted. It's untracked, so it can't be recovered from git, which is why deletion waits for you to confirm the migration.
- `openspec/config.yaml`: two new rules, one under `proposal` and one under `tasks`. Every future change will see them.
- Archived changes under `openspec/changes/archive/` are left as they are. Their stale follow-up bullets are historical.
