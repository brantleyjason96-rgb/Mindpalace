# Plan 12 — On-device AI and the night loop, hardening

**State today (D13, D22–D24):** llama.cpp is vendored and built through the NDK. `AiEngine` loads
a user-supplied GGUF file and runs one grammar-constrained completion per note.
`AiProposalValidator` drops any proposal whose evidence is not a literal substring of the note.
`AiNightRunner` writes `ai_index`, `proposals`, and `ai_runs`. `AiNightWorker` runs daily while
charging and idle. `AiProposalsScreen`, `AiSetupScreen` (model picker, "Run now"), and
`AiRunHistoryScreen` exist. Approval goes through `NoteCommandHandler.approveProposal`.
**This plan:** make runs crash-safe and repeatable with the `jobs` lease table, stop duplicates,
fix vocabulary duplication, and prove with tests that AI cannot touch note text.
**Sign-off needed:** none; no new permission. One owner decision: OD-6.
**Schema change:** v4 → v5.

## 1. Model options

The owner already chose and measured a model (D13, D22). This table is the comparison the brief
asked for; **only the first row was measured on the owner's phone.** The rest are published or
remembered figures and are marked as such.

| Option | Model file | Speed | APK cost | Fits this app? |
|---|---|---|---|---|
| **llama.cpp + Qwen3 4B Q4_K_M (current)** | 2.32 GiB, sideloaded | **14.1 tok/s generate, 25.3 tok/s prompt on the S26 Ultra (measured, D22)** → about 22 s per note, 150–250 notes/hour | Native libraries only; exact size not yet measured (plan 17) | Yes. Works with no network and no Google services. |
| llama.cpp + smaller GGUF (Llama 3.2 1B ≈ 0.8 GB, Qwen 1.5–1.7B ≈ 1.1 GB, Llama 3.2 3B ≈ 2.0 GB, Phi-4-mini ≈ 2.5 GB) | as listed (from memory, unverified) | not measured; expect roughly 2–4× faster for the 1–2B models | same as current | Yes, drop-in. Lower label quality is likely; test on real notes before switching. |
| LiteRT-LM + Gemma 3 1B (int4) | 529 MB (published) | 2,585 tok/s prompt on S24 Ultra (published); generate speed not found | New Google runtime library; permissions it merges not verified | Possible. Different file format, so a second runtime to maintain. |
| LiteRT-LM + Gemma 4 E2B / E4B | 2.58 / 3.65 GB (community figures) | 52 / 22 tok/s generate on S26 Ultra GPU (one community post) | as above | Faster if the figures hold. Unverified; would need its own spike like D22. |
| Gemini Nano through ML Kit GenAI (AICore) | none in the app; the system holds the model | not found | Small client library; requires Google Play services | **No.** Foreground-only, so it cannot run a night loop; input capped near 4,000 tokens; ties the app to Google services. |

**Recommendation:** keep the current choice. Make the model name and prompt version part of the
job key (§3) so a smaller GGUF can be tried later without code changes. MediaPipe's LLM API is
reported as maintenance-only; do not start on it.

Storage cost to the owner: one model file (0.5–3.7 GB) in the app's external files folder.
Uninstalling the app deletes it (see audit B1).

## 2. Problems being fixed (audit A1–A5)

| ID | Problem |
|---|---|
| A1a | No `catch` in `AiNightRunner.run`; a crash or cancel leaves `ai_runs.outcome = 'RUNNING'` forever. |
| A1b | `since` = last run's **finish** time; a note edited during a run is never picked up. |
| A1c | Heat is checked once at the start. Nothing stops a run between notes. |
| A1d | `FAILED_LOAD` → `Result.retry()`; a bad model retries forever. |
| A1e | The `jobs` table and its lease columns are unused; an interrupted run starts over. |
| A2 | No duplicate check: repeat runs duplicate pending proposals; rejected ones return; `ai_index` rows accumulate. |
| A3 | `insertIfAbsent` with a fresh UUID always inserts → duplicate people, places, topics. Categories invented by the model are accepted. `rejectProposal` ignores state. Approval cannot be undone. |
| A4 | `AiEngine` has no lock; the nightly and manual runs can overlap. |
| A5 | Model copy overwrites the old file in place with no validation or failure handling. |

