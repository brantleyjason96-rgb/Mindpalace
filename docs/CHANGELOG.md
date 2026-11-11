# CHANGELOG

All notable changes. Dates are YYYY-MM-DD.

## Unreleased — 2026-11-11 — Plan 11 Part A (UI half): date-picker checks, Due/Snooze, notification actions

### Added
- Reminders split into "Due" (fired) and "Upcoming" sections; a Due reminder offers Snooze
  (1 hour / tomorrow 8am) instead of Cancel.
- "Mark done" and "Snooze 1h" notification action buttons — resolved without opening the app.

### Fixed
- The notification-permission callback skipped the exact-alarm check entirely; now re-runs the
  full check. The exact-alarm dialog's second option now actually proceeds (approximate timing)
  instead of only offering Settings.
- A past time was silently accepted; the picker now rejects it inline and keeps the dialog open.
- Reminder times now respect the system's 12/24-hour setting instead of a fixed format, and never
  fall back to showing the raw stored value.

This closes plan 11 Part A (both halves). Test count unchanged (138) — no Compose UI testing
library in the project yet; verified by a full on-device check. See `docs/TEST_RESULTS.md`.

## Unreleased — 2026-11-11 — Plan 11 Part A (backend half): reliable time reminders

### Added
- `ReminderPlanner` — pure function deciding Exact/Inexact/FireNow/Nothing for a reminder.
- `ReminderCommands` — add/cancel/complete/snooze/fire/rescheduleAll in one place; `fire` is
  idempotent and shared between the exact-alarm receiver and the reschedule-on-boot/app-start/
  permission-change paths.
- A new receiver that re-plans every reminder when exact-alarm permission changes in system
  settings; `rescheduleAll` now also runs on `Application.onCreate` (catches app updates and
  anything missed while the app wasn't running).
- A proper monochrome notification icon — the launcher mipmap was rendering as a blank square.

### Fixed
- A denied exact-alarm permission used to leave a reminder silently never scheduled; a past time
  was silently accepted and never fired; reminders never caught up after boot/update/permission
  changes beyond the one case `BootReceiver` already handled.

### Changed
- Schema v3 → v4: `reminders` gains `fire_at`/`fired_at` (backfilled from `spec` for existing TIME
  rows). versionCode/versionName → 6/0.6.0.

Backend only — the UI half (date-picker permission re-checks, past-time rejection, localized time
display, a Due/Snooze section) is a separate unit, not yet done.

### Test count: 138 (was 122). See `docs/TEST_RESULTS.md`.

## Unreleased — 2026-11-11 — Plan 14: migration test framework

### Added
- `core/db/ALL_MIGRATIONS` — the single list `AppContainer` and every test register migrations
  through; nothing else does.
- `MigrationFixtures` (test-only) and seven new `MigrationTest` cases covering a full 1→latest
  migration with data in every table, FTS surviving migration (both old-text search and
  post-migration inserts), a real `Room.databaseBuilder` opening a migrated file, migration-list
  contiguity, schema-file presence, and a source-wide check that destructive fallback is never
  configured.

### Test count: 122 (was 115). See `docs/TEST_RESULTS.md`.

## Unreleased — 2026-11-11 — Plan 01 step 1e: docs brought up to date (closes plan 01)

### Changed
- `CLAUDE.md` and `README.md` rewritten to describe the app as it actually is (UI built and
  running, schema v3, versionCode 5/0.5.0, 115 tests) instead of the 2026-11-08 snapshot.
- `docs/DECISIONS.md`'s D14–D23 reordered to ascending (pure reorder, no content change).
- `MainActivity.kt`'s file header comment corrected (it claimed the UI never writes via DAOs,
  which plan 01's W1 finding shows isn't true yet).

Plan 01 (the audit-and-hardening punch list) is now fully executed, steps 1a through 1e.

## Unreleased — 2026-11-11 — Plan 01 step 1d: crash handling, revert safety, note-detail load, stale swipe

### Fixed
- A command failure (stale revision, note deleted elsewhere, empty capture, or anything else) no
  longer crashes the app — `MainViewModel` reports it through a new `errors` flow; the note detail
  screen shows it as a snackbar.
- Reverting to an earlier revision can no longer blank a note when its snapshot is
  missing/incomplete — the button is disabled instead. Reverting with unsaved edits in the editor
  now asks for confirmation first.
- Opening a note just closed it again on first load (including from a notification tap) — it now
  waits for the actual "not found" answer instead of treating "no data has arrived yet" as
  "deleted". A screen rotation after tapping a reminder notification no longer re-navigates to
  that note if you'd already moved on; opening a note from a notification no longer leaves it
  hidden behind whatever other screen (Trash, Rules, …) happened to still be open.
- Swiping the same note across two status changes in a row (e.g. Inbox → Active → Done) only
  applied the first one; the second repeated it.

### Test count: 115 (unchanged — no Compose UI testing library in the project yet; verified by
compile + full suite + manual trace). See `docs/TEST_RESULTS.md`.

## Unreleased — 2026-11-11 — Plan 01 step 1c: search sanitizer, relevance ranking, LIKE fallback

### Added
- `FtsQuery.sanitize(query, prefixLast = true)` — prefix matching on the last token ("oat" finds
  "oatmeal"), a 12-token/64-char cap, and control-character stripping.
