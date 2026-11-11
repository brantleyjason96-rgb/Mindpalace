# Plans index — handoff package

**Written:** 2026-11-11, against branch `claude/happy-fermi-begmf3` at `d851331`
(versionCode 5, schema v3). That code and these plans are now on `main`.
**Who does what:** the planning model wrote these files. A faster model executes one plan step at
a time. The owner answers the decisions in §3 and runs device checks. The planning model or the
owner runs the review gate (`REVIEW_GATES.md`) before each merge.

## 0. Read this first: the brief was written against an older app

The task list (`docs/PLANNING_TASKS.md`) describes the app as it was on `main` on 2026-11-08: 27 tests, no UI, zero permissions. The code has moved on by fourteen commits since. What
changed for the plans:

| Brief said | Actual state | Effect on the plan |
|---|---|---|
| 27 tests | 49 `@Test` methods; all passed in CI on 2026-11-11 | 02 re-mapped |
| "No new permissions" with a no-`INTERNET` test | 8 permissions signed off, including `INTERNET` (D26); the gate is now an approved list | 06 and the device script check against `docs/PERMISSIONS.md` |
| Plan a Compose frontend | Built: 16 screens in one 4,533-line file | 07 is a restructure |
| Plan voice, camera/OCR, rules, reminders, AI | Built (first versions) | 08–12 are hardening plans |
| Plan a migration test harness against `1.json` | Exists; schema is at v3 | 14 closes gaps and gives the template |
| Checkpoint 0 open | Passed on the phone 2026-11-09 (README) | 05 is a repeatable regression run |

## 1. Files

| # | File | Kind | Schema | Needs owner first? |
|---|---|---|---|---|
| — | `../PLAN.md` | Key to `§` references (reconstructed) | — | skim once |
| 01 | `01-audit-punch-list.md` | Audit + five fix steps | — | OD-2, OD-4 (awareness) |
| 02 | `02-missing-tests.md` | Test map + test specs | — | — |
| 03 | `03-toolchain-report.md` | Report only | — | OD-1 |
| 05 | `05-checkpoint-0.md` + `../CHECKPOINT_0.md` + `../../scripts/checkpoint0.sh` | Device checklist | — | run it |
| 06 | `06-ci.md` + `../../.github/workflows/android.yml` | CI (live) | — | OD-2 |
| 07 | `07-frontend-compose.md` | Restructure, 11 steps | — | OD-10 (optional) |
| 08 | `08-voice-capture.md` | Hardening | v7 only if OD-5 = B | **OD-5** |
| 09 | `09-camera-ocr-capture.md` | Hardening | — | OD-9 (optional) |
| 10 | `10-rules-engine.md` | Rules v2 | **v4** | OD-7, OD-8 |
| 11 | `11-reminders-geofences-calendar.md` | A: fixes. B: geofences. C: calendar | **v6** (A) | A: none. **B: SO-B1–B3. C: SO-C1** |
| 12 | `12-on-device-ai-night-loop.md` | Hardening | **v5** | OD-6 |
| 13 | `13-widgets-quick-capture.md` | New feature | — | OD-11 (optional) |
| 14 | `14-migration-test-framework.md` | Tests + template | — | — |
| 15 | `15-export-import.md` | Export v2 + import | — | — |
| 16 | `16-accessibility-multilingual.md` | Pass | — | test-dependency approval; OD-12 |
| 17 | `17-performance-storage.md` | Measurement | — | — |
| — | `REVIEW_GATES.md` | Checklist after every step | — | — |

Task 4 of the brief ("resolve the missing plan reference") is answered by `docs/PLAN.md`: yes,
a summary was added, clearly marked as reconstructed. `docs/FEATURE_GAP_PLAN.md` and
`docs/CAPTURE_GRAFT_PLAN.md` (already in the repo) remain the owner's feature wish lists; where
they overlap with a plan here, the plan here has the detail.

## 2. Execution order

Each row is one unit of work for the faster model: one branch, one pull request or merge, one
review gate. "After" lists hard dependencies.

