# Plan 02 — Test coverage map and missing tests

**Claim checked:** "27/27 green". The count is right: 3 + 6 + 7 + 8 + 3 = 27 `@Test` methods in
five suites. The tests were **not re-run** in this session (no Android SDK here; see `06-ci.md`).
The first CI run is the independent confirmation.

## 1. What is covered today

| Behavior | Covered? | Where |
|---|---|---|
| Create: status INBOX, rev 1, Inbox category, audit row | Yes | `NoteCommandHandlerTest` |
| Empty create throws and writes nothing | Yes | same |
| Text stored verbatim on create | Yes | same |
| Undo of UPDATE_RAW_TEXT | Yes | same |
| Undo of CREATE, then `restore` | Yes | same |
| Undo of SET_STATUS | Yes | same |
| Undo of SOFT_DELETE | **No** | — |
| Undo of RESTORE, undo of UNDO | **No** (code throws) | — |
| Undo twice / undo after a later edit | **No** | — |
| Undo with unknown audit id | **No** | — |
| Revision guard at DAO level (raw text only) | Partly | `NoteDaoTest` |
| Revision conflict through the handler | **No** | — |
| Guarded status / delete / restore reject stale revision | **No** | — |
| Operations on a soft-deleted note | **No** | — |
| No-op edits (same text, same status) write no audit | **No** | — |
| FTS: multi-token AND, apostrophe, parentheses, deleted excluded | Yes | `NoteDaoTest` |
| FTS: stays in sync after edit / after restore | **No** | — |
| FTS: title, transcript, OCR columns | Title only | `NoteDaoTest` |
| Sanitizer: blank, quotes, operators, control chars, long input | **No** unit tests | — |
| `LIKE` fallback: wildcard characters | **No** | — |
| Share mapping (6 cases) | Yes | `ShareIngestTest` |
| Share: newline after URL, untrimmed text, styled text | **No** | — |
| `ShareReceiverActivity` itself | **No** | — |
| Export JSON decodes, excludes deleted, writes files | Yes | `NoteExporterTest` |
| Export → import → equal database | **No** (no import exists) | plan 15 |
| Export of unicode / null fields / empty DB | **No** | — |
| Privacy: no INTERNET, no unknown permission | Yes | `PrivacyManifestTest` |
| Foreign keys actually enforced | **No** | — |
| `Origin.encode/parse` round-trip | **No** | — |
| Schema file matches entities; migrations | **No** | plan 14 |

## 2. Conventions for every test below

- Robolectric suites copy the header of `NoteCommandHandlerTest` (`@RunWith`, `@Config(sdk=[33])`,
  in-memory DB with `BundledSQLiteDriver`, injected `now` and `newId`).
- Suspend calls that must throw use the existing `try { …; fail() } catch (e: X) {}` pattern.
- "(needs 1a/1b/1c)" means the test is written together with that step of plan 01. Tests with no
  such note pass against today's code and can be added first.
- Helper used below: `suspend fun lastAudit(id) = db.auditDao().recentFor("note", id, 1).single()`.

## 3. Missing tests

### 3.1 `NoteCommandHandlerTest` (extend)