## 3. Design

### 3.1 Engine seam (for tests)
```kotlin
interface CompletionEngine {
    suspend fun load(modelPath: String): Boolean
    suspend fun complete(system: String, user: String, grammar: String, maxTokens: Int = 400): String
    suspend fun unload()
}
```
`AiEngine` implements it (wrap its current functions; it keeps the `Context` it needs from a
constructor or `init(context)`).
`AiNightRunner(database, engine, now, newId, promptVersion = AiPrompt.PROMPT_VERSION)` takes the
interface; `promptVersion` is a parameter so a test can simulate a prompt change. Tests pass a `FakeEngine` that returns canned JSON. No test loads a real model.

### 3.2 Jobs and leases
One job per note revision, prompt, and model:
`idempotency_key = "ai.propose|<noteId>|<revision>|<PROMPT_VERSION>|<modelName>"`,
`kind = "ai.propose"`, `payload = {"noteId": "…", "revision": N}`.

New `JobDao` queries:
```sql
-- claim: returns 1 only for the caller that wins
UPDATE jobs SET state = 'RUNNING', attempts = attempts + 1, lease_until = :leaseUntil, updated_at = :now
WHERE id = :id AND (state IN ('PENDING','RETRY') OR (state = 'RUNNING' AND lease_until < :now))

-- next candidates, oldest first, including expired leases
SELECT * FROM jobs WHERE kind = :kind AND (state IN ('PENDING','RETRY')
       OR (state = 'RUNNING' AND lease_until < :now)) ORDER BY created_at ASC LIMIT :limit

UPDATE jobs SET state = :state, lease_until = NULL, last_error = :error, updated_at = :now WHERE id = :id
```
Lease length: 5 minutes (one note takes about 22 s). `attempts >= 3` → `FAILED`, never retried.

### 3.3 One run, step by step (`AiNightRunner.run`)
1. `Mutex.tryLock()` on a process-wide lock. Busy → return `SKIPPED_BUSY` without an `ai_runs` row.
2. Mark any old `ai_runs` row still `RUNNING` as `ABANDONED`.
3. Insert the `ai_runs` row (`RUNNING`).
4. **Enqueue:** for every active note with `updated_at > since` (where `since` is the **start**
   time of the last `SUCCEEDED` run, or 0), `enqueue` a job (`INSERT OR IGNORE`). A note already
   processed at this revision with this prompt and model is skipped by the unique key.
5. Load the model. Failure → `FAILED_LOAD`, release, return.
6. Loop: take the next candidate → **check `shouldStop()`** → claim it → re-read the note; if its
   revision differs from the payload, mark the job `SUCCEEDED` with `last_error = "superseded"`
   and continue → complete → validate → **write results in one transaction** (§3.4) → mark
   `SUCCEEDED`. An exception on one note → `RETRY` with the message; the loop continues.
7. `finally`: unload the model; write the outcome (`SUCCEEDED`, `STOPPED:<reason>`, or
   `FAILED:<exception class>`); release the lock. Cancellation is caught, recorded as
   `STOPPED:cancelled`, and re-thrown.

`shouldStop: () -> String?` is passed in. The worker supplies: `isStopped` → "cancelled";
thermal status ≥ MODERATE → "hot"; battery below 20% and not charging → "battery".
Note text longer than 6,000 characters is cut to the first 6,000 for the prompt; evidence is
still checked against the full text.

### 3.4 Writing results (inside the runner, one transaction per note)
- **`ai_index`:** delete this note's existing rows, insert the new one (one current row per note).
- **Proposals — skip when any of these is true:**
  1. a proposal with the same `note_id`, `kind`, and case-insensitive `new_value` exists in any
     state (`PENDING`, `APPROVED`, `REJECTED`, `UNDONE`) — this is the "never re-propose" rule;
  2. `derived` already has that field and value for the note;
  3. kind is `CATEGORY` and the value is not in `categories` (pass the list to the validator).
