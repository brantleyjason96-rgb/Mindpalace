# Plan 01 — Audit and hardening punch list

**Audited version:** branch `claude/happy-fermi-begmf3` at `d851331` (schema v3, versionCode 5),
merged into this branch. This replaces an earlier audit of `main`, which was three days and
fourteen commits out of date.
**Read in full:** `core/commands`, `core/db`, `core/model`, `core/rules`, `core/reminders`,
`core/ink`, `core/ocr`, `ai/*`, `capture`, `export`, `MindPalaceApplication`, the manifest, build
files, all eight test suites, and `MainActivity.kt` (4,533 lines).
**Verified by running:** the search findings (S1, S2) were reproduced against real SQLite FTS5
with this schema's table and triggers. Nothing else was executed; the app cannot be built in a
Claude cloud session (`06-ci.md`).
**Limit:** the original build plan is not in the repo. "Gaps against the plan" means gaps against
the rules in `CLAUDE.md`, `DECISIONS.md`, and the plan fragments quoted in code (`docs/PLAN.md`).

Priority: **P0** = can lose the owner's input or data, or a feature silently does not work.
**P1** = wrong behavior or a broken stated rule. **P2** = cleanup.

## Summary

| ID | P | Area | Problem |
|---|---|---|---|
| R1 | P0 | reminders | A reminder can be saved as active and never fire (three separate ways) |
| U1 | P0 | commands | Undo of an old change overwrites newer text |
| V1 | P0 | revert | Revert can blank a note; it also discards unsaved edits |
| X1 | P0 | ui | Any command error crashes the app (no error handling on 22 launches) |
| C1 | P0 | share | Styled-text shares are dropped silently; a failed save crashes |
| N1 | P0 | ui | Note detail closes itself on first load, so notification taps open nothing |
| E1 | P0 | export | Export drops most data and there is no import; backups are off |
| B1 | P0 | build | Installing from another machine forces an uninstall, which deletes all notes |
| A1 | P1 | ai | A crashed run stays "RUNNING"; notes edited during a run are skipped |
| A2 | P1 | ai | Rejected proposals come back; repeat runs duplicate proposals |
| A3 | P1 | ai | Approving a proposal can create duplicate people, places, and topics |
| A4 | P1 | ai | Two runs at once share one native engine with no lock |
| A5 | P1 | ai | Model copy failure leaves a broken model in place; no file check |
| G1 | P1 | rules | Rule order is random (sorted by UUID); rules run only at capture |
| G2 | P1 | rules | "Which category wins" is undefined; manual category (owner-decided) not built |
| W1 | P1 | writes | Eleven ViewModel functions write through DAOs; none audited or undoable |
| H1 | P1 | commands | Screens cannot pass the revision they edited; stale saves overwrite |
| H2 | P1 | commands | No title edit; `softDelete` reports the wrong exception |
| S1 | P1 | search | No prefix matching; partial words find nothing |
| S2 | P1 | search | A NUL character in the query throws, and nothing catches it |
| S3 | P1 | search | `LIKE` fallback is never used; open search and Trash go stale |
| D1 | P1 | db | Every screen loads every note with full text |
| M1 | P1 | capture | Drawing and photo are lost on rotation; unsaved files are orphaned |
| M2 | P1 | capture | Voice: nothing is kept if recognition fails; permission denial dead-ends |
| C3 | P1 | share | URL followed by a newline is stored as a "link"; shared text is trimmed |
| T1 | P1 | ui | Stale swipe state re-sends the old status |
| P1 | P1 | privacy | With `INTERNET` granted, ML Kit's usage reporting can now reach Google |
| Y1 | P1 | a11y | Notes cannot be opened or swiped with TalkBack (plan 16) |
| Z1 | P2 | docs | `CLAUDE.md`, `README`, `TEST_RESULTS`, `CHANGELOG` describe an older app |
| Z2 | P2 | various | Cleanup list |

## P0 details

### R1 — Reminders that never fire
1. `AlarmScheduler.schedule` returns silently when exact alarms are not allowed. The row is
   already saved as `ACTIVE`. The notification-permission callback goes straight to the date
   picker and skips the exact-alarm check (`MainActivity.kt` 2480–2484).
