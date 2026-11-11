# Plan 02 — Test coverage map and missing tests

**Count checked:** the source has **49** `@Test` methods in eight suites
(`PrivacyManifestTest` 4, `NoteCommandHandlerTest` 16, `ShareIngestTest` 6, `AiRunDaoTest` 2,
`MigrationTest` 2, `NoteDaoTest` 7, `RuleEngineTest` 9, `NoteExporterTest` 3).
The task brief says 27 (the count on `main`); `DECISIONS.md` mentions 45 and 50. Use the number
CI reports. The tests were not run in the planning session; see `06-ci.md` for the first CI
result.

## 1. Coverage today

| Behavior | Covered? | Where |
|---|---|---|
| Create: INBOX, rev 1, Inbox category, audit row; empty create rejected; verbatim text | Yes | `NoteCommandHandlerTest` |
| Undo of UPDATE_RAW_TEXT, CREATE, SET_STATUS | Yes | same |
| Undo of SOFT_DELETE | **No** | — |
| Undo of RESTORE, undo of UNDO | **No** (code throws) | — |
| Undo twice; undo after a later edit; unknown audit id | **No** | — |
| Revision guard at DAO level, raw text only | Partly | `NoteDaoTest` |
| Revision conflict through the handler; guarded status/delete/restore | **No** | — |
| Operations on a soft-deleted note; no-op edits | **No** | — |
| Rules at create: match, disabled, non-match, attachment file name | Yes | `NoteCommandHandlerTest` |
| Rule engine: case, any-of, blank, order, disabled, JSON round trip | Yes | `RuleEngineTest` |
| Rule order as stored (`RuleDao.enabled()`), re-run on edit, rule deletion | **No** | plan 10 |
| Approve PERSON proposal; non-pending no-op; reject changes state only | Yes | `NoteCommandHandlerTest` |
| Approve CATEGORY / TAG / TOPIC / PLACE; approve twice → duplicates; deleted note | **No** | — |
| `AiProposalValidator`, `AiPrompt.needsSummary` | **No** | — |
| `AiNightRunner` (any path); `AiNightWorker` | **No** | plan 12 |
| `lastSucceededFinishedAt` | Yes | `AiRunDaoTest` |
| Migrations 1→2 and 2→3 | Yes | `MigrationTest` |
| Full chain with data in every table; FTS after migration; real DB opens migrated file | **No** | plan 14 |
| FTS: multi-token AND, apostrophe, parentheses, deleted excluded | Yes | `NoteDaoTest` |
| FTS in sync after edit / restore; transcript and OCR columns | **No** | — |
| Sanitizer unit tests; control characters; `LIKE` wildcards | **No** | — |
| Share mapping (6 cases) | Yes | `ShareIngestTest` |
| Share: newline after URL, untrimmed text, styled text; the activity itself | **No** | — |
| Export JSON decodes, excludes deleted, writes files | Yes | `NoteExporterTest` |
| Export → import → equal database | **No** (no import) | plan 15 |
| Permissions equal the approved list | Yes | `PrivacyManifestTest` |
| Manifest receivers resolve to real classes (the D25 crash) | **No** | — |
| `AlarmScheduler`, `BootReceiver`, `ReminderAlarmReceiver`, spec parsing | **No** | plan 11 |
| Foreign keys enforced; `Origin` round trip | **No** | — |
| Any ViewModel or UI behavior | **No** | plan 07 |

## 2. Conventions

- Robolectric suites copy the header of `NoteCommandHandlerTest` (in-memory DB,
  `BundledSQLiteDriver`, injected `now` and `newId`).
- Suspend calls that must throw use `try { …; fail() } catch (e: X) {}`.
- "(1a)", "(1b)", "(1c)" = written with that step of plan 01. Tests with no tag pass on today's
  code and go in first.
- Helper: `suspend fun lastAudit(id: String) = db.auditDao().recentFor("note", id, 1).single()`.

## 3. Tests to add in this plan

### 3.1 `NoteCommandHandlerTest`

