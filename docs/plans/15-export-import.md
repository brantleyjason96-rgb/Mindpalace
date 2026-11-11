# Plan 15 — Export and import

**State today:** `NoteExporter` writes JSON or Markdown to `filesDir/exports`, and Settings hands
the file to the share sheet through `FileProvider`. The JSON holds only the v1 note columns of
non-deleted notes. Nothing imports. `allowBackup=false` (D4), so this file is the only way to
move to a new phone or recover from an uninstall.
**Goal:** an export that carries everything the owner would miss, and an import that restores it
without ever overwriting a note.
**Sign-off needed:** none. The system file dialogs need no permission.
**Schema change:** none to the database. The **export format** goes from v1 to v2.

## 1. What the export loses today

| Data | In today's export? |
|---|---|
| Note text, title, source, status, timestamps | Yes |
| `image_path`, attachment path and file name | **No** |
| The photo, drawing, and attachment **files** | **No** |
| Categories, tags, people, places, topics on a note (`derived`) | **No** |
| Category list, topic/people/place lists | **No** |
| Rules | **No** |
| Reminders | **No** |
| Links between notes | **No** |
| Notes in Trash | **No** |
| Edit history (`audit`) | **No** |
| AI proposals and their decisions | **No** |

## 2. Export format v2

A single **ZIP** file, `mind-palace-<yyyyMMdd-HHmmss>.zip`:

```
manifest.json            # small header, read first
data.json                # everything below
media/photos/<file>      # every file referenced by notes.image_path
media/attachments/<file> # every file referenced by notes.attachment_path
```

`manifest.json`:
```json
{ "format": "mindpalace-export", "format_version": 2, "app_version": "0.6.0",
  "db_schema_version": 4, "exported_at": 1796130000000,
  "counts": { "notes": 412, "deleted_notes": 9, "media_files": 37 },
  "includes_history": true }
```

`data.json` (kotlinx.serialization, `encodeDefaults = true`, `ignoreUnknownKeys = true`). Field
names are the database column names. Enums are written by name.

| Key | Rows | Notes |
|---|---|---|
| `notes` | every note, **including soft-deleted** (`deleted_at` set) | all columns except `rowid`; paths are written as `media/photos/<name>` or `media/attachments/<name>` |
| `derived` | all | |
| `categories`, `topics`, `people`, `places` | all | |
| `rules` | all | matcher and action stay as the stored JSON strings |
| `note_links` | all | without the local integer `id` |
| `reminders` | all | |
| `proposals` | all | so rejected suggestions are not offered again after a restore |
| `audit` | all, **only if "Include edit history" is on** (default on) | without the local integer `id`; `undo_token` kept |

Not exported: `ai_index` (rebuilt by the next night run), `ai_runs`, `jobs`, the AI model file,
drafts.

Markdown export stays as a readable copy only. Fix it while here: escape a leading `#`, `---`
lines, and backticks in note text; write dates as ISO local time; add the effective category and
tags under each title.

## 3. Getting the file out and in

| Action | Mechanism | Permission |
|---|---|---|
| Save to a place the owner picks (Files, USB drive, SD card) | `ActivityResultContracts.CreateDocument("application/zip")` | none |
| Send through another app | existing share sheet + `FileProvider` | none |
| Import | `ActivityResultContracts.OpenDocument()` with `application/zip` and `application/json` | none |

Settings shows a plain warning before either export action: "This file contains all your notes
in readable form. Anyone who gets the file can read them."
Old files in `filesDir/exports` are pruned to the newest two after each export.

## 4. Import

### 4.1 Rules
1. **Nothing is ever overwritten.** A note that already exists is left exactly as it is.
2. **Validate everything, then write.** A file that fails validation changes nothing.
3. **Preview first.** The owner sees counts and confirms before any write.
4. Imports go through commands, are audited, and can be undone note by note.

### 4.2 Accepted input
- v2 ZIP (above).
- v1 JSON (`schema_version: 1`, the current format): notes only. Each becomes a v2 note with no
  media and no derived rows; `create`-style defaults apply (system `Inbox` row, rules run).
- Anything else, or `format_version` greater than 2 → `ImportError.UnsupportedVersion`. Never
  guess.

