# Mind Palace

Personal capture app: speak, type, or share something → saved instantly (save-first), sorted later
by plain rules, resurfaced at the right time, with a fully **on-device, offline** AI pass (later
phases). **No INTERNET permission — enforced by a unit test.**

**Owner:** Jason. **Plan:** see the Mind Palace build plan (§-references in code comments point at it).
**Verified status:** Phase 0 complete + Phase 1 backend core; **27/27 unit tests green** locally
(2026-11-08). On-phone Checkpoint 0 checklist below is the remaining gate.

## Build & test (this Mac)

There is no system Java on this machine — use Android Studio's bundled JDK (or just build inside
Android Studio, which does this automatically):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest   # 27 tests across 5 suites
./gradlew assembleDebug       # -> app/build/outputs/apk/debug/app-debug.apk
```

Android Studio: **File → Open** → this folder → Run ▶ on the S26 Ultra.
Terminal install (phone connected, USB debugging on):
`adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Checkpoint 0 — device checklist (Jason)

- [ ] Installs and launches on the S26 Ultra.
- [ ] Type a test note → Save → shows "Saved (rev 1)" and appears in the list.
- [ ] Kill the app (swipe away + force stop) → reopen → note still there.
- [ ] Reboot the phone → reopen → note still there (survives reboot).
- [ ] From a browser/other app, Share text → "Save to Mind Palace" → toast; note appears in list
      (share target is wired even though the full UI comes later).
- [ ] Search box finds a word from any saved note (FTS5, on-device).
- [ ] Airplane mode the whole time — nothing changes, ever.

## What's built vs. what's next

**Built (this session):** Phase 0 (project, schema, privacy gate, packaging) **plus the Phase 1
backend core** — all §6 tables in Room v1, save-first command handler with audit + undo, FTS5
search, share-text ingestion, JSON/Markdown export. Tests cover all of it.

**Deliberately next (not failures):** the full app UI (the frontend session replaces the
placeholder screen in `MainActivity.kt`), voice capture, camera/OCR, widgets, the rules engine,
reminders/geofences/calendar, the AI/night loop. Each has a placeholder package or table ready.

## Where things are

```
app/src/main/java/com/mindpalace/app/
├── MindPalaceApplication.kt     # AppContainer: database, commands (ONLY writer), noteDao, exporter
├── MainActivity.kt              # PLACEHOLDER UI — the frontend session replaces this
├── capture/ShareReceiverActivity.kt   # ACTION_SEND receiver (save-first, no UI)
├── core/model/Model.kt          # enums, Origin, NoteSnapshot, default categories
├── core/db/                     # entities (all §6 tables), DAOs, FTS5 entity, schema v1 exported
│   └── MindPalaceDatabase.kt
├── core/commands/NoteCommandHandler.kt  # ONLY mutator of canonical data; audit + undo
│   └── NoteCommands.kt          # CreateNoteRequest, ShareIngest (pure, tested)
└── export/NoteExporter.kt      # JSON (round-trips) + Markdown exports
```

See `docs/DECISIONS.md` for the recorded decision log and `docs/TEST_RESULTS.md` for the verified
test matrix. Principle: **never add a permission without sign-off; never touch notes outside the
command handler; the words in the original note are sacred.**