| ID | Test name | Arrange → Act → Assert |
|---|---|---|
| T-U1 | `undo of soft delete restores the note` | create; softDelete; `undo(lastAudit.id)` → `deletedAt == null`, revision 3. |
| T-U2 | `undo of restore deletes again` (1b) | create; softDelete; restore; undo → `deletedAt != null`; last action `UNDO`. |
| T-U3 | `undo of undo is redo` (1b) | create "a"; update "b"; undo → "a"; undo(lastAudit) → "b". |
| T-U4 | `undo is refused when the note changed since` (1b) | create "a"; update "b" (audit X); update "c"; `undo(X)` throws `UndoStaleException`; text "c"; audit count unchanged. |
| T-U5 | `second undo of the same entry is refused` (1b) | update (X); undo(X); undo(X) throws `UndoStaleException`. |
| T-U6 | `undo restores null raw text as null` (1b) | create with title only; `updateRawText("x")`; undo → `rawText == null`. |
| T-U7 | `undo of unknown audit id throws` | `undo(999)` throws `IllegalArgumentException`. |
| T-U8 | `every undo is audited as UNDO` (1b) | for CREATE, SOFT_DELETE, SET_STATUS: undo → last action `UNDO`. |
| T-R1 | `stale expected revision is rejected` (1b) | create; update "b"; `updateRawText(id, "c", expectedRevision = 1)` throws `NoteConflictException`; text "b". Repeat for `setStatus`, `softDelete`, `updateTitle`. |
| T-R2 | `matching expected revision is applied` (1b) | `updateRawText(id, "b", expectedRevision = 1)` → revision 2. |
| T-N1 | `no-op edits write no audit` | create "a"; `updateRawText(id, "a")`; `setStatus(id, INBOX)` → revision 1, audit count 1. |
| T-D1 | `editing a soft-deleted note throws NoteMissing` | create; softDelete; `updateRawText`, `setStatus` each throw. |
| T-D2 | `soft delete twice is idempotent` (1a) | second call does not throw; audit count 2. |
| T-D3 | `restore of a live note is a no-op` | revision stays 1. |
| T-A1 | `audit snapshots record before and after` | update → `before.revision == 1`, `after.revision == 2`, `after.rawText == "b"`. |
| T-K1 | `updateTitle is audited and undoable` (1b) | title "T", rev 2, action `UPDATE_TITLE`; undo → title null. |
| T-K2 | `snapshot carries image and attachment paths` (1a) | create ATTACHMENT → audit `after` JSON contains the file name. |
| T-PR1 | `approving CATEGORY, TAG, TOPIC and PLACE proposals writes the matching derived row` | one case per kind; origin `ai:<runId>`; state `APPROVED`. |
| T-PR2 | `approving a proposal for a deleted note throws NoteMissing and leaves it pending` | state still `PENDING`. |
| T-PR3 | `approving two proposals for the same person creates one person row` | **Fails today** (A3). Keep it failing-and-ignored with `@Ignore("A3, fixed in plan 12")` so the bug is on record. |

### 3.2 `NoteDaoTest`

| ID | Test name | Assert |
|---|---|---|
| T-G1 | `guarded status, delete and restore reject stale revisions` | each returns 0 with a wrong revision. |
| T-F1 | `fts follows edits` | "alpha" → edit to "beta"; search "alpha" 0, "beta" 1. |
| T-F2 | `fts finds restored notes again` | delete, restore, search → 1. |
| T-F3 | `fts covers transcript and ocr columns` | each found. |
| T-F4 | `fts folds case and diacritics` | "El café" found by "CAFE". |
| T-F5 | `fts integrity check passes after churn` | 50 inserts, 20 edits, 10 deletes; `INSERT INTO notes_fts(notes_fts) VALUES('integrity-check')` does not throw. |
| T-F6 | `prefix search matches a partial last word` (1c) | "oatmeal" found by `sanitize("oat")`. |
| T-F7 | `search results are ranked` (1c) | note with "drift" three times comes before a note with it once. |
| T-L1 | `like fallback treats percent and underscore literally` (1c) | "100%" finds "100% done", not "100 done". |
| T-FK1 | `foreign keys are enforced` | `derivedDao().upsert` for a missing note throws. **Probe:** if it does not throw, stop and report. |
| T-ORD1 | `enabled rules come back in a stable, intended order` | **Fails today** (G1): insert rules with ids "b", "a" in that order; expect insertion order. `@Ignore("G1, fixed in plan 10")`. |

### 3.3 New `FtsQueryTest` (plain JUnit)

