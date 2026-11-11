# Plan 01 — Backend audit and hardening punch list

**Audited:** `core/commands`, `core/db`, `core/model`, `capture`, `export`, `MainActivity`,
`MindPalaceApplication`, build files, manifest, all five test suites (commit `faa6514`).
**Method:** full read of every file. The FTS findings (S1–S4) were reproduced against real SQLite
FTS5 on a desktop with the same table and trigger definitions as `app/schemas/…/1.json`.
**Not done:** the project was not compiled here (see `06-ci.md`, "Network limits").
**Limit:** the original build plan is not in the repo, so "gaps against the plan" means gaps
against the plan rules quoted in the code (`docs/PLAN.md`).

Priority: **P0** = can lose or strand the owner's notes, or breaks a stated principle.
**P1** = will cause bugs or rework as soon as a UI exists. **P2** = cleanup.

## Summary

| ID | P | Area | One-line problem |
|---|---|---|---|
| C1 | P0 | capture | Styled-text shares are silently dropped |
| C2 | P0 | capture | A failed save during share crashes; a rejected share gives no feedback |
| E1 | P0 | export | Export file is unreachable by the owner, so there is no working way off the phone |
| E2 | P0 | export | Export drops categories/tags, deleted notes, and history |
| B1 | P0 | build | Reinstalling from a different machine forces an uninstall, which wipes all notes |
| U1 | P0 | commands | Undo of an old edit silently overwrites newer edits |
| H1 | P1 | commands | Callers cannot pass the revision they edited, so stale UI saves overwrite |
| H2 | P1 | commands | No commands for title, transcript, OCR text, or media paths |
| H3 | P1 | commands | Derived facts (category, tag…) have no command and no audit |
| H4 | P1 | commands | Undo is not supported for RESTORE or UNDO; undo audit naming is inconsistent |
| H5 | P1 | commands | `softDelete` reports the wrong exception |
| H6 | P1 | commands | "Only writer" is a convention; DAOs expose unguarded writes |
| H7 | P1 | commands | `hasCaptureContent` rejects a photo-only or audio-only capture |
| D1 | P1 | db | `observeActive()` loads every note with full text |
| D2 | P1 | db | No query by status; "Inbox" has no backing query |
| D3 | P1 | db | "Current category" is undefined when several origins disagree |
| S1 | P1 | search | No prefix matching, so search-as-you-type finds nothing until a word is complete |
| S2 | P1 | search | A NUL character in the query throws |
| S3 | P1 | search | `LIKE` fallback is never used and does not escape `%` or `_` |
| S4 | P1 | search | Results are not ranked; no snippet; out-of-order results in the placeholder |
| C3 | P1 | capture | Multi-line text starting with a URL is stored as a "link" |
| C4 | P1 | capture | Share path trims text, against "verbatim" |
| M1 | P1 | ui | Placeholder loses typed text on rotation |
| X1 | P2 | various | Cleanup list (12 items) |

## P0 details

### C1 — Styled-text shares are dropped
`ShareReceiverActivity` reads `intent.getStringExtra(EXTRA_TEXT)`. When the sender puts a
`SpannableString` (styled text) in the extra, `getStringExtra` returns `null`. The share is then
treated as empty and discarded with no message.
**Fix:** `intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()`; same for subject and title.
**Test:** T-C1 in `02-missing-tests.md`.

