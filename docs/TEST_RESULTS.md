# TEST RESULTS

## 2026-11-11 — Plan 11 Part A, UI half — device check PASSED (SM-S948U, installed over existing app)

Past-time rejection shows an inline error and keeps the picker open; a valid time saves and
displays with correct locale formatting ("Wed, Nov 11, 6:55 PM" — no raw ISO `spec` shown); the
reminder fired with both "Mark done"/"Snooze 1h" notification action buttons present; tapping
"Snooze 1h" directly from the notification (screen locked, app never opened) re-armed the
reminder exactly +1h with `fired_at` cleared — confirmed by reopening the app afterward and
seeing "Wed, Nov 11, 7:56 PM" with Cancel (not Snooze) actions again, i.e. no longer "Due".
`./gradlew testDebugUnitTest assembleDebug` — 138 tests (unchanged), 0 failures. No schema
change. This closes plan 11 Part A (both halves). See D35 in `docs/DECISIONS.md`.

## 2026-11-11 — Plan 11 Part A device check — PASSED (SM-S948U, installed over existing app)

A reminder set ~4 minutes out fired correctly while the phone was locked (correct note text,
posted exactly once per `dumpsys notification`); the v3→v4 migration preserved the existing
note and its full revision history; the notification icon renders as a real glyph, not a blank
square. Two of the plan's five device checks (reboot catch-up; inexact fallback when exact
alarms are off) weren't run this pass — see `docs/CHECKPOINT_0.md`'s "Reliable time reminders"
section for what's still open. Merged to `main` (`9504f63`).

## 2026-11-11 — Plan 11 Part A, backend half (reliable time reminders) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **138 tests (was 122, +16), 0 failures, 0 errors, 2 skipped**
(same two pre-existing `@Ignore`d bug records). `assembleDebug` also green. Schema v3 → v4
(`reminders.fire_at`/`fired_at`); permission list unchanged.

Added (D34 in `docs/DECISIONS.md`): `ReminderPlanner` (pure decision function: Exact/Inexact/
FireNow/Nothing), `ReminderCommands` (add/cancel/complete/snooze/fire/rescheduleAll — the one
place reminder writes and scheduling now happen), a monochrome notification icon (the launcher
mipmap was rendering as a blank square), and a new manifest receiver that re-plans reminders when
the user changes the exact-alarm permission in system settings. **This is the backend half only**
— the UI half (re-checking permissions on every path into the date picker, rejecting a past time
inline, localized time formatting, a "Due"/Snooze section) is a separate unit with no automated
test coverage, still to come, needing a device check before merge (same situation as plan 01 step
1d).

## 2026-11-11 — Plan 14 (migration test framework) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **122 tests (was 115, +7), 0 failures, 0 errors, 2 skipped**
(same two pre-existing `@Ignore`d bug records). `assembleDebug` also green. No schema change.

Added (D33 in `docs/DECISIONS.md`): `ALL_MIGRATIONS` as the single migration-registration point;
`MigrationFixtures` (seeds one row per schema-v1 table, raw SQL); and seven tests —
migrating 1→latest keeps every row and the original text verbatim, FTS still finds pre-migration
text and passes an integrity check, FTS triggers fire for a post-migration insert, a real
`Room.databaseBuilder` opens the migrated file, the migration list is contiguous and current, a
schema file exists for every version, and `fallbackToDestructiveMigration` appears nowhere in the
source.

## 2026-11-11 — Plan 01 step 1e (stale docs brought up to date) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **115 tests (unchanged), 0 failures, 0 errors, 2 skipped**.
`assembleDebug` also green.

Docs-only step (plan 01 Z1): `CLAUDE.md` and `README.md` rewritten to describe the app as it
actually is now (UI built and running, schema v3, versionCode 5/0.5.0, 115 tests, D31 latest/
next D32) instead of the 2026-11-08 snapshot they still described. `docs/DECISIONS.md`'s D14–D23
entries reordered to ascending (they were written out of order as decisions landed); no content
changed, pure reorder. `MainActivity.kt`'s file header comment corrected — it claimed "the UI
never touches DAOs for writes," which plan 01's W1 finding shows isn't true for several
vocabulary/rules/reminder writes.