| ID | Test name | Arrange → Act → Assert |
|---|---|---|
| T-U1 | `undo of soft delete restores the note` | create; `softDelete`; `undo(lastAudit.id)` → `deletedAt == null`, `countActive() == 1`, revision 3. |
| T-U2 | `undo of restore deletes again` (needs 1b) | create; softDelete; restore; undo(lastAudit) → `deletedAt != null`; last audit action `UNDO`. |
| T-U3 | `undo of undo is redo` (needs 1b) | create "a"; update "b"; undo → "a"; undo(lastAudit) → "b". |
| T-U4 | `undo is refused when the note changed since` (needs 1b) | create "a"; update "b" (audit X); update "c"; `undo(X)` throws `UndoStaleException`; text still "c"; audit count unchanged. |
| T-U5 | `second undo of the same entry is refused` (needs 1b) | create "a"; update "b" (X); undo(X); undo(X) throws `UndoStaleException`. |
| T-U6 | `undo restores null raw text as null` (needs 1b) | create with `title="t"` only; `updateRawText("x")`; undo → `rawText == null` (not `""`). |
| T-U7 | `undo of unknown audit id throws` | `undo(999)` throws `IllegalArgumentException`; nothing written. |
| T-U8 | `every undo is audited as UNDO by the acting actor` (needs 1b) | for CREATE, SOFT_DELETE, SET_STATUS: undo → last audit `action == UNDO`, `before`/`after` non-null. |
| T-R1 | `stale expected revision is rejected` (needs 1b) | create (rev 1); `updateRawText(id, "b")` (rev 2); `updateRawText(id, "c", expectedRevision = 1)` throws `NoteConflictException`; text "b". Repeat for `setStatus`, `softDelete`, `updateTitle`. |
| T-R2 | `matching expected revision is applied` (needs 1b) | `updateRawText(id, "b", expectedRevision = 1)` → rev 2. |
| T-N1 | `no-op edits write no audit and keep the revision` | create "a"; `updateRawText(id,"a")`; `setStatus(id, INBOX)` → revision 1, audit count 1. |
| T-D1 | `editing a soft-deleted note throws NoteMissing` | create; softDelete; `updateRawText` and `setStatus` each throw `NoteMissingException`. |
| T-D2 | `soft delete twice is idempotent` (needs 1a) | create; softDelete; softDelete → no throw; audit count 2. |
| T-D3 | `restore of a live note is a no-op` | create; restore → revision 1; audit count 1. |
| T-A1 | `audit snapshots record before and after revisions` | create; update "b" → audit `before.revision == 1`, `after.revision == 2`, `after.rawText == "b"`, `at == clock`. |
| T-A2 | `undo tokens are unique` | 5 mutations → 5 distinct `undoToken`. |
| T-K1 | `updateTitle is audited and undoable` (needs 1b) | create; `updateTitle("T")` → title "T", rev 2, action `UPDATE_TITLE`; undo → title null. |
| T-K2 | `attachTranscript fills only an empty transcript` (needs 1b) | create VOICE with `audioPath`; `attachTranscript("hi", actor=SYSTEM)` ok; second call by SYSTEM throws `IllegalStateException`; by USER succeeds. |
| T-M1 | `image-only capture is saved` (needs 1a) | `create(CreateNoteRequest(PHOTO, imagePath="images/a.jpg"))` succeeds. |

### 3.2 `NoteDaoTest` (extend)

| ID | Test name | Arrange → Act → Assert |
|---|---|---|
| T-G1 | `guarded status, delete and restore reject stale revisions` | insert rev 1; each of `updateStatusGuarded`, `softDeleteGuarded` with revision 7 returns 0; `restoreGuarded` on a live note returns 0. |
| T-F1 | `fts follows edits` | insert "alpha"; `updateRawTextGuarded` → "beta"; search "alpha" → 0; "beta" → 1. |
| T-F2 | `fts finds restored notes again` | insert "gamma"; softDelete; restore; search "gamma" → 1. |
| T-F3 | `fts covers transcript and ocr columns` | insert with `transcript="seismograph"`, another with `ocrText="isobar"` → each found. |
| T-F4 | `fts folds case and diacritics` | insert "El café está aquí"; search "CAFE" → 1. |
| T-F5 | `fts integrity check passes after churn` | 50 inserts, 20 edits, 10 deletes; run `INSERT INTO notes_fts(notes_fts) VALUES('integrity-check')` through a raw statement → no exception. |
| T-F6 | `prefix search matches partial last word` (needs 1c) | insert "oatmeal"; `sanitize("oat")` → 1 hit; `sanitize("oat ", …)` behaviour documented in the test. |
| T-F7 | `search results are ranked` (needs 1c) | note A has "drift" 3×, note B 1× → A first. |
| T-L1 | `like fallback treats percent and underscore literally` (needs 1c) | insert "100% done", "100 done"; `searchLike("100%")` → only the first. |
| T-FK1 | `foreign keys are enforced` | `derivedDao().upsert(DerivedEntity(noteId="missing", …))` throws. If it does **not** throw, stop and report: foreign keys are off in Room 3 + bundled driver. |
| T-P1 | `list projection truncates preview and filters status` (needs 1c) | insert 300-char note + DONE note; `observeList(INBOX, 50).first()` → 1 item, `preview.length == 240`. |

### 3.3 New `FtsQueryTest` (plain JUnit, no Robolectric)

One table-driven test per row; expected values are for the **current** sanitizer unless marked.

