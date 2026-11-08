# CHANGELOG

All notable changes. Dates are YYYY-MM-DD.

## [0.1.0] — 2026-11-08 — Phase 0 complete + Phase 1 backend core

### Added
- Project scaffold: AGP 9.5.1 / Gradle 9.9.0 / Kotlin 2.3.21 (built-in Kotlin) / minSdk 33 /
  target+compile 37; version catalog; wrapper. Single-module, packages per plan §5.
- Room3 3.0.3 database "mind_palace.db", **schema v1 with all §6 tables** (notes + FTS5
  external-content index, derived, topics, note_links, reminders, people, places, rules, ai_index,
  proposals, ai_runs, audit, jobs). Schema exported & committed for future migration tests.
- `NoteCommandHandler` — the only writer of canonical records: save-first single-transaction
  create (status INBOX, derived CATEGORY=Inbox origin=system, revision 1), revision-guarded
  raw-text/status edits, soft delete + restore, **audit trail with before/after snapshots and
  undoable every action** (§3.7).
- FTS5 search over title/raw_text/transcript/ocr_text with a hostile-input sanitizer and a LIKE
  fallback (§15 mitigation).
- Share ingestion: pure `ShareIngest` mapper + UI-less `ShareReceiverActivity`
  (ACTION_SEND text/plain → Inbox, records source app, link-only shares stored as link+title).
- Exports: JSON (round-trippable, v1 schema) and Markdown, written to the app-private dir only.
- Privacy gate tests: merged manifest must contain **no permissions** (INTERNET/network-state
  explicitly asserted; androidx core's app-private receiver permission is the one documented
  exception). `allowBackup=false`.
- Placeholder capture UI (single screen; to be replaced by the frontend session).
- Docs: README (build/run/Checkpoint-0 checklist), DECISIONS (D1–D12), TEST_RESULTS (27/27 green).

### Verified
- `./gradlew testDebugUnitTest`: 27/27 green on this Mac (Robolectric + real bundled SQLite/FTS5).
- `./gradlew assembleDebug`: debug APK builds.
- Room3 schema compiles (KSP) for every table + DAO, including the FTS5 entity.

### Not yet verified (owner on-device)
- Checkpoint 0 device checklist (see README). Do not start Phase 1 UI work before it passes.