## 2026-11-11 — Plan 01 step 1d device check — PASSED (SM-S948U, installed over existing app)

All four manual checks passed: command-error snackbar, revert-with-unsaved-edits confirmation,
notification-open-after-rotation (no stale re-navigation, not hidden behind another screen), and
two status-advancing swipes on the same note both applying. Merged to `main` (`78fe013`).

## 2026-11-11 — Plan 01 step 1d (crash handling, revert safety, note-detail load, stale swipe) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **115 tests (unchanged), 0 failures, 0 errors, 2 skipped**.
`assembleDebug` also green. No schema change. No new automated tests this step (no Compose UI
test library in the project yet — `docs/plans/INDEX.md` OD-10) — verified by compile + full
unit suite + manual trace against each bug's repro steps. **Owner should exercise on-device**:
a command error showing a snackbar, revert-with-unsaved-edits confirmation, opening a note from
a notification after rotating, and two status-advancing swipes in a row on the same note.

Fixes in this step (D31 in `docs/DECISIONS.md`): `MainViewModel` now wraps every write in a
`launchCommand` helper that reports failures via a new `errors` flow instead of crashing;
`NoteDetailScreen` shows them as a snackbar. `RevisionHistorySection`'s Revert button is disabled
when there's nothing safe to revert to, confirms before discarding unsaved edits, and marks
"current" by matching saved text instead of trusting the newest audit row's position.
`NoteDetailScreen` loads via a new live single-note query (`NoteLookup`) instead of treating an
empty not-yet-populated list the same as "deleted". `MainActivity.onCreate` consumes the
notification intent extra once and clears every screen flag, not just two of them.
`SwipeableNote`'s drag gesture reads the note via `rememberUpdatedState` so two status-advancing
swipes in a row both apply.

## 2026-11-11 — Plan 01 step 1c (search sanitizer, ranking, LIKE fallback) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **115 tests, 0 failures, 0 errors, 2 skipped** (same two
`@Ignore`d bug records: A3, G1). `assembleDebug` also green. No schema change.

Fixes in this step (D30 in `docs/DECISIONS.md`): `FtsQuery.sanitize` adds prefix matching on the
last token, a 12-token/64-char cap, and control-character stripping that doesn't eat legitimate
whitespace separators. New `NoteSearch.search` ranks FTS5 hits by relevance (`bm25`) and falls
back to `searchLike` on a database exception. `searchLike`'s LIKE fallback query was missing an
`ESCAPE` clause entirely — its "escape %/_ literally" contract was silently broken before this
fix, since SQLite's `LIKE` has no default escape character.

## 2026-11-11 — Plan 01 step 1b (undo redesign, optimistic concurrency, updateTitle) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **107 tests, 0 failures, 0 errors, 2 skipped** (same two
`@Ignore`d bug records: A3, G1). `assembleDebug` also green. No schema change.

Fixes in this step (D29 in `docs/DECISIONS.md`): `undo()` redesigned to restore the exact
`before` snapshot only when the note's revision still matches the audited action's `after`
revision (stale otherwise, `UndoStaleException`) — RESTORE and a previous UNDO are now undoable,
and undo-of-undo is redo. `updateRawText`/`updateTitle`/`setStatus`/`softDelete`/`restore` all
accept an optional `expectedRevision` for optimistic concurrency. New `updateTitle` method
(previously didn't exist).

## 2026-11-11 — Plan 01 step 1a (share hardening, soft-delete idempotency, snapshot fields) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **98 tests, 0 failures, 0 errors, 2 skipped** (same two `@Ignore`d
bug records from the previous step: A3, G1). `assembleDebug` also green (debug APK built).

Fixes in this step (see D28 in `docs/DECISIONS.md` for detail): `ShareReceiverActivity` reads
styled text correctly and never silently drops a share; `ShareIngest`'s link detection now
catches a URL followed by a newline (not just a space); shared raw text is stored untrimmed;
`NoteCommandHandler.softDelete` is idempotent instead of throwing on an already-deleted note;
`NoteSnapshot` now carries `imagePath`/`attachmentPath`/`attachmentFileName`.