| ID | Input | Expected |
|---|---|---|
| T-S1 | `""`, `"   "`, `"\t\n"` | `null` |
| T-S2 | `"\""`, `"\"\""` | `null` |
| T-S3 | `"oat milk"` | `"\"oat\" \"milk\""` |
| T-S4 | `"a\"b"` | `"\"ab\""` |
| T-S5 | `"AND"`, `"NEAR"`, `"title:x"`, `"^x"`, `"x*"` | each wrapped in quotes, unchanged inside |
| T-S6 | `"oat milk"` | two tokens (Kotlin `\s` must split NBSP; if it does not, fix the regex to `[\\s\\u00A0]+`) |
| T-S7 (needs 1c) | `"\u0000oat"` | `"\"oat\"*"` — control characters removed |
| T-S8 (needs 1c) | 40 tokens | only the first 12 kept |
| T-S9 (needs 1c) | one 500-char token | truncated to 64 chars |
| T-S10 (needs 1c) | `"oat mi"` with `prefixLast=true` | `"\"oat\" \"mi\"*"` |

Plus one Robolectric test in `NoteDaoTest`, **T-S11** `sanitized hostile inputs never throw`:
loop over `listOf("-", "( )", "***", "+", "{}", "col:", "🙂", "\u0000", "a OR", "NOT", "\"", "'")`;
for each non-null sanitized value call `searchFts` and assert no exception.

### 3.4 `ShareIngestTest` (extend) and new `ShareReceiverActivityTest`

| ID | Test name | Assert |
|---|---|---|
| T-C3 | `url followed by a newline and text is not link-only` (needs 1a) | `toRequest("https://a.org\nread ch. 3", null, null)` → `rawText` equals input; `sourceUrl == "https://a.org"`. |
| T-C4 | `shared text is stored untrimmed` (needs 1a) | `toRequest("  hi  ", …).rawText == "  hi  "`. |
| T-C5 | `two urls in text set no source url` (needs 1a) | `sourceUrl == null`. |
| T-C1 | `styled text share is saved` (needs 1a) | Robolectric: build `Intent(ACTION_SEND)` with `putExtra(EXTRA_TEXT, SpannableString("styled"))`; `Robolectric.buildActivity(ShareReceiverActivity::class.java, intent).create()`; `countActive() == 1`; activity `isFinishing`. The test swaps the container DB for an in-memory one (add a `@VisibleForTesting` setter on `MindPalaceApplication`). |
| T-C2 | `empty share shows a toast and saves nothing` (needs 1a) | `ShadowToast.getTextOfLatestToast()` equals `share_empty_toast`; count 0. |
| T-C6 | `non-SEND intent finishes without saving` | action `VIEW` → count 0, `isFinishing`. |

### 3.5 `NoteExporterTest` (extend)

| ID | Test name | Assert |
|---|---|---|
| T-E1 | `export of an empty database is valid` | `decode(buildJson())` → `noteCount == 0`, `notes.isEmpty()`. |
| T-E2 | `export preserves unicode and newlines` | note `"línea 1\nسطر ٢ 🙂 \"q\""` → decoded text identical. |
| T-E3 | `null fields survive the round trip` | title-less note → decoded `title == null`. |
| T-E4 | `decode ignores unknown keys` | add `"future_field": 1` to the JSON string → decodes. |
| T-E5 | `markdown export escapes structure characters` (after X1.3) | note text `"---\n## fake"` → output has exactly one `## ` heading per note. |
| T-E6…E10 | export → import round trip | specified in `15-export-import.md` §6. |

### 3.6 Other new suites

| ID | Suite | Assert |
|---|---|---|
| T-O1 | `OriginTest` (plain) | `parse(encode(x)) == x` for all four kinds; `parse("bogus") == null`; `parse("rule:a:b")` → `RuleOrigin("a:b")`. |
| T-ARCH | `ArchitectureTest` (plain, needs 1b) | see plan 01 H6. |
| T-PRIV2 | `PrivacyManifestTest` (extend) | exported activities are exactly `MainActivity` and `ShareReceiverActivity`; `usesCleartextTraffic` is not true. |
| T-SCHEMA | `SchemaContractTest` | specified in `14-migration-test-framework.md`. |

## 4. Order

1. Add every test **without** a "needs" note (16 tests). All must pass on current code except
   T-FK1 and T-S6, which are probes: if either fails, stop and report rather than "fixing" the
   test.
2. Add the rest together with plan 01 steps 1a, 1b, 1c.
3. Update the table in `docs/TEST_RESULTS.md` with the new counts after each step.
