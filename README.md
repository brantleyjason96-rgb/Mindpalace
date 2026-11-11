# Mind Palace

> **Working on this repo?** Start at [`docs/plans/INDEX.md`](docs/plans/INDEX.md) (work queue,
> order, owner decisions) and [`CLAUDE.md`](CLAUDE.md) (rules). The audit of the current code is
> [`docs/plans/01-audit-punch-list.md`](docs/plans/01-audit-punch-list.md) — steps 1a–1d are
> done; this README was rewritten as step 1e.

Personal capture app: speak, type, share, or photograph something → saved instantly (save-first),
sorted later by plain rules, resurfaced at the right time, with a fully **on-device, offline** AI
pass at night. **No INTERNET permission by default** — one narrow, owner-approved exception exists
for the handwriting-recognition model download only (D26); `PrivacyManifestTest` enforces the
approved-permissions list.

**Owner:** Jason. **Plan:** `docs/PLAN.md` is the reconstructed key to `§`-references in code
comments; `docs/plans/INDEX.md` is the actual work queue.
**Verified status:** full app UI built and running on-device (versionCode 5, versionName 0.5.0,
schema v3). **115 unit tests, all green** locally (`docs/TEST_RESULTS.md` has the current count
and what each suite covers). **Checkpoint 0 passed on-device 2026-11-09** — see checklist below;
re-run it (`scripts/checkpoint0.sh`) after any change that could affect capture, persistence, or
boot behavior.

## Build & test (this Mac)

There is no system Java on this machine — use Android Studio's bundled JDK (or just build inside
Android Studio, which does this automatically):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest   # unit tests — see docs/TEST_RESULTS.md for the current count
./gradlew assembleDebug       # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # installs OVER the existing app on a connected phone — never uninstalls
```

Android Studio: **File → Open** → this folder → Run ▶ on the phone.
Terminal install (phone connected, USB debugging on, preferred over a bare `adb install` since it
never accidentally uninstalls): `./gradlew installDebug`.

## Checkpoint 0 — device checklist (Jason)

- [x] Installs and launches on the phone.
- [x] Type a test note → Save → shows "Saved (rev 1)" and appears in the list.
- [x] Kill the app (swipe away + force stop) → reopen → note still there.
- [x] Reboot the phone → reopen → note still there (survives reboot).
- [x] From a browser/other app, Share text → "Save to Mind Palace" → toast; note appears in list.
- [x] Search box finds a word from any saved note (FTS5, on-device).
- [x] Airplane mode the whole time — nothing changes, ever.

All 7/7 passed 2026-11-09. Re-run via `scripts/checkpoint0.sh` as a regression check — see
`docs/plans/05-checkpoint-0.md`.

## What's built vs. what's next

**Built:** the full app — capture (text/voice/photo-OCR/handwriting/attachment), browse/search,
note detail with undo and revision history, Trash, Settings (categories, export, AI setup), Rules
engine, Topics/People/Places, Reminders (time-based), AI night loop with proposal review. All
behind the same save-first, audited-write contract from day one.

**What's actually next** (not a screen backlog anymore — read
[`docs/plans/01-audit-punch-list.md`](docs/plans/01-audit-punch-list.md) and
[`docs/plans/INDEX.md`](docs/plans/INDEX.md) for the real list): known bugs in the AI night loop's
dedup logic, rule ordering, a few direct-DAO writes from the ViewModel, splitting the now-large
`MainActivity.kt`, export/import, accessibility, geofenced/calendar reminders, widgets.

## Where things are

```
app/src/main/java/com/mindpalace/app/
├── MindPalaceApplication.kt     # AppContainer: database, commands (ONLY writer), noteDao, exporter
├── MainActivity.kt              # the app UI (Compose) — large; splitting it is plan 07
├── capture/ShareReceiverActivity.kt   # ACTION_SEND receiver (save-first, no UI)
├── core/model/Model.kt          # enums, Origin, NoteSnapshot, default categories
├── core/db/                     # entities (all §6 tables), DAOs, FTS5 + NoteSearch, schema v3 exported
│   └── MindPalaceDatabase.kt
├── core/commands/NoteCommandHandler.kt  # the ONLY mutator of canonical note data; audit + undo
│   └── NoteCommands.kt          # CreateNoteRequest, ShareIngest (pure, tested)
├── core/rules/                  # keyword-matcher rules engine
├── core/reminders/              # AlarmManager scheduling, boot rescheduling
├── core/ink/, core/ocr/         # handwriting recognition, OCR
├── ai/                          # llama.cpp night loop: engine, prompt/grammar, validator, runner
└── export/NoteExporter.kt       # JSON (round-trips) + Markdown exports
```

See `docs/DECISIONS.md` for the recorded decision log and `docs/TEST_RESULTS.md` for the verified
test matrix. Principle: **never add a permission without sign-off; never touch notes outside the
command handler; the words in the original note are sacred.**