- `NoteSearch.search(noteDao, query)` — the search entry point the app should call: sanitized,
  relevance-ranked (`bm25`), falls back to `searchLike` on a database exception. New
  `NoteDao.searchFtsRanked` and `NoteDao.observeDeleted` (a live Trash query, not yet wired into
  the UI — that's plan 07).

### Fixed
- `NoteDao.searchLike`'s LIKE fallback query had no `ESCAPE` clause, so a literal `%`/`_` the
  caller tried to escape was never actually treated literally by SQLite. Added `ESCAPE '\'`.

### Test count: 115 (was 107). See `docs/TEST_RESULTS.md`.

## Unreleased — 2026-11-11 — Plan 01 step 1b: undo redesign, optimistic concurrency, updateTitle

### Changed
- `NoteCommandHandler.undo` redesigned: restores the recorded `before` snapshot only if the note's
  revision still matches the audited action's `after` revision; otherwise throws the new
  `UndoStaleException`. RESTORE and a previous UNDO are now undoable (previously refused outright);
  undo-of-undo is redo.
- `updateRawText`, `updateTitle`, `setStatus`, `softDelete`, and `restore` accept an optional
  `expectedRevision` for optimistic concurrency (`NoteConflictException` on mismatch).

### Added
- `updateTitle(noteId, newTitle, actor, expectedRevision)` — there was no title edit before.
  Audited as the new `AuditAction.UPDATE_TITLE`, undoable through the same path as any other edit.

### Test count: 107 (was 98). See `docs/TEST_RESULTS.md`.

## Unreleased — 2026-11-11 — Plan 01 step 1a: share hardening, soft-delete idempotency

### Fixed
- Share target no longer silently drops styled-text shares (`getCharSequenceExtra` instead of
  `getStringExtra`), no longer crashes on a save failure (toasts instead), and now toasts when
  there is genuinely nothing to save.
- `ShareIngest` no longer mistakes "a URL followed by a newline and more text" for a link-only
  share; a single URL elsewhere in the text is still recorded as `source_url`; shared raw text is
  stored untrimmed.
- `NoteCommandHandler.softDelete` is idempotent (deleting an already-deleted note is a no-op, not
  a thrown `NoteConflictException`); a genuine concurrent-edit race still throws.

### Added
- `NoteSnapshot` carries `imagePath`/`attachmentPath`/`attachmentFileName` (audit trail now
  records which file an ATTACHMENT/PHOTO note's entry refers to).

### Test count: 98 (was 91). See `docs/TEST_RESULTS.md`.

## Unreleased — 2026-11-11 — Plan 02 §5 step 1: baseline untagged tests

### Added
- ~40 new unit tests from `docs/plans/02-missing-tests.md` §3 (the tests that should already pass
  on today's code): undo-of-soft-delete, no-op edits, soft-deleted-note guards, audit snapshot
  contents, proposal approval for CATEGORY/TAG/TOPIC/PLACE, stale-revision guards, FTS behavior
  (edits, restore, transcript/ocr columns, case/diacritic folding, integrity check), a foreign-key
  enforcement probe, export edge cases (empty DB, unicode, null fields, unknown-key decoding), and
  new suites `FtsQueryTest`, `OriginTest`, `AiProposalValidatorTest`, `ShareReceiverActivityTest`,
  `ManifestComponentsTest` (regression guard for the D25 wrong-path crash).
- Two known bugs (A3, G1 in `docs/plans/01-audit-punch-list.md`) recorded as `@Ignore`d failing
  tests rather than silently left uncovered.

### Fixed
- Stray word "quilks" in `NoteFtsEntity`'s KDoc (plan 01 Z2.12).

### Test count: 91 (was 49). See `docs/TEST_RESULTS.md`.

## [0.1.0] — 2026-11-08 — Phase 0 complete + Phase 1 backend core

### Added
- Project scaffold: AGP 9.5.1 / Gradle 9.9.0 / Kotlin 2.3.21 (built-in Kotlin) / minSdk 33 /
  target+compile 37; version catalog; wrapper. Single-module, packages per plan §5.
- Room3 3.0.3 database "mind_palace.db", **schema v1 with all §6 tables** (notes + FTS5
  external-content index, derived, topics, note_links, reminders, people, places, rules, ai_index,
  proposals, ai_runs, audit, jobs). Schema exported & committed for future migration tests.
- `NoteCommandHandler` — the only writer of canonical records: save-first single-transaction
  create (status INBOX, derived CATEGORY=Inbox origin=system, revision 1), revision-guarded
  raw-text/status edits, soft delete + restore, **audit trail with before/after snapshots and
  undoable every action** (§3.7).
- FTS5 search over title/raw_text/transcript/ocr_text with a hostile-input sanitizer and a LIKE
  fallback (§15 mitigation).
- Share ingestion: pure `ShareIngest` mapper + UI-less `ShareReceiverActivity`
  (ACTION_SEND text/plain → Inbox, records source app, link-only shares stored as link+title).
- Exports: JSON (round-trippable, v1 schema) and Markdown, written to the app-private dir only.
- Privacy gate tests: merged manifest must contain **no permissions** (INTERNET/network-state
  explicitly asserted; androidx core's app-private receiver permission is the one documented
  exception). `allowBackup=false`.
- Placeholder capture UI (single screen; to be replaced by the frontend session).
- Docs: README (build/run/Checkpoint-0 checklist), DECISIONS (D1–D12), TEST_RESULTS (27/27 green).

### Verified
- `./gradlew testDebugUnitTest`: 27/27 green on this Mac (Robolectric + real bundled SQLite/FTS5).
- `./gradlew assembleDebug`: debug APK builds.
- Room3 schema compiles (KSP) for every table + DAO, including the FTS5 entity.

### Not yet verified (owner on-device)
- Checkpoint 0 device checklist (see README). Do not start Phase 1 UI work before it passes.
