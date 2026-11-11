# Mind Palace — brief for Claude Code

Read this first. Then read `README.md`, `docs/DECISIONS.md` (D1–D14), and `docs/TEST_RESULTS.md`.

## What this is
Personal Android capture app for Jason (8th-grade Earth Science teacher). Speak, type, share, or
photograph something → **saved instantly (save-first)** → sorted later by plain rules → resurfaced at
the right time → plus an **on-device, offline** AI pass at night. Kotlin, Jetpack Compose, Room 3.

## Non-negotiable rules
1. **Default to no INTERNET permission.** One deliberate, owner-approved exception exists (D26,
   2026-11-10): handwriting capture's ML Kit Digital Ink Recognition model has no bundled/offline
   variant, so INTERNET + ACCESS_NETWORK_STATE are scoped to a one-time-per-language model
   download only — recognition itself still runs on-device, and no note content ever leaves the
   phone. Any OTHER network use still needs the same explicit discussion + sign-off as rule 2,
   not a quiet reuse of this exception. `PrivacyManifestTest`'s approved-permissions list is the
   enforcement point now (see its doc comment).
2. **Never add any permission without Jason's explicit sign-off.** Propose it, say why, wait. When
   approved, record it in `docs/PERMISSIONS.md` and update the manifest test deliberately.
3. **All canonical writes go through `NoteCommandHandler`** (audit + undo). UI never writes via DAOs.
4. **The words in the original note are sacred.** Never rewrite raw text outside an audited user edit.
5. **AI never edits notes.** It writes only to `proposals`; approval applies it through the command
   handler with origin `ai:<run>`. Rules apply with origin `rule:<id>`.
6. Any Room schema change = version bump + committed schema JSON + a migration test.
7. Enums persist by name — never rename a constant; add and migrate.
8. Record each non-obvious choice in `docs/DECISIONS.md` (next ID: D15).

## Current state (verify, don't trust)
- Phase 0 + Phase 1 backend: schema v1 (all tables, FTS5), command handler, share target, exports.
  Last known: 27/27 unit tests green on Jason's Mac (2026-11-08).
- **The main UI (`MainActivity.kt`, `ui/theme/Theme.kt`) was written in a cloud session with no
  Android SDK. It has NEVER been compiled.** First job: sync, build, fix any compile errors, run the
  27 tests, install on the phone.
- Toolchain: AGP 9.5.1, Gradle 9.9.0, Kotlin 2.3.21, compile/target SDK 37, min SDK 33.
  CLI builds need `JAVA_HOME` set to Android Studio's JBR (see README).
- Device: Samsung S26 Ultra. Jason is NOT a professional developer: explain errors plainly, give
  exact commands, one step at a time.

## Commands
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest     # must stay green
./gradlew assembleDebug         # APK in app/build/outputs/apk/debug/
```
Work on branch `claude/happy-fermi-begmf3`. Commit in small steps. Don't open PRs unless asked.

## Screens still to build (in this order)
1. **Note detail/edit** — full text, edit with undo, status change, category/tags/links, audit history.
2. **Trash** — recently deleted notes with restore (`restore` already exists).
3. **Settings hub** — editable categories (6 defaults in `DEFAULT_CATEGORIES`), JSON/Markdown export
   (`NoteExporter`), privacy/permission status.
4. **Inbox triage** — unsorted notes, quick set category/archive; later shows rule/AI suggestions.
5. **Rules list + editor** — on/off, conditions (keywords), action, test against sample text.
6. **Topics & links**, **People & Places** management.
7. **Voice capture** and **Camera/OCR capture** screens, each with a permission explainer.
8. **Reminders list + editor** (time, place, person) with permission explainers.
9. **AI: Proposals review** (approve/reject), **AI setup** (pick model file, schedule, limits),
   **AI run history** (from `ai_runs`).
10. Optional: onboarding, home-screen widget for one-tap capture.

Keep the existing look: teal/warm-paper theme, rounded cards, animated lists, swipe-to-delete with
undo, expanding composer. No icon library is in the project yet — ask before adding dependencies.

## Feature plan (after the screens above need them)
- **Rules engine:** pure Kotlin evaluator (no Android types), rules stored in the `rules` table as
  JSON, run after save via the command handler, table-driven tests, idempotent, undoable. No
  permissions. Do this first among the features.
- **Reminders:** `AlarmManager` time reminders (needs notification + exact-alarm permissions and a
  boot receiver; must survive reboot). **Geofenced place reminders are in scope (D14)** — needs
  location permission and probably Play Services geofencing; discuss the trade-off with Jason
  before adding either. Calendar read is optional, last.
- **Voice:** Android on-device `SpeechRecognizer` (microphone permission only). Save the raw
  transcript as a note immediately; keep no audio by default. Check the offline language pack.
- **Camera/OCR:** system camera/photo-picker intents (no camera permission) + ML Kit **bundled**
  text recognition. Verify with `PrivacyManifestTest` that it adds no network permission.
- **AI night loop (D13):** llama.cpp via JNI with a **user-supplied GGUF**, sideloaded through a file
  picker or `adb push` (no download path exists). Reference model: Qwen3 4B Q4_K_M, ~10 tok/s on
  the phone. Thinking mode OFF, JSON constrained by GBNF grammar, context ≤ ~4k, `WorkManager`
  with charging+idle constraints, `jobs` lease table, stop on heat/low battery, process only notes
  changed since the last run (~150–250 notes/hour budget). Spike speed/battery before building around it.

## Definition of done for each piece
- Builds, all unit tests pass, new logic has tests, `PrivacyManifestTest` still green.
- A short device checklist added to the README for Jason to run on the phone.
- `docs/DECISIONS.md` and `docs/CHANGELOG.md` updated; version bumped per D12 when due.
- Committed and pushed.
