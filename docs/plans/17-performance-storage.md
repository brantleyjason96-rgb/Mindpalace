# Plan 17 — Performance and storage checks

**State today:** nothing is measured. D3 recorded an APK of about 36 MB at v0.1. Since then the
app gained ML Kit text recognition (bundled model), ML Kit digital ink, WorkManager, and
llama.cpp native libraries for two ABIs with all CPU variants, packaged with
`useLegacyPackaging`. The first green CI run (2026-11-11) uploaded a debug APK artifact of about **55 MB zipped**;
the exact APK byte count is not recorded yet (step 1 below).
**Goal:** known numbers, written budgets, and cheap checks that catch a regression.
**Sign-off needed:** none. **Schema change:** none (one optional index, §4.3, would be a bump).

## 1. Budgets

Starting points. Replace each "target" with a measured baseline after the first run, and keep
the rule "fail on more than X% worse".

| Metric | Target | How measured | Gate |
|---|---|---|---|
| Debug APK size | record baseline; then ≤ baseline + 10% | CI: `stat` on the built APK | CI step fails |
| Largest items in the APK | listed | `unzip -lv app-debug.apk \| sort -k1 -n -r \| head -25` | report only |
| Capture-to-saved (`commands.create`) with 10k notes | < 50 ms on the phone | in-app perf screen | report |
| Share-to-saved, cold process | < 2,000 ms | `scripts/checkpoint0.sh` step 7a | script FAIL |
| Search, 2-word query, 10k notes | < 50 ms on the phone; < 500 ms in CI | perf screen; JVM test | CI ceiling only |
| Search, 1-letter prefix (`a*`), 10k notes | < 150 ms on the phone | perf screen | report |
| Timeline first page (50 items) with 10k notes | < 100 ms | perf screen | report |
| Note detail open | < 100 ms | perf screen | report |
| Cold start to first frame | < 1,500 ms | `adb shell am start -W` in the script | report |
| Database size at 10k notes with 3 edits each | record | perf screen | report |
| `audit` share of database size | record; investigate above 60% | perf screen | report |
| Night run | about 22 s per note (D23) | `ai_runs` | existing |
| Git repository checkout | record; llama.cpp source is 70 MB, 3,855 files | `du -sh` | report |

CI machines are slow and noisy. CI timing tests use ceilings ten times the phone target so they
catch only gross regressions (an accidental full scan), never small drift.

## 2. Seed-data generator

`app/src/debug/java/com/mindpalace/app/debug/SeedData.kt` — in the **debug** source set, so it
is never in a release build. Unit tests run against the debug variant and can use it.

```kotlin
data class SeedSpec(
    val notes: Int = 10_000,
    val seed: Long = 42,                 // same seed → identical data
    val editsPerNote: Int = 3,           // drives audit growth
    val deletedShare: Double = 0.03,
    val sources: Map<NoteSource, Double> = mapOf(TEXT to .6, VOICE to .2, SHARE to .1, PHOTO to .1),
    val wordsMin: Int = 5, val wordsMax: Int = 120,
    val spanMillis: Long = 365L * 24 * 3600 * 1000,   // spread created_at over a year
    val rules: Int = 8, val remindersShare: Double = 0.02, val linksShare: Double = 0.05,
)
object SeedData {
    /** Realistic path: every note and edit goes through NoteCommandHandler. Slow; use for ≤ 1,000. */
    suspend fun viaCommands(commands: NoteCommandHandler, spec: SeedSpec, now: () -> Long): SeedReport
    /** Fast path: batched inserts of notes, derived and audit rows, 500 per transaction. */
    suspend fun bulk(db: MindPalaceDatabase, spec: SeedSpec): SeedReport
    fun text(random: Random, words: Int): String
}
data class SeedReport(val notes: Int, val auditRows: Int, val elapsedMs: Long)
```

- **Text:** drawn from a fixed word list in the same file: about 400 words mixing classroom
  vocabulary (quiz, rubric, seismograph, isobar, pacing, lab), errands, names, and a share of
  Spanish and accented words (`café`, `evaluación`, `niño`) so diacritic folding is exercised.
  Word frequency is skewed (a few very common words, a long tail) so search hit counts are
  realistic. 2% of notes contain a URL. No real personal data.
- **Marker words:** `SEEDRARE` in exactly 1 note, `SEEDCOMMON` in about 20% of notes, so tests
  can assert exact and approximate hit counts.