### C2 — Share failure handling
(a) `runBlocking { commands.create(request) }` is not wrapped; an `SQLiteException` (disk full)
crashes the transient activity and the text is gone. (b) When `ShareIngest` returns `null`, the
activity finishes silently.
**Fix:** wrap in `try/catch`; on failure show `R.string.share_failed_toast` ("Could not save —
copy your text and try again") and log. On `null` request show `R.string.share_empty_toast`.
Never swallow without a toast.

### E1 — Export cannot be reached
`NoteExporter.writeJson` writes to `filesDir/exports`. That directory is private; the owner
cannot open, copy, or share the file. Nothing in the UI calls the exporter either. Because
`allowBackup=false` (D4), there is currently **no** way to move notes to a new phone.
**Fix:** `15-export-import.md` (Storage Access Framework save dialog; needs no permission).
**Interim:** `scripts/checkpoint0.sh --pull-export` copies the private file with `adb run-as`
(debug builds only).

### E2 — Export is lossy
`ExportNote` omits `image_path`, every `derived` row, deleted notes, and the audit trail.
`NoteExporterTest` "round-trips" only by decoding the JSON; nothing imports it.
**Fix:** export schema v2 in `15-export-import.md`.

### B1 — Signing key and data loss
The app is debug-signed. Each machine (the Mac, a CI runner) has its own debug key. Installing an
APK signed with a different key fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; the usual "fix"
is to uninstall, **which deletes the database**.
**Fix:** (1) `scripts/checkpoint0.sh` never uninstalls and explains this error. (2) Treat CI APKs
as verification artifacts, not as the build the owner installs, until a single signing key is
chosen (owner decision OD-2 in `INDEX.md`).

### U1 — Undo can overwrite newer edits
`undo(auditId)` re-applies the old `before` text no matter what happened since. Sequence: edit
A→B, edit B→C, undo the first edit → text becomes A; C is gone from the note (it survives only in
`audit`). Undoing the same row twice is a silent no-op for edits and a `NoteConflictException`
for creates.
**Fix (design used by every later plan):** *undo = restore the `before` snapshot, only if nothing
changed since.*
- `undo(auditId, actor = USER)` loads the audit row and the note. If
  `note.revision != after.revision`, throw `UndoStaleException(noteId, auditId)`.
- `before == null` (a CREATE) → soft delete. Otherwise apply one guarded update that restores
  `title`, `raw_text`, `status`, `deleted_at` from `before`:
  ```sql
  UPDATE notes SET title = :title, raw_text = :rawText, status = :status,
         deleted_at = :deletedAt, updated_at = :now, revision = revision + 1
  WHERE id = :id AND revision = :revision
  ```
  (`NoteDao.restoreSnapshotGuarded`; note there is no `deleted_at` condition.)
- Every undo writes one audit row with `action = UNDO`, whatever the original action was.
- This makes undo work for all six actions, including RESTORE and UNDO (undo of an undo is redo),
  and makes a second undo of the same row fail as stale. It also restores `raw_text = NULL`
  correctly (today it writes `""`).

## P1 details

### H1 — Expose the revision guard
Handler methods re-read the note and use *that* revision, so the guard only covers a
microsecond window. A screen that loaded revision 2 and saves after a rule moved the note to
revision 3 overwrites silently.
**Fix:** add `expectedRevision: Int? = null` to `updateRawText`, `updateTitle`, `setStatus`,
`softDelete`, `restore`. When non-null and `!= current.revision`, throw `NoteConflictException`.
UI always passes it; rules and AI always pass it.

### H2 — Missing note commands
Add, all audited and revision-guarded, each with a new `AuditAction` constant (enums persist by
name, so adding constants needs no migration):
`updateTitle` (`UPDATE_TITLE`), `attachTranscript` (`SET_TRANSCRIPT`), `attachOcrText`
(`SET_OCR_TEXT`). `transcript`/`ocr_text` may be set only when currently `NULL` or by actor USER.
Add `imagePath` to `NoteSnapshot` (new field with default `null`; old JSON still decodes).

### H3 — Derived facts need commands
`DerivedDao.upsert/deleteForField` are public and unaudited. Add to the handler:
`addDerived(noteId, field, value, origin, confidence, actor)` and
`removeDerived(noteId, field, value, origin, actor)`. Audit rows use `entity = "derived"`,
`entity_id = noteId`, actions `DERIVED_ADD` / `DERIVED_REMOVE`, and a small JSON
(`DerivedSnapshot`) in `before`/`after`. Extend `undo` to handle `entity = "derived"` (remove ↔
add). Full spec in `10-rules-engine.md` §3.

### H4 — covered by U1.

### H5 — `softDelete` exception mapping
`throw if (current.deletedAt != null) NoteConflictException else NoteMissingException` is
backwards for the second branch: a live note whose update matched 0 rows lost a race.
**Fix:** already deleted → return (idempotent, like `restore`); otherwise `NoteConflictException`.

### H6 — Enforce "only writer"
- Delete `NoteDao.updateRow` (unused, unguarded, unaudited).
- Add `ArchitectureTest` (plain JUnit, scans `app/src/main/java`): no file outside
  `core/commands/` and `core/db/` may contain a call matching
  `\.(insert|insertIfAbsent|upsert|append|enqueue|setState|setEnabled|updateState|finish|delete\w*|update\w*Guarded|softDeleteGuarded|restore\w*Guarded)\(`
  on a DAO. Allow-list file paths explicitly (the night loop's job runner will be added later).
- `AppContainer`: keep `database` but document read-only use; UI code receives DAOs, not the DB.

### H7 — Media-only capture
`CreateNoteRequest.hasCaptureContent()` ignores `imagePath`. A photo whose OCR has not run yet
cannot be saved, which breaks save-first for camera capture.
**Fix:** count a non-blank `imagePath` (and `audioPath` once `08-voice-capture.md` adds it).

### D1 — List query
`observeActive()` returns `List<NoteEntity>` for all notes; every change re-reads all text.
**Fix:** add a projection and a limit:
```kotlin
data class NoteListItem(val id: String, val title: String?, val preview: String?,
    val status: NoteStatus, val source: NoteSource, val createdAt: Long,
    val updatedAt: Long, val revision: Int)
@Query("SELECT id, title, substr(coalesce(raw_text, transcript, ocr_text, ''), 1, 240) AS preview, " +
       "status, source, created_at AS createdAt, updated_at AS updatedAt, revision " +
       "FROM notes WHERE deleted_at IS NULL AND (:status IS NULL OR status = :status) " +
       "ORDER BY created_at DESC, rowid DESC LIMIT :limit")
fun observeList(status: NoteStatus?, limit: Int): Flow<List<NoteListItem>>
```
Also add `fun observeById(id: String): Flow<NoteEntity?>`. This covers D2.

### D3 — Category resolution
`derived` can hold `CATEGORY=Inbox (system)` and `CATEGORY=Classroom (rule:r1)` together.
**Rule (data contract, record as a decision):** the effective value of a single-valued field
(`CATEGORY`, `DUE_AT`) is the row with the highest-precedence origin: `user` > `rule:*` > `ai:*`
> `system`; ties break on newest `created_at`. Multi-valued fields (`TAG`, `PERSON`, `PLACE`,
`TOPIC`) are the union. Implement once as a pure function `DerivedResolver` in `core/model`.

### S1–S4 — Search
Reproduced on SQLite 3.53 FTS5 with the v1 schema:

| Input | Sanitized | Result |
|---|---|---|
| `oa` (note has "oat") | `"oa"` | no match — **S1** |
| `"oa"*` (hand-written) | — | matches "oat", "oatmeal" |
| `\u0000oat` | `"\u0000oat"` | **throws** `unterminated string` — **S2** |
| `-`, `( )`, `***`, `+`, `{}` | `"-"` … | no rows, no error (fine) |
| `milk - oat` | `"milk" "-" "oat"` | matches (empty phrase ignored) |
| `AND`, `NEAR`, `title:papa`, `^oat` | quoted | treated as plain words (fine) |
| `cafe` (note has "café") | `"cafe"` | matches (diacritics folded) |
| `地震` (note has "地震波の観測") | `"地震"` | no match — unicode61 does not segment CJK |

**Fix:** `FtsQuery.sanitize(raw, prefixLast = true)`: strip characters below U+0020 and `"`,
cap at 12 tokens and 64 chars per token, and append `*` to the last token. Add
`NoteDao.observeSearch`/`search` ordered by `bm25(notes_fts)` with
`snippet(notes_fts, -1, '[', ']', '…', 12)`. Wrap the call in one function
`NoteSearch.search(query)` that catches `SQLiteException` and falls back to `searchLike` with
`%`, `_`, `\` escaped (`LIKE … ESCAPE '\'`). The CJK limit is recorded, not fixed.

### C3, C4 — `ShareIngest`
`linkOnly` tests `contains(' ')` only; `"https://a.org\nmy notes"` becomes a link and the whole
string lands in `source_url`. Use `none { it.isWhitespace() }`. Store the shared text untrimmed
in `raw_text` (use the trimmed copy only for the emptiness and link checks). When text is not
link-only, set `source_url` to the first `https?://\S+` match if there is exactly one.

### M1 — Placeholder state
`remember { mutableStateOf("") }` loses the draft on rotation, font-size change, or dark-mode
switch. Do not patch the placeholder; `07-frontend-compose.md` requires `rememberSaveable` and a
persisted draft.

## P2 cleanup (X1)

1. Stray word "quilks" in the `NoteFtsEntity` KDoc.
2. `NoteCommandHandler.undo` KDoc says "undo is undoable" while the code throws (fixed by U1).
3. Markdown export does not escape note text; a note containing `---` or `## ` breaks structure.
   Timestamps are raw epoch millis.
4. `gradle-wrapper.properties` has no `distributionSha256Sum`.
5. `libs.versions.toml` comment says the JVM SQLite artifact carries "macOS" natives; CI is Linux.
6. `MainActivity` uses `collectAsState`; `lifecycle-runtime-compose` is a dependency but unused.
7. Theme is light-only (`Theme.Material.Light.NoActionBar`).
8. `NoteDao.changedSince` hides deletions from a future night loop.
9. `JobDao.nextBatch` + `updateState` cannot claim a job atomically (fixed in plan 12).
10. No `android:dataExtractionRules`. `allowBackup=false` stops cloud backup, but on Android 12+
    some manufacturers still allow cable/Wi-Fi device-to-device transfer. Decide and make it
    explicit (owner decision OD-3).
11. `ShareReceiverActivity` is exported with no restriction; any app can add notes. Accepted by
    design; write it down in `DECISIONS.md`.
12. `PrivacyManifestTest` checks the debug variant only; add the same assertions for `<queries>`
    and for exported components (allow-list: `MainActivity`, `ShareReceiverActivity`).

## Execution (for the faster model)

Do this plan in three commits, each with its tests from `02-missing-tests.md`:

| Step | Items | Files allowed |
|---|---|---|
| 1a | C1, C2, C3, C4, H5, H7, X1.1, X1.2 | `capture/ShareReceiverActivity.kt`, `core/commands/*`, `res/values/strings.xml`, matching tests |
| 1b | U1/H4, H1, H2, H6 | `core/commands/*`, `core/db/Daos.kt`, `core/model/Model.kt`, new `ArchitectureTest.kt`, matching tests |
| 1c | D1, D2, S1–S4 | `core/db/Daos.kt`, `core/db/FtsQuery.kt`, new `core/db/NoteSearch.kt`, matching tests |

H3 and D3 are done inside plan 10. E1/E2 are plan 15. B1 is plan 05/06. No step here changes the
schema (`1.json` must be byte-identical afterwards; if Room wants to regenerate it, stop and
report).