| ID | Input | Expected |
|---|---|---|
| T-S1 | `""`, `"   "`, `"\t\n"` | `null` |
| T-S2 | `"\""` | `null` |
| T-S3 | `"oat milk"` | `"\"oat\" \"milk\""` |
| T-S4 | `"a\"b"` | `"\"ab\""` |
| T-S5 | `"AND"`, `"NEAR"`, `"title:x"`, `"^x"` | each quoted, unchanged inside |
| T-S6 | `"oat milk"` | two tokens. **Probe:** if Kotlin's `\s` does not split it, change the regex to `[\\s\\u00A0]+`. |
| T-S7 (1c) | `"\u0000oat"` | `"\"oat\"*"` |
| T-S8 (1c) | 40 tokens | first 12 kept |
| T-S9 (1c) | one 500-char token | cut to 64 chars |
| T-S10 (1c) | `"oat mi"` | `"\"oat\" \"mi\"*"` |
| T-S11 (1c, Robolectric, in `NoteDaoTest`) | `-`, `( )`, `***`, `+`, `{}`, `🙂`, `\u0000`, `a OR`, `'` | `NoteSearch.search` never throws |

### 3.4 Share

| ID | Test name | Assert |
|---|---|---|
| T-C3 | `url followed by a newline and text is not link-only` (1a) | `rawText` equals input; `sourceUrl == "https://a.org"`. |
| T-C4 | `shared text is stored untrimmed` (1a) | `"  hi  "` stays `"  hi  "`. |
| T-C5 | `two urls set no source url` (1a) | `sourceUrl == null`. |
| T-C1 | `styled text share is saved` (1a) | New `ShareReceiverActivityTest` (Robolectric): intent with `SpannableString("styled")`; `countActive() == 1`; activity finishing. |
| T-C2 | `empty share shows a toast and saves nothing` (1a) | `ShadowToast` text equals `share_empty_toast`. |
| T-C6 | `non-SEND intent finishes without saving` | count 0. |

### 3.5 AI (pure JUnit, no model needed)

New `AiProposalValidatorTest`:

| ID | Test name | Assert |
|---|---|---|
| T-AV1 | `evidence must be a literal substring` | paraphrased evidence → dropped; exact → kept. |
| T-AV2 | `evidence match is case-sensitive` | "DYLAN" for note "Dylan" → dropped. |
| T-AV3 | `confidence below 0.5 or above 1 is dropped` | 0.49, 1.01 dropped; 0.50, 1.00 kept. |
| T-AV4 | `unknown kind is dropped` | `"kind":"LINK2"` → dropped. |
| T-AV5 | `malformed json yields an empty result` | `"{"` → no summary, no proposals, no exception. |
| T-AV6 | `summary is dropped for notes of 50 words or fewer` | 50 words → null; 51 → kept. |
| T-AV7 | `blank value or blank evidence is dropped` | each dropped. |

### 3.6 Other

| ID | Suite | Assert |
|---|---|---|
| T-O1 | `OriginTest` (plain) | `parse(encode(x)) == x` for four kinds; `parse("bogus") == null`. |
| T-MAN1 | `ManifestComponentsTest` (Robolectric) | For every `<receiver>`, `<activity>`, `<provider>`, `<service>` of this package in `PackageManager.getPackageInfo(…, GET_RECEIVERS or GET_ACTIVITIES or GET_PROVIDERS or GET_SERVICES)`: `Class.forName(info.name)` succeeds. This is the regression test for the D25 wrong-path crash. |
| T-MAN2 | same | Exported components are exactly `MainActivity` and `ShareReceiverActivity`. |
| T-E1…E4 | `NoteExporterTest` | empty database exports validly; unicode and newlines survive; null fields stay null; unknown keys are ignored on decode. |
| T-ARCH | `ArchitectureTest` | plan 01 W1, added when the last DAO write leaves the ViewModel. |

## 4. Tests specified in other plans

| Plan | Tests |
|---|---|
| 07 | `MainViewModelTest` (error mapping, search flow, draft) |
| 10 | rule ordering, re-run, user override, derived undo, resolver |
| 11 | reminder scheduling decisions, boot catch-up, spec parsing |
| 12 | night-run state machine, job leases, dedupe, vocabulary uniqueness |
| 14 | T-MIG1…T-MIG7 |
| 15 | T-E5…T-E12 export → import round trip |
| 17 | seed generator, FTS timing |

## 5. Order

1. Add every untagged test (about 25). All must pass on today's code except the two `@Ignore`d
   bug records and the two probes.
2. Add the tagged tests with plan 01 steps 1a–1c.
3. After each step, update the table in `docs/TEST_RESULTS.md` with the count CI reports.