| Order | Unit | After | Why here |
|---|---|---|---|
| 1 | 02 §5 step 1 — add the tests that pass today | — | Safety net before anything changes. |
| 2 | 01 step 1a — share target, `softDelete`, snapshot fields | 1 | Input-loss bugs first. |
| 3 | 01 step 1b — stale-safe undo, `expectedRevision`, `updateTitle` | 2 | Every later plan relies on this undo rule. |
| 4 | 01 step 1c — search sanitizer, ranking, fallback | 2 | |
| 5 | 01 step 1d — crash handling, revert, note-detail load, swipe | 3 | Smallest edits to the big file, before it is split. |
| 6 | 01 step 1e — bring `CLAUDE.md`, README, TEST_RESULTS, CHANGELOG up to date | 5 | So the faster model's own brief is true. |
| 7 | 14 — migration tests and template | 1 | Must exist before the first new schema version. |
| 8 | 07 steps 0–4 — split files, navigator, errors, undo controller | 5, 3 | Later plans edit small files instead of one huge one. |
| 9 | 11 Part A — reliable time reminders (**v6** in the plan; see note) | 7, 8 | P0: reminders may never fire today. |
| 10 | 10 — rules v2 (**v4**) | 7, 8 | |
| 11 | 12 — night loop hardening (**v5**) | 7, 8; 10 for derived audit | |
| 12 | 08 — voice (option A unless OD-5 says B) | 8 | |
| 13 | 09 — camera, OCR, handwriting, attachments | 8, 12 (`DraftStore`) | |
| 14 | 07 steps 5–10 — per-screen ViewModels, previews, `ArchitectureTest` | 8–13 | Independent per screen; can interleave with 9–13. |
| 15 | 15 — export v2 and import | 9, 10, 11 | Format should cover their columns. |
| 16 | 13 — widget and quick capture | 12 (`DraftStore`), 8 | |
| 17 | 16 — accessibility and multilingual | 14 | Needs the split files and stateless screens. |
| 18 | 17 — performance and storage | 4, 14 | Measures the final queries. |
| 19 | 11 Part B, then C | sign-off | Only after the owner signs off the permissions. |

**Schema numbers:** the plans name v4 (rules), v5 (AI), v6 (reminders), v7 (voice, optional).
Order 9 runs reminders before rules because it is the more urgent fix. **Whoever goes first
takes v4**; each later one takes the next free number and renames its migration
(`MIGRATION_3_4`, `MIGRATION_4_5`, …) accordingly. The plan 14 template handles this.

**Compared with the order suggested in the brief** (1 → 2 → 4 → 5 → 6 → 14 → 7 → 15 → 10 → 8 → 9
→ 11 → 12 → 13 → 16 → 17): the audit fixes and reminders move up because they are data-loss or
does-not-work bugs; export/import moves after the schema plans so the format is designed once.

## 3. Owner decisions and sign-offs

Nothing below blocks orders 1–8.

### 3.1 Permission sign-offs (hard stops; `CLAUDE.md` rule 2)

| ID | Permission | Plan | Needed for |
|---|---|---|---|
| SO-B1 | `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` | 11 B | Place reminders that fire with the app closed |
| SO-B2 | (engine choice: framework alerts or Play Services) | 11 B | Same; D14's open question |
| SO-B3 | (how a place gets coordinates) | 11 B | Same |
| SO-C1 | `READ_CALENDAR` | 11 C | Calendar-aware reminders (optional) |

No other plan adds a permission. If any step seems to need one, the faster model stops.

### 3.2 Decisions

| ID | Question | Default if unanswered | Plan |
|---|---|---|---|
| OD-1 | kotlinx-serialization 1.12.0 was built for Kotlin 2.4; the project uses Kotlin 2.3.21. Pin 1.11.0, move Kotlin up, or leave? Also KSP 2.3.13 has a known regression; pin 2.3.12? | Leave both; CI is green | 03 |
| OD-2 | Which build is the one installed on the phone: Mac only, or a shared signing key so CI builds can update it? | Mac only; CI APK is never installed over the app | 06 |
| OD-3 | Should cable phone-to-phone transfer carry the notes? (Add `dataExtractionRules` either way.) | Not decided; nothing changes | 01 Z2 |
| **OD-4** | `INTERNET` (added for the handwriting model) also lets ML Kit's usage reporting reach Google, and the in-app "100% on-device" wording is no longer exact. Options: (a) accept and reword; (b) first capture the phone's traffic to see what is sent; (c) drop ML Kit handwriting, use the Samsung keyboard's pen-to-text, and remove `INTERNET` again. | (b), then decide | 01 P1 |
| **OD-5** | Voice: when recognition fails, keep only the text recognized so far (A), or also keep the audio (B, overturns D15), or bundle a recognizer (C)? | A | 08 |
| OD-6 | Is a person, place, or topic with the same name (ignoring case) the same one? | Yes; anything looser stays separate | 12 |
| OD-7 | Category precedence: you > approved AI > rule > system? | That order | 10 |
| OD-8 | "Manual category is permanent" (D25): is "your choice always wins, rules still record theirs" acceptable, or must rules skip those notes? | Always wins | 10 |
| OD-9 | Should OCR read scripts other than Latin? Which? | Latin only | 09 |
| OD-10 | Keep the hand-written back stack or adopt Navigation 3? | Hand-written; no new dependency | 07 |
| OD-11 | May the home-screen widget ever show note text? | No | 13 |
| OD-12 | Interface languages to translate into. | English only | 16 |
| — | Approve two **test-only** Compose UI test libraries for accessibility tests? | Not added; manual checks only | 16 |

