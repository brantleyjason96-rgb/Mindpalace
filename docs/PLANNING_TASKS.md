# Planning tasks: Fable reviews, a faster model executes

**How to use this.** Fable does the review and planning only. It reads the code and docs and writes one plan file per task under `docs/plans/`. A faster model (Sonnet or Haiku) then executes each plan.

Each plan should be written so the faster model never has to make a judgment call:
- exact file paths
- the interfaces to create
- the tests to write
- a "done when" check
- an explicit list of things not to touch

**Standing constraints for every plan** (from `README.md` and `docs/DECISIONS.md`):
- No new permissions without owner sign-off (D4, tested by `PrivacyManifestTest`).
- `NoteCommandHandler` is the only writer of canonical data.
- The original note text is never altered.
- Any schema bump needs a migration and a `MigrationTest`.
- `./gradlew testDebugUnitTest` must stay green (27 tests today).

---

## A. Review the current state (do first)

1. **Audit Phase 0 + Phase 1 backend.** Read `core/commands`, `core/db`, `capture`, `export`. List bugs, gaps against the Mind Palace build plan, and anything that will cause trouble when a UI is added. Output a prioritized punch list.
2. **Check the test coverage claim (27/27).** Map which behaviors are tested and which are not: undo of every action, revision conflicts, FTS sanitizer edge cases, export round-trip. Output a list of missing tests, each specified well enough to write mechanically.
3. **Re-verify the toolchain pins in D2** (AGP 9.5.1, Gradle 9.9.0, Kotlin 2.3.21, KSP, Room 3 3.0.3). Flag anything stale or risky. Do not upgrade anything; just report.
4. **Resolve the missing plan reference.** Code comments cite plan sections (§6, §14, §15, and so on), but the plan is not in the repo. Decide whether to add a `docs/PLAN.md` summary so a faster model can resolve the references.

## B. Close Checkpoint 0

5. **Turn the device checklist into a runnable script and doc.** Cover the `adb` install, the airplane-mode check, and the reboot-persistence check. Output is a checklist the owner can run in about 10 minutes, plus pass/fail logging into `docs/TEST_RESULTS.md`.
6. **Add a CI build.** Plan a GitHub Actions workflow that runs `testDebugUnitTest` and `assembleDebug` (JDK 17+, Gradle cache, upload the APK). Note any proxy or network limits that affect the plan.

## C. Plan the next build phases (one plan file each)

7. **Frontend replacement for `MainActivity` (Compose).** Plan the screens (Inbox list, note detail/edit, search, undo snackbar), ViewModel boundaries against `AppContainer`, and navigation. Specify state classes and previews so a faster model can build it screen by screen.
8. **Voice capture.** On-device speech-to-text only, and this needs a mic permission, which means owner sign-off. Plan the permission flow, a transcript field on notes, and what happens when recognition fails (save audio or text first, never lose input).
9. **Camera/OCR capture.** On-device OCR only, using the existing `ocr_text` column. Plan the permission decision, the pipeline, and the tests.
10. **Rules engine (Phase 3).** Plan the rule model (the `rules` table is already there), evaluation order, how output is written to `derived` with `origin = rule:<id>`, and undo through the command handler. Include test cases.
11. **Reminders, geofences, calendar (Phase 4).** List the permissions each needs, the offline constraints, and the migration to the `reminders`, `places`, and `people` tables. Flag every owner sign-off point.
12. **On-device AI and night loop (Phase 5).** Compare model options by size, speed, and APK/storage cost. Plan the `jobs` lease system, `ai_runs`, `proposals` (AI suggests, the user approves), and the guarantee that AI never edits raw text.
13. **Widgets and quick capture.** Plan a home-screen widget and a quick-capture entry point that follow save-first.

## D. Cross-cutting plans

14. **Migration test framework.** Plan the `MigrationTest` harness against `app/schemas/.../1.json` so every later schema bump has a template.
15. **Export/import.** Exports exist, but import doesn't. Plan the JSON import path (round-trip, duplicate handling, audit entries) since `allowBackup=false` means export is the only migration route.
16. **Accessibility and multilingual pass.** Plan screen-reader labels, font scaling, and RTL, since `supportsRtl` is already on.
17. **Performance and storage.** Plan checks for APK size (about 36 MB, per D3), FTS5 query speed at 10k+ notes, and DB growth from the audit table. Include a seed-data generator for testing.

## E. Handoff package for the faster model

18. **Write `docs/plans/INDEX.md`.** Give the execution order, dependencies between plans, which plans need owner sign-off before starting, and a standard prompt template for the faster model ("read plan N, change only these files, run these tests, stop and report if anything is ambiguous").
19. **Define review gates.** After each executed plan, a short checklist (tests green, privacy test green, no new permissions, `DECISIONS.md` and `CHANGELOG.md` updated) that Fable or the owner can run before merging.

---

**Suggested order:** 1 → 2 → 4 → 5 → 6 → 14 → 7 → 15 → 10 → 8 → 9 → 11 → 12 → 13 → 16 → 17 → 18 → 19. Tasks 3 and 4 can run in parallel with 1 and 2.
