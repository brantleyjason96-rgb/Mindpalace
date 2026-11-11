# Mind Palace — brief for Claude Code

## Start here (updated 2026-11-11, plan 01 step 1e)

**If you are an agent picking up work, read these in order:**

1. This file — the rules below are binding.
2. **`docs/plans/INDEX.md`** — what to do next, in what order, which steps need the owner's
   sign-off first, and the prompt template for executing one plan step.
3. The one plan file for your step (`docs/plans/NN-*.md`). Each lists the files you may change,
   the tests to write, a "done when" check, and when to stop and report.
4. `docs/plans/REVIEW_GATES.md` — the checklist to fill in before anything is merged.

| Looking for | Go to |
|---|---|
| What is broken or risky right now | `docs/plans/01-audit-punch-list.md` (steps 1a–1d done; 1e is this doc pass) |
| Which tests are missing | `docs/plans/02-missing-tests.md` |
| What `§3.7`, `§6`, … in code comments mean | `docs/PLAN.md` |
| Why something was decided | `docs/DECISIONS.md` (latest is D31; **next ID: D32**) |
| Which permissions are approved | `docs/PERMISSIONS.md` |
| How to check the app on the phone | `docs/CHECKPOINT_0.md`, `scripts/checkpoint0.sh` |
| CI | `.github/workflows/android.yml`, `docs/plans/06-ci.md` |
| The owner's feature wish lists | `docs/FEATURE_GAP_PLAN.md`, `docs/CAPTURE_GRAFT_PLAN.md` |
| The original planning brief | `docs/PLANNING_TASKS.md` |

**A Claude cloud session cannot build this app** (no Android SDK; Google's Maven host is
blocked) — push and read the "Android build" GitHub Actions run instead. **On the owner's own
Mac** (this repo, via Claude Code CLI), the Android SDK and a JDK (Android Studio's bundled JBR)
are both present, so `./gradlew` runs directly — no need to wait on CI there.

Then read `README.md`, `docs/DECISIONS.md` (D1–D31), and `docs/TEST_RESULTS.md`.

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
3. **All canonical writes go through `NoteCommandHandler`** (audit + undo). The UI is NOT fully
   compliant with this yet — several vocabulary/rules/reminder writes go straight through DAOs
   from `MainViewModel` (plan 01 W1, tracked, not yet fixed); don't add new direct-DAO writes on
   top of that debt.
4. **The words in the original note are sacred.** Never rewrite raw text outside an audited user edit.
5. **AI never edits notes.** It writes only to `proposals`; approval applies it through the command
   handler with origin `ai:<run>`. Rules apply with origin `rule:<id>`.
6. Any Room schema change = version bump + committed schema JSON + a migration test.
7. Enums persist by name — never rename a constant; add and migrate.
8. Record each non-obvious choice in `docs/DECISIONS.md` (next ID: D32).

## Current state (verify, don't trust)
- Schema **v3** (notes gained `attachment_path`/`attachment_file_name` in D25). All §6 tables,
  FTS5 search, command handler with audit + undo, share target, JSON/Markdown export.
- **The UI is built and runs on the phone** (`MainActivity.kt`, ~4,700 lines, single file —
  not yet split; that's plan 07). versionCode 5, versionName 0.5.0.
- **115 `@Test` methods, all green** (`./gradlew testDebugUnitTest`) as of this commit; see
  `docs/TEST_RESULTS.md` for the exact count and what's covered. Two are deliberate `@Ignore`d
  bug records (A3, G1 in the punch list), not gaps to "fix" by deleting. No Compose UI test
  library yet (`docs/plans/INDEX.md` OD-10) — UI-only fixes are verified by compile + manual
  device trace, not automated tests.
- Toolchain: AGP 9.5.1, Gradle 9.9.0, Kotlin 2.3.21, compile/target SDK 37, min SDK 33.
  CLI builds need `JAVA_HOME` set to Android Studio's JBR (see README).
- Device: Jason's Samsung phone (model SM-S948U). Jason is NOT a professional developer: explain
  errors plainly, give exact commands, one step at a time.
- Known gaps, tracked in `docs/plans/01-audit-punch-list.md` and not yet fixed: AI night-loop
  proposal dedup/duplicate-vocabulary bugs (A1–A5), rule ordering/retroactive-apply (G1, G2),
  direct DAO writes from the ViewModel (W1), per-screen loading of the full note list (D1),
  capture-screen draft loss on rotation (M1, M2), accessibility (Y1), and the debug-signing /
  CI-can't-install-over-dev-build issue (B1).

## Commands
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest     # must stay green
./gradlew assembleDebug         # APK in app/build/outputs/apk/debug/
./gradlew installDebug          # installs OVER the existing app on a connected phone — never uninstalls
```
Branch from `main` for each plan step. Commit in small steps. Don't open PRs unless asked.

## What's built (no longer "screens still to build" — see the plans index for what's next)
Note detail/edit (with undo, revert, status, derived facts, links, reminders), Trash, Settings
hub (categories, export, AI setup), Inbox triage, Rules list + editor, Topics/People/Places
management, Voice/Camera-OCR/Handwriting capture, Reminders list + editor (time only — place/
calendar reminders are still future work, `docs/plans/11-reminders-geofences-calendar.md`), AI
Proposals review + AI run history. What's actually next, in order, is
`docs/plans/INDEX.md`'s execution table — not a screen backlog.

Keep the existing look: teal/warm-paper theme, rounded cards, animated lists, swipe-to-delete with
undo, expanding composer. No icon library is in the project — icons are hand-drawn Canvas glyphs
(`ui/CaptureIcons.kt`, `ui/TabIcons.kt`); ask before adding an icon dependency.

## Feature plan (superseded by the dated plans for anything already built)
This section is the ORIGINAL pre-build feature plan, kept for the "why" behind early choices.
For what's actually built vs. still open, use `docs/plans/01-audit-punch-list.md` and
`docs/plans/INDEX.md` instead of this list.
- **AI night loop (D13, D22, D23):** llama.cpp via JNI with a **user-supplied GGUF**, sideloaded
  through a file picker or `adb push` (no download path exists). Measured ~14 tok/s generation on
  Jason's phone (D22) — the spike passed. Thinking mode OFF, JSON constrained by GBNF grammar,
  `WorkManager` with charging+idle constraints (D24). Known bugs: A1–A5 in plan 01.
- **Reminders:** time-based shipped (D19). **Geofenced place reminders are in scope (D14)** but
  not yet built — needs location permission; discuss with Jason before adding it
  (`docs/plans/11-reminders-geofences-calendar.md` Part B). Calendar read is optional, last.

## Definition of done for each piece
- Builds, all unit tests pass, new logic has tests, `PrivacyManifestTest` still green.
- For a user-visible change with no automated test (no Compose UI test library yet): a manual
  device trace of the fix, called out explicitly in the commit and `docs/DECISIONS.md`.
- `docs/DECISIONS.md` and `docs/CHANGELOG.md` updated; version bumped per D12 when due.
- Committed and pushed.