## 4. How the faster model works in this repo

1. **It cannot build here.** Claude cloud sessions have no Android SDK and cannot reach Google's
   Maven host (`06-ci.md` §2). The loop is: edit → commit → push → wait for the "Android build"
   workflow → read the result. Never claim tests pass without a green CI run or the owner's Mac
   output.
2. **One step per session.** A step is one row of a plan's own step table, or one row of §2.
3. **Only the files the plan lists.** Every plan has a "Files allowed" section.
4. **Stop instead of guessing.** Every plan has "Stop and report if". Any ambiguity, any new
   permission, any new dependency, any schema change the plan did not name → stop.
5. Line numbers in plans 07–13 and 16 refer to `MainActivity.kt` at `d851331`. After plan 07
   step 1 the code moves to `ui/…`; search by composable name.
6. Record decisions as D27 onward in `docs/DECISIONS.md`, in ascending order.

### 4.1 Prompt template

```
You are executing one step of a written plan in the Mind Palace Android repo.

Read first, in this order:
  1. CLAUDE.md
  2. docs/plans/INDEX.md  (section 4)
  3. docs/plans/<NN-plan-file>.md   — you are doing: <step id and name, e.g. "plan 01 step 1b">
  4. docs/plans/REVIEW_GATES.md

Branch: create <branch name> from main. Do not open a pull request unless asked.

Rules:
  - Change only the files listed under "Files allowed" in the plan (plus tests it names).
  - Write the tests the plan specifies for this step, with the given names. Do not weaken,
    skip, or @Ignore a test to get green, except where the plan says to add an @Ignore.
  - Do not add a permission, a Gradle dependency, or a schema change the plan does not name.
  - Never alter note text outside an audited user edit. All canonical writes go through
    core/commands.
  - You cannot build locally. Push, then wait for the "Android build" GitHub Actions run on
    your branch and read its result. If it fails, fix and push again (at most 3 attempts,
    then stop and report).
  - If anything is ambiguous, or a "Stop and report if" condition is met, stop. Do not improvise.

When CI is green:
  - Update docs/DECISIONS.md (next free D-number), docs/CHANGELOG.md, and the count in
    docs/TEST_RESULTS.md, as the plan's "Done when" says.
  - Reply with: what changed (files), tests added (names), the CI run URL, the REVIEW_GATES
    checklist filled in, anything you were unsure about, and the device checks the owner
    must still run.
```

### 4.2 What the owner does after each unit

1. Read the report and the filled-in gate.
2. Pull the branch on the Mac, build, install over the existing app (never uninstall), run the
   plan's device checks or `scripts/checkpoint0.sh`.
3. Commit the appended `docs/TEST_RESULTS.md`. Merge.

## 5. State of verification on 2026-11-11

| Claim | Status |
|---|---|
| Unit tests pass on the current code | **Yes** — the "Unit tests" step passed in CI on Linux (run 43060205130) |
| Debug APK builds in CI (includes the native llama.cpp build) | **Yes** — run 43061606673, every step green, about 14 minutes in total |
| Permission gate and schema-unchanged check | **Yes** — both passed in the same run |
| Audit findings | From reading the code. Two search findings were reproduced on real SQLite. Findings marked "likely" in `01` were not run on a device. |
| Device script | Syntax-checked only; never run against a phone |
| Library facts in `03`, `08`, `09`, `11`, `12`, `13` | From release notes and source manifests where stated; several are marked unverified |