2. A past time is accepted (2617–2626) and never scheduled.
3. If the phone is off at the reminder time, `BootReceiver` skips it (time is in the past). It
   stays `ACTIVE` forever and never shows.

Also: a reminder on a soft-deleted note still fires; a denied notification permission drops the
notification without a trace; `R.mipmap.ic_launcher` as the small icon usually renders as a blank
square. Before D25 the receivers were declared at the wrong path, so **no reminder notification
had ever fired**; the owner has confirmed boot no longer crashes, but a reminder firing end to end
is not recorded as verified anywhere.
**Fix:** `11-reminders-geofences-calendar.md` Part A.

### U1 — Undo overwrites newer text
`NoteCommandHandler.undo(auditId)` re-applies the old `before` text whatever happened since
(edit A→B, edit B→C, undo the first → A). RESTORE and UNDO rows cannot be undone although the
KDoc says they can. A `NULL` text is restored as `""`. No UI calls `undo` today, but revert (V1)
has the same flaw and plan 07 wires undo in.
**Fix (design every later plan uses): undo = restore the `before` snapshot only if nothing
changed since.**
- Load the audit row and the note. If `note.revision != after.revision`, throw
  `UndoStaleException(noteId, auditId)`.
- `before == null` (a CREATE) → soft delete. Otherwise one guarded update restores `title`,
  `raw_text`, `status`, `deleted_at` from `before`:
  ```sql
  UPDATE notes SET title = :title, raw_text = :rawText, status = :status,
         deleted_at = :deletedAt, updated_at = :now, revision = revision + 1
  WHERE id = :id AND revision = :revision
  ```
  (`NoteDao.restoreSnapshotGuarded`, nullable `rawText`, no `deleted_at` condition.)
- Every undo writes one audit row with `action = UNDO`.
- Result: all six actions are undoable, undo of an undo is redo, a second undo of the same row
  fails as stale, and `NULL` stays `NULL`.

### V1 — Revert
`RevisionHistorySection` (2248–2272) reverts by calling `updateText` with an old snapshot's text.
A missing snapshot falls back to `""`, which **overwrites the note with nothing** (2265). Revert
replaces unsaved edits in the editor (2163) and has no confirmation. "Current" is the newest
audit row of any kind, not the newest text change (2263).
**Fix:** if the snapshot or its text is missing, disable Revert for that row. Confirm before
reverting when the editor is dirty. Mark "current" by comparing the row's text with the note's
text.

### X1 — No error handling in the ViewModel
None of the 22 `viewModelScope.launch` blocks in `MainViewModel` has a `try/catch`. The handler
throws `NoteConflictException`, `NoteMissingException`, `EmptyCaptureException`,
`IllegalArgumentException`, and `UnsupportedOperationException`. Example: tap Undo on a status
snackbar after deleting that note → crash. The capture screens set `saving = true` and never
reset it on failure.
**Fix:** one helper in the ViewModel:
```kotlin
private fun launchCommand(onError: (UiError) -> Unit = ::report, block: suspend () -> Unit)
```
It catches `Exception`, maps it to a `UiError` (`Conflict`, `Missing`, `Empty`, `Unknown`), and
emits it on `val errors: SharedFlow<UiError>`; the root composable shows a snackbar. Every write
uses it. Captures keep the user's text on screen until the save succeeds.

### C1 — Share target
`ShareReceiverActivity` is unchanged since v0.1. `getStringExtra(EXTRA_TEXT)` returns `null` for
styled text (`SpannableString`), so the share is discarded with no message. `runBlocking {
create }` is not wrapped; a database error crashes and the text is gone.
**Fix:** `getCharSequenceExtra(...)?.toString()`; `try/catch` with a "Could not save — copy your
text and try again" toast; a "Nothing to save" toast when the share is empty.