## 2026-11-11 — Plan 02 §5 step 1 (baseline untagged tests) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`
**Result:** `BUILD SUCCESSFUL` — **91 tests, 0 failures, 0 errors, 2 skipped**
(`@Ignore`d bug records: T-PR3 for A3, T-ORD1 for G1 — kept on record per plan 02).
Run locally on the owner's Mac (unlike the cloud-planning session, this one has the Android SDK
and a JDK via Android Studio's bundled JBR, so tests ran directly instead of waiting on CI).

Added this step: every untagged test from `docs/plans/02-missing-tests.md` §3 that was feasible
without new production code — `NoteCommandHandlerTest` (undo-of-soft-delete, no-op edits,
soft-deleted-note guards, audit snapshot contents, approve-CATEGORY/TAG/TOPIC/PLACE, approve-on-
deleted-note), `NoteDaoTest` (stale-revision guards, FTS follows edits/restore/transcript/ocr/
case-folding, FTS integrity check, foreign-key enforcement probe), new `FtsQueryTest` (sanitizer
unit tests), new `OriginTest`, new `AiProposalValidatorTest`, new `ShareReceiverActivityTest`
(non-SEND intent only — the SEND-intent cases are tagged (1a) and land with that step), new
`ManifestComponentsTest` (D25 regression guard), and four new `NoteExporterTest` cases.

Found during this pass (not yet fixed — tracked by `docs/plans/01-audit-punch-list.md`):
foreign keys ARE enforced (Room enables them by default), but the violation surfaces as a plain
`android.database.SQLException`, not `SQLiteConstraintException` — worth a note if a later plan
catches by type. `ManifestComponentsTest` had to allow-list several androidx-library components
(WorkManager's job service/diagnostics receiver, profileinstaller's install receiver, Compose
preview tooling, androidx.test instrumentation activities) that are exported by the libraries
themselves, not by this app.

## 2026-11-08 — Phase 0 + Phase 1 backend — ALL GREEN (local JVM)

**Command:** `JAVA_HOME=<AS JBR 25> ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **27 tests, 0 failures, 0 errors, 0 skipped**, debug APK built,
room3 schema v1 exported to `app/schemas/`.

| Suite | Tests | Failures | Covers |
|---|---|---|---|
| PrivacyManifestTest | 3 | 0 | merged manifest: no INTERNET, no ACCESS_NETWORK_STATE, no unknown permissions |
| ShareIngestTest | 6 | 0 | verbatim text, link-only → source_url, subject→title, empty→null, link-with-space ≠ link |
| NoteDaoTest | 7 | 0 | insert/fetch, revision-guard rejects stale writes, soft-delete/restore, **FTS5 search across columns (multi-token AND)**, hostile-input FTS safety, deleted excluded from search, LIKE fallback |
| NoteCommandHandlerTest | 8 | 0 | create seeds INBOX + audit; empty capture throws & writes nothing; verbatim original; **edit→undo restores previous text (rev 3)**; undo-of-create soft-deletes then restore; status undo; missing note throws; share note keeps source URL/app/title |
| NoteExporterTest | 3 | 0 | JSON export **round-trips** (decode ↔ payload), excludes deleted; markdown readable; file writes to private dir |

**FTS5 note:** tests run the real SQLite with FTS5 (BundledSQLiteDriver + `sqlite-bundled-jvm`
desktop natives) — the same engine ships in the APK, so search behavior is identical on-device.

**Apk:** `app/build/outputs/apk/debug/app-debug.apk` (~36 MB; bundling real SQLite ≈ +10 MB — D3).

## Verified on-device? NOT YET — Checkpoint 0 is Jason's

The following Checkpoint 0 items **cannot be verified from this Mac** (no phone attached) and are
still required before Phase 1 starts:
- install + launch on the S26 Ultra;
- typed note survives force-kill and reboot;
- capture-to-saved time (target < 2 s);
- end-to-end share sheet path (toast + note visible);
- airplane-mode behavior (guaranteed by design: no INTERNET permission at all).

Re-run locally anytime:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest
```

Fixes discovered during this run (kept for the record): room3 has no room2-style
`withTransaction` (→ `withWriteTransaction`); kotlinx.serialization builder property is
`ignoreUnknownKeys`; JUnit `assertThrows` can't host suspend calls (→ try/fail/catch);
BundledSQLiteDriver needs the JVM-variant artifact on the test classpath (D3).
