# DECISIONS

Chronological decision log (plan §16.6). Format: ID, date, decision, why, evidence.

## D1 — Layout: single Gradle module, packages for "modules"
Nov 8, 2026. One `:app` module; the plan's module list (`core/model`, `core/db`, `core/commands`,
`export`, …) is realized as packages. Why: plan §5 explicitly allows "packages first, split to
modules only if needed"; smaller build = fewer walls at 3–5 hrs/week. Split when a real need shows.

## D2 — Toolchain pinned to current stable, verified (not guessed)
Nov 8, 2026. AGP **9.5.1**, Gradle **9.9.0**, Kotlin **2.3.21** (built-in Kotlin via AGP 9; no
separate kotlin-android plugin), KSP **2.3.13**, compileSdk/targetSdk **37**, minSdk **33**.
Why: plan §16.8 — verify, don't assume. Every pair was checked against release notes/Maven
metadata before use: AGP 9.5 max API is 37 and requires Gradle ≥ 9.6, Build-Tools 36.0.0 (present),
JDK 17+ (AS JBR 25 used). Machines: this Mac has **no system Java**; CLI builds must set
`JAVA_HOME` to the Android Studio JBR (README), Android Studio needs nothing special.

## D3 — FTS5 satisfied via ROOM 3 + BundledSQLiteDriver (plan-vs-reality report)
Nov 8, 2026. The plan said "Room + FTS5". Reality found: classic `androidx.room` 2.8.5 tops out at
`@Fts4`; **`androidx.room3` 3.0.3 (stable) ships `@Fts5`** with external-content support
(`contentEntity`, `contentRowId`, tokenizer options). Chose room3 3.0.3 + `@Fts5`
(contentEntity = notes). Per room3's KDoc, FTS5 availability depends on the driver; therefore the
database uses **`BundledSQLiteDriver`** (real SQLite with FTS5 compiled in) — this also makes FTS5
run identically in Robolectric JVM tests (`androidx.sqlite:sqlite-bundled-jvm` test dep carries
desktop natives — without it, JVM tests die with `UnsatisfiedLinkError: no sqliteJni`).
Cost: APK ≈ 36 MB (bundled SQLite ≈ 10 MB). Accepted; revisit only if storage matters.
Room2-style `db.withTransaction {}` does not exist in room3 — writes use
`database.withWriteTransaction {}` (IMMEDIATE). Room schema v1 is exported to
`app/schemas/…/1.json` and committed; every future version bump needs a MigrationTest (§14).

## D4 — Privacy: zero permissions, allowBackup=false, tested gate
Nov 8, 2026. Manifest declares **no permissions at all**; `PrivacyManifestTest` reads the MERGED
manifest and fails on `INTERNET`, `ACCESS_NETWORK_STATE`, or any unknown permission. One documented
exception: `com.mindpalace.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — merged in by
androidx.core, app-private/signature-level self-protection for dynamic receivers; grants no
external capability. Also `android:allowBackup="false"`: Android's cloud backup would copy notes
off-device — violates private-by-construction; migration is by manual export files instead.

## D5 — IDs, timestamps, enums
Nov 8, 2026. `notes` has TWO ids: internal `rowid` (INTEGER PK — required: the FTS5 external-content
docid must sync to an integer rowid) and the public **UUID `id`** column (plan §6 uses it
everywhere: links/derived/audit/exports). Timestamps = epoch millis Long. Enums persist **by name**
via Room's built-in mapping — never rename a constant post-release; add + migrate instead.
Wire format of `derived.origin` ("user"|"system"|"rule:<id>"|"ai:<run>") is a data contract.

## D6 — Schema v1 includes ALL §6 tables now
Nov 8, 2026. notes(+FTS), derived, topics, note_links, reminders, people, places, rules, ai_index,
proposals, ai_runs, audit, jobs all exist in v1 so later phases ALTER/append with tested migrations
instead of churn. Future-only tables have minimal columns and kdocs marking their phase.

## D7 — Audit actor adds SYSTEM
Nov 8, 2026. Plan listed audit actors (user|rule|ai). Mechanical attribution needs a fourth:
`SYSTEM` (default Inbox seeding today; night-loop lease recovery/safety repairs in Phase 5).
Anything semantic remains user/rules/ai.

## D8 — Manual DI (no Hilt)
Nov 8, 2026. `AppContainer` exposes exactly: `database`, `noteDao`, `commands`, `exporter`,
`exportDir`. The plan allows either; fewer moving parts at this size. Revisit if test wiring
becomes painful.

## D9 — Share ingestion is a pure function
Nov 8, 2026. `ShareIngest.toRequest(text, subject, sourcePackage)` has no Android types → plain
JUnit tested. The `ShareReceiverActivity` is UI-less (`Theme.NoDisplay`), blocks on the single
fast insert before finishing (save-first: shared text can never be lost), toasts, exits.
Link-only shares store link+title only (plan §12, offline rule).

## D10 — Search sanitizer
Nov 8, 2026. User input to FTS MATCH goes through `FtsQuery.sanitize` (quote each token) so input
like `don't`/`(`/`-` can't break FTS5 syntax; a `LIKE` fallback query exists for hostile devices
(§15). Verified by tests including hostile-input cases.

## D11 — Session scope (this repo state)
Nov 8, 2026. Phase 0 complete **plus** Phase 1's backend (capture command handler, share target,
FTS5 search, exports) per owner instruction "start building, don't build the frontend".
The visible UI is a marked placeholder. Deferred, with their hooks in place: rules engine logic
(Phase 3), surfacing (4), AI/night loop (5), voice/OCR/widgets (Phase 1/2 UI parts).

## D12 — Version bump policy
Nov 8, 2026. `versionCode 1 / versionName 0.1.0`. Bump on any schema change, permission change, or
Phase completion. Room schema files must be committed (done).