### 4.3 Validation (`ImportValidator`, pure)
| Check | Failure |
|---|---|
| ZIP has `manifest.json` and `data.json`; both parse | `Malformed` |
| `format == "mindpalace-export"` and version ≤ 2 | `UnsupportedVersion` |
| Every ZIP entry name is exactly `manifest.json`, `data.json`, or `media/(photos|attachments)/<name>` where `<name>` has no `/`, `\`, or `..` | `UnsafePath` |
| Every note id is a non-blank string, unique within the file | `Malformed` |
| Every enum value is a known constant (`NoteSource`, `NoteStatus`, `DerivedField`, …) | `UnknownValue(field, value)` |
| Every `derived`, `reminders`, `proposals`, `note_links` row points at a note id in the file or already in the database | drop the row, count it under `skippedOrphans` |
| Every media path in a note has a matching ZIP entry | keep the note, clear nothing, count under `missingMedia` |
| Uncompressed total ≤ free space − 200 MB; `data.json` ≤ 200 MB; entry count ≤ 100,000 | `TooLarge` |

### 4.4 Matching and what happens

| Item | Match on | New | Same | Different |
|---|---|---|---|---|
| Note | `id` | insert (see 4.5) | skip | **skip**, list under "kept the version on this phone" |
| Category | `name`, case-insensitive | insert at the end | skip | — |
| Topic / person / place | `name`, case-insensitive | insert with the file's id unless that id is taken, then a new id | skip | — |
| Rule | `id` | insert, `position` after existing rules | skip | skip |
| Derived row | primary key (note, field, value, origin) | insert | skip | — |
| Link | (from, to, type) | insert if both notes exist after import | skip | — |
| Reminder | `id` | insert; then `ReminderCommands.rescheduleAll` | skip | skip |
| Proposal | `id` | insert | skip | skip |
| Audit row | `undo_token` | insert | skip | — |

"Same" for a note means equal `revision`, `updated_at`, `title`, `raw_text`, `transcript`,
`ocr_text`, `status`, and `deleted_at`. "Different" notes are never merged; the summary offers
"Import those as copies", which inserts them under new ids with no links or reminders.

### 4.5 Commands (new `core/commands/ImportCommands.kt`)
```kotlin
class ImportCommands(db, noteCommands, reminderCommands, files: CaptureFiles, now, newId) {
    suspend fun preview(source: ImportSource): ImportPreview        // validates, writes nothing
    suspend fun apply(source: ImportSource, options: ImportOptions): ImportSummary
}
data class ImportPreview(val newNotes: Int, val sameNotes: Int, val differentNotes: Int,
    val deletedNotes: Int, val mediaFiles: Int, val missingMedia: Int, val skippedOrphans: Int,
    val newRules: Int, val newReminders: Int, val warnings: List<ImportWarning>)