- New query: `ProposalDao.exists(noteId, kind, value): Boolean`
  (`… AND new_value = :value COLLATE NOCASE`).

### 3.5 Approval, rejection, undo (`NoteCommandHandler`)
- **Vocabulary reuse (A3):** before inserting a topic, person, or place, look it up with the
  existing `byName` query (`COLLATE NOCASE`). Found → reuse; not found → insert. Apply the same
  to `MainViewModel.addTopic/addPerson/addPlace` by moving them into a small
  `VocabCommands` class. **OD-6:** is "same name, ignoring case" the same person? Default yes.
  Anything looser (nicknames, "Dylan M." vs "Dylan") always creates a new row; merging stays
  manual and unbuilt (`PLAN.md` §7).
- `rejectProposal`: only from `PENDING`; otherwise no-op.
- New `undoProposal(id)`: only from `APPROVED`; deletes the `derived` row with that note, field,
  value, and `origin = ai:<runId>`; sets `UNDONE`. Vocabulary rows are left in place.
- Approval and undo write a `derived` audit row (plan 10 §3.3) once plan 10 is merged. If plan 10
  is not merged yet, skip the audit and leave a `TODO(plan 10)`.

### 3.6 Worker (`AiNightWorker`)
- `FAILED_LOAD` → `Result.failure()`. `STOPPED:*` → `Result.retry()`. `SKIPPED_BUSY` →
  `Result.success()`.
- Manual and nightly work keep separate names; the mutex makes overlap safe.
- Remove the start-only thermal check (now in `shouldStop`).

### 3.7 Model file (`AiSetupScreen`, new `ai/ModelInstaller.kt`)
`suspend fun install(source: InputStream, sizeBytes: Long, target: File): InstallResult`:
check the first four bytes are `GGUF`; check free space ≥ size + 200 MB; copy to
`model.gguf.part`; on success rename over `model.gguf`; on any failure delete the `.part` file
and leave the old model untouched. The screen shows progress and the result, and always clears
its spinner.

### 3.8 Schema v5 (`MIGRATION_4_5`)
`ALTER TABLE jobs ADD COLUMN last_error TEXT`. Nothing else. (`ai_runs.outcome` is free text and
already holds the new values.)

## 4. The guarantee: AI never edits note text

Enforced three ways; all three are tests.

| # | Guard | Test |
|---|---|---|
| 1 | Code in `ai/` has no path to note writes. | `ArchitectureTest.ai package cannot write notes`: no file under `ai/` contains `NoteCommandHandler`, `updateRawText`, `updateTitle`, `noteDao().insert`, or `Guarded(`. |
| 2 | A run leaves every note row identical. | T-AI9 below. |
| 3 | The only way an AI suggestion reaches a note is `approveProposal`, and it writes `derived` only. | T-AI12 below. |

Shared web text can contain instructions aimed at the model. The worst outcome is a misleading
proposal, which still needs an evidence quote from the note and the owner's approval. Keep the
note in the **user** message only, never in the system prompt (already true).

## 5. Tests

`AiNightRunnerTest` (Robolectric, in-memory DB, `FakeEngine`):

