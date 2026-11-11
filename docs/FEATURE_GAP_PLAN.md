# Stitch mockup gap plan (all screens)

Written 2026-11-10. Full gap list from comparing every Stitch mockup screen (Capture ×2,
Timeline, Vault, Privacy & Rules, Handwriting, Thought Detail) against the real app. Phased by
value and tractability — each phase is "mockup → build → wire up" in one pass, not design-only.

## Phase 1 — Thought Detail: revision history & audit viewer — DONE (2026-11-10)

Biggest gap: `NoteDetailScreen` today is view+edit only. The audit trail
(`NoteCommandHandler`'s `audit` table) and derived facts (`derived` table, with `Origin`
encoding system/rule/AI provenance) already exist and are fully populated — there was just no
UI reading either. Building:
- **Revision History section**: every audit entry for this note, newest first (data already
  ordered that way), each showing actor/action/relative time and a word-count delta instead of
  a full word-level diff (ponytail: real LCS diffing is a bigger lift than the value justifies
  for v1 — add if "what exactly changed" turns out to matter in practice). A **Revert** button
  on past entries calls the existing `updateText` command with that revision's text — this is
  itself a new audited revision, so revert is automatically append-only/undoable, no new command
  needed.
- **Derived Facts section**: current category/tag/person/place/topic facts for this note, each
  showing its value and a decoded, human-readable origin (system default / which rule / which AI
  run) via the existing `Origin.parse`.
- **Pending AI Suggestions**: any `PENDING` proposals for this specific note, with inline
  Approve/Reject using the existing `MainViewModel.approveProposal`/`.rejectProposal` — same
  commands the dedicated AI Proposals screen uses, just surfaced in context.
- **Copy** button for the note body (clipboard only, no new permission).

Not building (decorative or disproportionate effort): true word-level diff highlighting, "raw
SQLite view", a resurfacing banner duplicating what `ReminderSection` already shows on the same
screen.

## Phase 2 — Vault: search, sort, stats, disambiguation
- Search bar for notebooks/people (Timeline already has one; Vault doesn't).
- Sort toggle for notebooks (recently updated / alphabetical / count / created).
- Per-notebook stats (note count, last-updated) — `CategoryDao`/note queries can support this;
  needs a count query per category.
- Duplicate-person disambiguation ("two people named Dylan?") — real gap: `PersonDao.insertIfAbsent`
  matches by exact name only. Needs a product decision on matching heuristic before building.

## Phase 3 — Privacy & Rules: purge derived facts
"Purge Derived Facts Cache (keeps raw notes)" — wipe `derived`/`proposals` tables without
touching note text. New `NoteCommandHandler`-adjacent command (doesn't touch notes, so may not
need to go through the command handler's audit path — needs a decision on whether this itself
should be audited).

## Phase 4 — Timeline: voice search
Mic icon in the search field, feeding the same on-device `SpeechRecognizer` Voice capture
already uses — transcribe into the search box instead of a new note.

## Phase 5 — Handwriting / Capture (from the earlier list, still open)
Title field on the ink screen, Redo, copy-to-clipboard, share/export the rendered PNG,
auto-recognize after a pause, pressure-sensitive stroke width (Compose's `PointerInputChange`
exposes `.pressure`), lasso selection, insert shapes, guide ruler, dot-grid toggle, true vector
(SVG) export.

## Explicitly not building anywhere
Fabricated telemetry/numbers from the mockups (ink latency ms, SHA-256 hashes, "0B sent"
counters, paper-texture watermarks, "Saved Xs ago" ambient timers) — decorative mockup dressing,
not real features worth faking.