### N1 — Note detail closes on first load
`NoteDetailScreen` looks the note up in `allNotes`, which starts as an empty list; the
`LaunchedEffect` then calls `onBack()` (2056–2064). A reminder notification starts a fresh
activity, so tapping it most likely opens and immediately closes the note. The same happens when
Android restores the app after killing it. `onCreate` also re-reads the notification extra on
every rotation (654), and the note can open hidden behind Settings or Trash (681–688).
**Fix:** load the note with `noteDao.observeById(id)`; distinguish "loading" from "missing";
consume the intent extra once (`intent.removeExtra`); clear every screen flag when opening from a
notification. Structural fix in plan 07.

### E1 — Export and import
The share sheet now delivers the export file (good). But `NoteExporter` still writes only the
note columns from schema v1: no categories or tags (`derived`), no rules, reminders, links,
people, places, topics, proposals, audit history, deleted notes, photos, drawings, or attached
files. Nothing imports. With `allowBackup=false` this is the only route to a new phone, and it
would carry bare text only.
**Fix:** `15-export-import.md`.

### B1 — Signing key
Debug builds are signed per machine. An APK from another machine (or CI) cannot update the
installed app; Android demands an uninstall, **which deletes the database, photos, and model**.
**Fix:** owner decision OD-2 in `INDEX.md`; `scripts/checkpoint0.sh` never uninstalls.

## P1 details

### A1–A5 — Night loop (`ai/`)
- **A1.** `AiNightRunner.run` has no `catch`. An exception or a cancelled worker leaves the
  `ai_runs` row at `RUNNING` forever. `since` is the last run's **finish** time, so a note edited
  while a run was in progress is never picked up; it must be the run's **start** time. The
  thermal check runs once at the start, not between notes. `FAILED_LOAD` returns `retry`, so a
  bad model file retries forever. The `jobs` table (the lease design in D13) is unused.
- **A2.** Nothing checks existing proposals. "Run now" twice duplicates every pending proposal,
  and a rejected proposal returns after the next edit (against `PLAN.md` §8). `ai_index` rows
  pile up per run.
- **A3.** `approveProposal` calls `insertIfAbsent` with a **new UUID** each time. The primary key
  is the id and `name` has a non-unique index, so "if absent" is always true: approving "Dylan"
  twice makes two people named Dylan. Same for places and topics, and for `addTopic`/
  `addPerson`/`addPlace` in the ViewModel. A model-invented category is written without
  checking the `categories` table. `rejectProposal` does not check the state, so an approved
  proposal can be flipped to rejected while its fact stays. Approval cannot be undone
  (`ProposalState.UNDONE` is unused).
- **A4.** `AiEngine` is a singleton with plain booleans. The nightly worker and "Run now" use
  different unique work names and can overlap; two loads into one native context will crash.
- **A5.** `AiSetupScreen` copies the picked file over the existing model with no validation; a
  failed copy leaves a partial file and a spinner that never stops (2868–2877).

**Fix:** `12-on-device-ai-night-loop.md`.

### G1, G2 — Rules
- `RuleDao.enabled()` is `ORDER BY id`, and ids are random UUIDs, so "first match wins" picks an
  arbitrary rule. Matching is substring (`art` matches `start`). Rules run only inside `create`;
  editing a note or adding a rule never re-evaluates. Deleting a rule leaves its `derived` rows.
  Existing rules cannot be edited.
- The system `Inbox` row is never removed and D16 leaves "which category wins" open. The Vault
  counts every row, so a filed note is counted under both Inbox and its category. The owner
  decided (D25) that a hand-picked category is a permanent override; no command or UI exists.

**Fix:** `10-rules-engine.md`.

### W1 — Writes outside the command handler
`MainViewModel` writes through DAOs in `addCategory`, `addTopic`, `addPerson`, `addPlace`,
`addLink`, `addReminder`, `cancelReminder`, `completeReminder`, `setRuleEnabled`, `addRule`,
`deleteRule` (lines 311–445). The file header says the UI never does this. D18/D19 accepted it
for vocabulary and reminders; it still breaks `CLAUDE.md` rule 3 as written, and none of it is
audited or undoable. `AiNightRunner` also writes `proposals`, `ai_index`, `ai_runs` directly,
which is by design.
**Fix:** move each write behind a small command class in `core/commands` as its plan touches it
(rules → plan 10, reminders → plan 11, vocabulary and links → plan 07 step 3). Then add
`ArchitectureTest`: no file outside `core/commands`, `core/db`, and `ai/AiNightRunner.kt` may
call a DAO write method. Record the final rule wording in `DECISIONS.md`.