- `bulk` must produce rows that the real code accepts: same enum names, valid `NoteSnapshot`
  JSON in `audit`, revision = edits + 1, an `Inbox` system row per note. A test compares one
  `bulk` note with one `viaCommands` note field by field (T-PF2).
- `bulk` writes through DAOs. That is allowed here: the file is under `src/debug`, and
  `ArchitectureTest` scans `src/main` only.

**On the phone:** a "Developer" card on the Settings screen, compiled only in debug
(`if (BuildConfig.DEBUG)`), with:
- "Seed 10,000 test notes" — refuses unless the database has fewer than 50 notes, and says so:
  seeding the owner's real database would bury real notes. Recommended use is a second user
  profile or an emulator.
- "Run performance check" — runs §3 and shows the table; "Copy results".
- "Delete seeded notes" — soft-deletes notes whose title starts with `[seed]`.

Requires `buildFeatures { buildConfig = true }` in `app/build.gradle.kts`.

## 3. Measurements

`app/src/debug/java/com/mindpalace/app/debug/PerfCheck.kt`:
```kotlin
data class PerfRow(val name: String, val medianMs: Double, val p95Ms: Double, val runs: Int)
object PerfCheck { suspend fun run(container: AppContainer): List<PerfRow> }
```
Each measurement runs 3 warm-up passes then 20 timed passes with `System.nanoTime()`:

| Name | Operation |
|---|---|
| `create` | `commands.create` of a 40-word note |
| `search.rare` | `NoteSearch.search("SEEDRARE")` |
| `search.common` | `NoteSearch.search("SEEDCOMMON quiz")` |
| `search.prefix1` | `NoteSearch.search("s")` |
| `search.like` | `noteDao.searchLike("seismograph", 100)` (the fallback, for comparison) |
| `list.first50` | `noteDao.observeList(null, 50).first()` |
| `list.inbox` | `noteDao.observeList(INBOX, 50).first()` |
| `detail` | `observeById` + audit (50) + derived for one note |
| `vault.counts` | effective-category counts (plan 10 §3.1) |
| `export.zip` | `Exporter.writeZip` to a null stream (plan 15), once |

Storage report (same screen):
```sql
PRAGMA page_count; PRAGMA page_size; PRAGMA freelist_count;
SELECT count(*), sum(length(coalesce(raw_text,''))+length(coalesce(transcript,''))+length(coalesce(ocr_text,''))) FROM notes;
SELECT count(*), sum(length(coalesce(before,''))+length(coalesce(after,''))) FROM audit;
```
plus file sizes of the `.db`, `-wal`, `photos/`, `attachments/`, `exports/`, and the model file.
Report "audit bytes ÷ note text bytes". (`dbstat` gives exact per-table pages but may not be
compiled into the bundled SQLite; try it and fall back to the sums above.)

## 4. What to expect, and what to do about it

### 4.1 Audit growth
Each edit stores two full snapshots of the note's text. Rough size: a 300-character note is
about 0.5 KB per snapshot → about 1 KB per edit. 10,000 notes × 3 edits ≈ 30 MB of audit against
about 3 MB of note text. That is acceptable on a phone and is the price of full undo.
It becomes a problem only if something saves on every keystroke: the editor must save on an
explicit action or on leaving the screen (it does today; plan 07 keeps it so).
**If** the measured ratio is far above 10×, the options (owner decision, not automatic) are:
store `before` only (the next row's `before` is this row's `after`), or compress old rows. Never
delete audit rows silently (`PLAN.md` §3.7).

### 4.2 Search
FTS5 with an external-content table is fast for whole words. A one- or two-letter prefix has to
walk many terms. If `search.prefix1` misses its budget, add `prefix='2 3'` to the FTS table
(Room: `@Fts5(prefix = [2, 3])`), which is a schema bump with an index rebuild, **or** require
two characters before searching (no schema change; do this first).
Ordering by `bm25` on a common word sorts every match; the query already has `LIMIT`.

### 4.3 Lists
`observeList` orders by `created_at DESC` with a `LIMIT`; the `created_at` index covers it. The
status-filtered list would use a composite index `(status, created_at)`; add it only if
`list.inbox` misses its budget (schema bump; follow plan 14).