| ID | Test name | Assert |
|---|---|---|
| T-AI1 | `a successful run records SUCCEEDED and one job per note` | 2 notes → 2 `SUCCEEDED` jobs; run outcome `SUCCEEDED`; `notes_processed == 2`. |
| T-AI2 | `a second run with no changes does nothing` | engine `complete` call count stays 2. |
| T-AI3 | `an edited note is processed again` | edit one note → 1 more call. |
| T-AI4 | `a note edited while a run is in progress is picked up next time` | edit note 2 inside the fake engine's callback for note 1 → next run processes note 2's new revision. |
| T-AI5 | `an engine exception marks the job RETRY and the run continues` | note 1 throws, note 2 fine → run `SUCCEEDED`, job 1 `RETRY` with `last_error`, attempts 1. |
| T-AI6 | `three failures mark the job FAILED` | three runs → `FAILED`; a fourth run does not call the engine for it. |
| T-AI7 | `shouldStop ends the run between notes` | stop after the first note → outcome `STOPPED:hot`; job 2 still `PENDING`; model unloaded. |
| T-AI8 | `a stale RUNNING run is marked ABANDONED` | pre-insert a `RUNNING` row → `ABANDONED` after the next run. |
| T-AI9 | `a run never changes any note row` | snapshot all `notes` rows (every column) before and after → equal. |
| T-AI10 | `load failure records FAILED_LOAD and enqueued jobs stay pending` | fake `load` returns false. |
| T-AI11 | `overlapping runs: the second returns SKIPPED_BUSY` | start run 1 with a suspended fake engine; start run 2 → `SKIPPED_BUSY`. |
| T-AI13 | `a rejected proposal is not proposed again` | reject; edit the note; run → no new proposal with that kind and value. |
| T-AI14 | `repeat runs do not duplicate pending proposals` | second run with a runner built with `promptVersion = "v2"` (forces re-processing) → still one proposal. |
| T-AI15 | `a category outside the list is dropped` | fake returns category "Finance" not in `categories` → no proposal. |
| T-AI16 | `ai_index holds one row per note` | process a note twice (edit between) → 1 row. |
| T-AI17 | `an expired lease is reclaimed` | job `RUNNING` with `lease_until` in the past → processed. |

`JobDaoTest`: T-JB1 `claim succeeds once` (two claims → 1 then 0); T-JB2 `enqueue ignores a
duplicate key`.

`NoteCommandHandlerTest`: T-AI12 `approving a proposal changes derived only` (note row equal
before and after; no `note` audit row added); T-PR3 from plan 02 un-ignored (two approvals for
"Dylan" → one person row); T-PR4 `rejecting an approved proposal is a no-op`; T-PR5
`undoProposal removes the derived row and sets UNDONE`.

`ModelInstallerTest` (plain JUnit, temp files): T-MI1 non-GGUF bytes → `NotAModel`, old file
intact; T-MI2 a stream that throws midway → old file intact, no `.part` left; T-MI3 valid →
replaced.

`MigrationTest`: `migrate 4 to 5 adds jobs.last_error`. Bump `LATEST`.

## 6. UI changes (`MainActivity.kt`: `AiSetupScreen`, `AiRunHistoryScreen`, `AiProposalsScreen`, `ProposalRow`)

1. Run history shows readable outcomes from string resources ("Stopped: phone was warm",
   "Model could not be loaded"), not the raw `outcome` text (3015).
2. Proposals: show the kind through `stringResource`, not the enum name (3125); add
   "Approve all for this note"; show approved proposals from the last 7 days with an Undo button.
3. Setup: progress and result from `ModelInstaller`; a "Nightly run" switch (cancels or
   schedules the periodic work); the last run's outcome.
4. Use a `LazyColumn` for proposals and run history.

## 7. Files allowed

`ai/*` (plus new `CompletionEngine.kt`, `ModelInstaller.kt`), `core/commands/NoteCommandHandler.kt`
(proposal functions only), new `core/commands/VocabCommands.kt`, `core/db/Daos.kt` (`JobDao`,
`ProposalDao`, `AiIndexDao`, `AiRunDao`), `core/db/AiEntities.kt` (`JobEntity`),
`core/db/Migrations.kt`, `MindPalaceDatabase.kt` (version), `MindPalaceApplication.kt`,
`MainActivity.kt` (the four composables in §6 and the vocabulary functions in `MainViewModel`),
`strings.xml`, tests, generated schema JSON.

**Do not touch:** `app/src/main/cpp/**`, `AiPrompt.GRAMMAR` (changing either needs a new device
spike), reminders, rules, capture.

## 8. Done when

- All tests pass in CI; permission list unchanged.
- On the phone (owner): tap "Run now" twice in a row → no duplicate proposals; unplug during a
  nightly-style run → history shows "Stopped", and the next run continues where it left off.
- `DECISIONS.md`: job key, lease length, dedupe rule, OD-6 answer. `CHANGELOG.md`; version bump.

## 9. Stop and report if

- `AiEngine` cannot implement the interface without changing the JNI signatures.
- A test needs a real model file.