### H1, H2 — Command handler
- Handler methods re-read the note and use that revision, so the guard covers microseconds only.
  **Fix:** `expectedRevision: Int? = null` on `updateRawText`, `updateTitle`, `setStatus`,
  `softDelete`, `restore`; mismatch throws `NoteConflictException`. The editor passes the
  revision it loaded.
- There is no `updateTitle` (the ViewModel comment says so). Add it with `AuditAction.UPDATE_TITLE`.
- `softDelete`: an already-deleted note throws `NoteConflictException`; a lost race throws
  `NoteMissingException`. Both are wrong. Already deleted → return; lost race → conflict.
- `NoteSnapshot` lacks `imagePath`, `attachmentPath`, `attachmentFileName`. Add them with
  `null` defaults (old JSON still decodes).

### S1–S3 — Search
Reproduced on SQLite FTS5:

| Input | Sanitized | Result |
|---|---|---|
| `oa` (note has "oat") | `"oa"` | no match — **S1** |
| `"oa"*` written by hand | — | matches "oat", "oatmeal" |
| a query containing U+0000 | quoted as is | **throws** `unterminated string` — **S2** |
| `-`, `( )`, `***`, `+` | quoted | no rows, no error |
| `AND`, `NEAR`, `title:x` | quoted | treated as plain words |
| `cafe` (note has "café") | `"cafe"` | matches |
| `地震` (note has "地震波の観測") | `"地震"` | no match (tokenizer limit; recorded, not fixed) |

`results` has no `catch`, so S2 ends the search flow. `searchLike` is never called.
`highlightMatches` highlights the whole query as one string while search is token-AND. `results`
and `trash` re-query only when the ViewModel bumps `refresh`, so a share, a rule, or the night
loop does not update an open search.
**Fix:** `FtsQuery.sanitize(raw, prefixLast = true)` strips characters below U+0020 and quotes,
caps at 12 tokens of 64 chars, and appends `*` to the last token. New `NoteSearch.search(query)`
orders by `bm25(notes_fts)`, catches `SQLiteException`, and falls back to `searchLike` with
`%`, `_`, `\` escaped. Make search a `Flow` query so Room re-runs it on any table change;
replace `trash` with `noteDao.observeDeleted(limit)`.

### D1 — Loading everything
`allNotes = noteDao.observeActive()` is an unbounded `SELECT *`, collected by six screens;
Settings uses it only for a count. The link picker lowercases every note's text on each
keystroke on the main thread (2423–2434). Photos are decoded at full size on the main thread
(4056, 4378).
**Fix:** a `NoteListItem` projection with a 240-character preview and a limit;
`observeById`; `countActive` as a `Flow`; sampled, off-main-thread image decoding. Details in
plans 07 and 17.

### M1, M2 — Capture
- Handwriting strokes and the photo file reference are plain `remember`; rotation or a font-size
  change loses the drawing or the photo. Files are written before save and never cleaned up when
  the user backs out. An unreadable picked file is saved as an empty attachment.
- Voice keeps no audio (D15). A failed recognition leaves nothing. The transcript is shown as
  read-only text, so "review before save" cannot correct it. After one denial the screen shows
  text only, with no retry or settings link. Every recognizer error shows the same message.

**Fix:** plans 08 and 09.

### C3 — `ShareIngest`
`linkOnly` tests for a space only, so `"https://a.org\nmy notes"` is stored whole as
`source_url`. Use `none { it.isWhitespace() }`. Store the shared text untrimmed (rule 4); trim
only for the emptiness and link checks. If the text contains exactly one `https?://\S+`, set
`source_url` to it.