### 4.4 APK size
Likely top items: llama.cpp `.so` files (several CPU variants × two ABIs), `libsqliteJni.so`,
ML Kit models. Options if size matters, each an owner decision:
- drop `x86_64` from release builds (emulator only);
- build fewer llama.cpp CPU variants (needs a new device spike, plan 12 forbids touching the
  native build otherwise);
- `isMinifyEnabled = true` for release (shrinks Kotlin/Compose code; needs keep rules for
  kotlinx.serialization and JNI names, and a full regression pass).

## 5. Tests (CI)

`SeedDataTest` (Robolectric, in-memory DB):

| ID | Test name | Assert |
|---|---|---|
| T-PF1 | `the same seed produces the same data` | two `bulk` runs with 200 notes → identical lists of (id-independent) text and status. |
| T-PF2 | `bulk rows match what commands would write` | one note each way → same columns populated, valid `NoteSnapshot` JSON, `Inbox` derived row, revision = edits + 1. |
| T-PF3 | `marker words have the promised counts` | `SEEDRARE` → 1 hit; `SEEDCOMMON` → between 15% and 25% of notes. |
| T-PF4 | `fts integrity holds after a bulk seed` | integrity-check does not throw. |

`PerfSmokeTest` (Robolectric, file-backed DB in a temp dir, 10,000 notes via `bulk`; annotate
with a JUnit category or name suffix so it can be excluded locally):

| ID | Test name | Ceiling |
|---|---|---|
| T-PF5 | `seeding 10k notes completes` | 120 s |
| T-PF6 | `two-word search at 10k notes` | median < 500 ms |
| T-PF7 | `first page of the list at 10k notes` | median < 500 ms |
| T-PF8 | `search returns at most the limit and never loads all notes` | result size ≤ 100 |
| T-PF9 | `audit bytes per edit stay under 4x the note text` | computed from the storage sums |

Each prints its numbers with a `PERF` prefix so they can be read from the CI log.

## 6. CI changes (`.github/workflows/android.yml`)

After "Build debug APK":
```yaml
      - name: APK size
        run: |
          size=$(stat -c %s app/build/outputs/apk/debug/app-debug.apk)
          echo "APK bytes: $size" | tee -a "$GITHUB_STEP_SUMMARY"
          unzip -lv app/build/outputs/apk/debug/app-debug.apk | sort -k1 -n -r | head -25 >> "$GITHUB_STEP_SUMMARY"
          base=$(grep -oE 'apk_debug_bytes: [0-9]+' docs/PERF_BASELINE.md | grep -oE '[0-9]+' || echo 0)
          if [ "$base" -gt 0 ] && [ "$size" -gt $((base * 110 / 100)) ]; then
            echo "APK grew more than 10% over the baseline ($base)"; exit 1
          fi
```
`docs/PERF_BASELINE.md` (new) holds `apk_debug_bytes: <n>` and the table of phone measurements
with date, commit, and device. Raising the baseline is a deliberate edit with a `DECISIONS.md`
line.

## 7. Steps

| Step | What | Files |
|---|---|---|
| 1 | Read the APK size from the latest green CI run; create `docs/PERF_BASELINE.md`; add the CI step. | workflow, new doc |
| 2 | `SeedData` + T-PF1…T-PF4. | `src/debug/**`, tests |
| 3 | `PerfSmokeTest`. | tests |
| 4 | `PerfCheck` + the debug "Developer" card. | `src/debug/**`, `SettingsScreen` (one `if (BuildConfig.DEBUG)` block), `app/build.gradle.kts` (`buildConfig = true`) |
| 5 | Owner runs the check on the phone (second profile or emulator) and pastes the table into `PERF_BASELINE.md`. | doc |
| 6 | Only if a budget is missed: apply the matching item in §4 as its own small plan. | — |

Depends on: plan 01 step 1c (`NoteSearch`) and plan 07 step 5 (`observeList`). If those are not
merged, measure `searchFts` and `observeActive` instead and note it in the baseline.

## 8. Done when

- `PERF_BASELINE.md` has the APK size and one full table of phone numbers.
- T-PF1…T-PF9 pass in CI and print their numbers.
- The release build contains no `debug` package classes (check with
  `unzip -l app-release-unsigned.apk` once a release build exists; until then, confirm the files
  are under `src/debug`).

## 9. Stop and report if

- Seeding 10,000 notes takes over 120 s in CI (lower the count to 5,000 and say so).
- Any measurement suggests a full table scan where an index was expected; report the query and
  the number, do not add an index without the plan 14 procedure.