data class ImportOptions(val importDifferentAsCopies: Boolean = false, val includeHistory: Boolean = true)
```
`apply`:
1. Copy media files first, each through `CaptureFiles.import` (plan 09 §3.3) into `photosDir` /
   `attachmentsDir`. If a file name already exists with different bytes, store under a new name
   and rewrite that note's path. Media copied for notes that end up skipped is removed again.
2. Insert in chunks of 200 notes per write transaction. Notes keep their `id`, `created_at`,
   `updated_at`, `status`, `revision`, and `deleted_at`. Inserting through the `notes` table
   keeps the search index in step (the FTS triggers fire).
3. Each inserted note gets one audit row: `action = IMPORT` (new `AuditAction` constant),
   `actor = USER`, `before = null`, `after = snapshot`. With plan 01's undo rule, undoing an
   `IMPORT` row soft-deletes that note.
4. Then vocabulary, rules, derived, links, reminders, proposals, and (if chosen) the file's audit
   rows, each with "insert if absent".
5. A failure in a chunk rolls back that chunk and stops; the summary says how many notes were
   imported before the failure. Re-running the same import is safe because imported notes now
   count as "same".

Add `imported_from` nowhere; the `IMPORT` audit row is the record.

### 4.6 UI (`SettingsScreen`, after plan 07 step 9: `ui/settings/`)
- Export card: "Save backup file…" (SAF), "Share backup…" (share sheet), "Include edit history"
  switch, the warning text, and the date and size of the last export.
- Import card: "Restore from a backup file…" → preview dialog with the counts → Confirm →
  progress → summary with "View imported notes" (Timeline filtered to the import time).
- Export and import run in an application-scoped coroutine with a progress state, so leaving
  Settings does not cancel them.

## 5. Export side (`NoteExporter` → `export/Exporter.kt`)

```kotlin
class Exporter(db: MindPalaceDatabase, photosDir: File, attachmentsDir: File, now: () -> Long) {
    suspend fun writeZip(out: OutputStream, includeHistory: Boolean): ExportCounts
    suspend fun writeMarkdown(out: OutputStream)
}
```
- Reads inside one read transaction so the data is consistent.
- Streams `data.json` with kotlinx.serialization's `encodeToStream`; never builds one giant
  string. Streams media with an 8 KB buffer.
- A referenced media file that is missing on disk is counted in the manifest
  (`"missing_media": n`) and the export still succeeds.
- Keep `NoteExporter.decode` for v1 files; add `ExportV2.decode`.

## 6. Tests

`ExportImportRoundTripTest` (Robolectric, two in-memory databases A and B, temp dirs):

| ID | Test name | Assert |
|---|---|---|
| T-E5 | `export then import into an empty database reproduces every table` | Seed A with: 5 notes (one deleted, one PHOTO with a file, one ATTACHMENT with a file), 2 rules, a user category, tags, 1 link, 1 reminder, 1 rejected proposal, edits (audit). Export A → import into B. For each exported table, rows in B equal rows in A (ignoring local integer ids). Media bytes equal. |
| T-E6 | `search works on imported notes` | `NoteSearch.search` in B finds a word from an imported note. |
| T-E7 | `importing the same file twice changes nothing the second time` | second `apply` → `newNotes == 0`; row counts unchanged. |
| T-E8 | `a note that differs on the phone is never overwritten` | edit a note in B, import again → B's text unchanged; `differentNotes == 1`. |
| T-E9 | `import different as copies creates new ids` | option on → one extra note with a new id; original untouched. |
| T-E10 | `each imported note has one IMPORT audit row and undo soft-deletes it` | action `IMPORT`; `undo` → `deletedAt != null`. |
| T-E11 | `deleted notes arrive in Trash` | `recentlyDeleted` contains it; not in the active list. |
| T-E12 | `a v1 json file imports as plain notes` | use the output of today's `NoteExporter.buildJson()` → notes present with `Inbox` category. |
| T-E13 | `reminders are re-armed after import` | `ShadowAlarmManager` has one alarm (needs plan 11 Part A; otherwise assert the row only). |

`ImportValidatorTest` (plain JUnit, in-memory ZIPs built in the test):

| ID | Test name | Assert |
|---|---|---|
| T-E14 | `a path with dot-dot is rejected` | entry `media/photos/../../evil` → `UnsafePath`; nothing written. |
| T-E15 | `a newer format version is rejected` | `format_version: 3` → `UnsupportedVersion`. |
| T-E16 | `an unknown enum value is rejected with its field name` | `status: "PINNED"` → `UnknownValue("status", "PINNED")`. |
| T-E17 | `duplicate note ids in the file are rejected` | `Malformed`. |
| T-E18 | `rows pointing at unknown notes are dropped and counted` | `skippedOrphans == 1`. |
| T-E19 | `truncated zip is rejected and changes nothing` | cut the bytes in half → `Malformed`; database unchanged. |
| T-E20 | `missing media keeps the note and is counted` | `missingMedia == 1`. |

`ExporterTest`: T-E21 the ZIP lists exactly `manifest.json`, `data.json`, and the referenced
media; T-E22 manifest counts equal the data; T-E23 Markdown escapes `---` and `#` in note text;
T-E24 export with history off has no `audit` key.

Keep the three existing `NoteExporterTest` cases passing.

## 7. Device check (owner) — add to `docs/CHECKPOINT_0.md`

1. Settings → Save backup file → pick Downloads → the ZIP is there and opens on a computer.
2. On a second device or an emulator with a fresh install: Restore → preview counts match →
   notes, categories, a photo, and a reminder are all present.
3. Restore the same file again on the first phone → "0 new".

**Do not test restore by uninstalling the app on the main phone** until step 2 has passed
somewhere else.

## 8. Files allowed

`export/*` (new `Exporter.kt`, `ExportV2.kt`, `ImportValidator.kt`), new
`core/commands/ImportCommands.kt`, `core/commands/NoteCommandHandler.kt` (an internal
`insertImported` used only by `ImportCommands`), `core/model/Model.kt` (`AuditAction.IMPORT`),
`core/db/Daos.kt` (bulk read queries, `insertIfAbsent` variants), `MindPalaceApplication.kt`,
`res/xml/file_paths.xml` (no change expected), `MainActivity.kt` or `ui/settings/*`
(`SettingsScreen` only), `strings.xml`, tests. **Not** the manifest, entities, or migrations.

## 9. Done when

- All tests pass in CI; `PrivacyManifestTest` unchanged.
- The three device checks are logged as PASS.
- `DECISIONS.md`: format v2, never-overwrite rule, what is not exported. `CHANGELOG.md`; version
  bump. `README` gains a short "Moving to a new phone" section.

## 10. Stop and report if

- A round-trip test shows a table whose rows cannot be compared without local ids; say which.
- Streaming JSON needs a dependency not already in the project.
- Order matters: do this plan **after** plans 10, 11A, and 12 if possible, so the format covers
  their columns. If done earlier, the format must tolerate added columns (it does:
  `ignoreUnknownKeys`, defaults) and T-E5 must be extended by each later plan.