### T1 — Swipe
`pointerInput(note.id, canAdvance)` captures the note. After INBOX → ACTIVE the keys do not
change, so the next swipe computes from the old status and sends ACTIVE again (1627–1638).
**Fix:** read the status through `rememberUpdatedState(note)` inside the gesture.

### P1 — Network permission side effect
D17 removed `INTERNET` and `ACCESS_NETWORK_STATE` because ML Kit's libraries include a usage
reporting component (`transport-backend-cct`). D26 restored both permissions for the handwriting
model download. Android permissions are per app, not per library, so that reporting component can
now upload its usage statistics to Google. It does not send note text. The in-app "100% on-device
and private" wording is no longer exact.
**Not verified:** whether uploads actually happen; that needs a traffic capture on the phone.
**Options:** owner decision OD-4 in `INDEX.md`.

## P2

### Z1 — Stale documents
| File | Says | Actually |
|---|---|---|
| `CLAUDE.md` | "read D1–D14", "next ID: D15", "27/27", "UI has never been compiled", lists built screens as to-do | D26 is the latest; next is D27; 49 `@Test` methods; UI runs on the phone |
| `README.md` | 27 tests, "what's next" lists built features | as above |
| `docs/TEST_RESULTS.md` | last entry 2026-11-08, 27 tests | D-entries mention 45 and 50; the count in the source is 49 |
| `docs/CHANGELOG.md` | stops at 0.1.0 | versionCode 5, versionName 0.5.0 |
| `docs/DECISIONS.md` | entries out of order (D13, D21, D20, D19, …) | sort ascending |
| `MainActivity.kt` header | "UI never writes via DAOs" | see W1 |

### Z2 — Cleanup
1. `OcrEngine` never closes its recognizer. It uses the Latin-script model only.
2. `rules`, `people`, `places`, `topics`, `links`, `reminders` rows are hard-deleted or never
   deletable; decide per table in the owning plan.
3. Old export files are never pruned (they contain all notes in plain text).
4. Schema JSON files ship inside the APK as assets (plan 14 §2.4).
5. `gradle-wrapper.properties` has no `distributionSha256Sum`.
6. `isMinifyEnabled = false`; no release signing.
7. Voice, photo, and ink "Saved" snackbars never show (the screen navigates away first).
8. `AiNightRunner` KDoc still says the WorkManager wrapper is "not built yet".
9. `ShareReceiverActivity` is exported with no restriction; any app can add notes. Accepted by
   design; write it down.
10. No `android:dataExtractionRules`. `allowBackup=false` stops cloud backup, but some
    manufacturers still allow cable device-to-device transfer on Android 12+. Decide (OD-3).
11. 70 MB of vendored llama.cpp (3,855 files) is in git, including its web UI and tests. Prune
    to what the CMake build needs, or record why not.
12. Stray word "quilks" in the `NoteFtsEntity` KDoc.

## Execution (for the faster model)

Each step is one commit with its tests from `02-missing-tests.md`. No step changes the schema.

| Step | Items | Files allowed |
|---|---|---|
| 1a | C1, C3, H2 (`softDelete`, snapshot fields), Z2.12 | `capture/ShareReceiverActivity.kt`, `core/commands/*`, `core/model/Model.kt`, `res/values/strings.xml`, tests |
| 1b | U1, H1, H2 (`updateTitle`) | `core/commands/*`, `core/db/Daos.kt`, `core/model/Model.kt`, tests |
| 1c | S1–S3 (backend part) | `core/db/Daos.kt`, `core/db/FtsQuery.kt`, new `core/db/NoteSearch.kt`, tests |
| 1d | X1, V1, N1, T1 (smallest possible edits) | `MainActivity.kt` only: `MainViewModel`, `NoteDetailScreen`, `RevisionHistorySection`, `SwipeableNote`, `MainActivity.onCreate` |
| 1e | Z1 | the six documents in the table |

R1 → plan 11. A1–A5 → plan 12. G1, G2 → plan 10. W1 → plans 07, 10, 11. E1 → plan 15.
M1, M2 → plans 08, 09. D1 → plans 07, 17. Y1 → plan 16. B1, P1 → owner decisions.
