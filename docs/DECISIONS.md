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

## D13 — Night-loop model: llama.cpp + user-supplied GGUF (sideloaded)
Nov 9, 2026. Runtime is llama.cpp (native, via JNI), not a Google-format runtime, so the same GGUF
Jason already runs in PocketPal works. Reference model: **Qwen3 4B Q4_K_M (~2.5 GB), measured
~10 tok/s generation on the S26 Ultra** (owner-reported, via PocketPal). No download path exists
(no INTERNET): the model file is chosen via a file picker or `adb push`; the app never fetches it.
Design rules: thinking mode OFF (thinking tokens would burn the night budget), JSON output
constrained by a GBNF grammar, short prompts (context capped ~4k), AI output goes ONLY to
`proposals` (origin `ai:<run>` after the user approves), charging+idle constraints, stop on thermal
or battery limits. Budget math: ~100 output tokens/note ≈ 10 s of generation → roughly 150–250
notes/hour; the loop works on notes changed since the last run, not the whole archive.

## D19 — Reminders: AlarmManager, time-based only, three permissions approved
Nov 9, 2026. Owner sign-off for `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and
`RECEIVE_BOOT_COMPLETED` (docs/PERMISSIONS.md). Scope is TIME reminders only — place/geofenced
reminders stay deferred per D14 ("time-based reminders ship first; places follow"); no location
permission is requested here. `ReminderAlarmReceiver` fires a notification when a reminder's exact
time arrives; `BootReceiver` re-schedules every still-active, still-future TIME reminder after a
reboot (AlarmManager drops all alarms on reboot otherwise). A reminder is created from the note
detail screen (date+time picker → `AlarmScheduler.schedule`); a global Reminders screen
(Settings) lists every ACTIVE reminder with Mark done / Cancel, both of which cancel the
underlying alarm. `ReminderState.DONE`/`CANCELLED` are terminal — no undo, consistent with rules
and derived facts not going through the audited note-mutation path.

## D18 — Topics/People/Places: append-only vocab; note links are explicit-only for now
Nov 9, 2026. Three screens (reachable from Settings) share one `VocabScreen` composable: a name
field + Add, and a plain list — no delete, no merge/edit. Why: nothing yet references these
vocabularies from a note (that's the still-open "tags" part of the note-detail screen, deliberately
NOT built in this pass), so there's nothing to clean up yet; and §7 says two plausible person/place
matches must ask the user, not silently merge — building a merge UI ahead of any real matching
logic would be speculative. Note detail gained a **Links** section instead: existing links
(`noteLinkDao.observeTouching`) shown and tappable (navigates to the other note), plus "Add link"
→ an inline filtered picker over `allNotes` that inserts a `NoteLinkEntity(type=EXPLICIT,
state=CONFIRMED)` directly — consistent with how Rules already writes its own table straight from
the ViewModel (no audit trail for this table, same as rules/categories).

## D17 — Camera/OCR: bundled ML Kit, system camera/photo-picker intents, no new permission
Nov 9, 2026. `com.google.mlkit:text-recognition:16.0.1` (the bundled variant — the model ships in
the APK, ~4MB, never the Play-Services/unbundled variant that lazily installs a dynamic module).
Its transitive Play-Services-base manifest declares INTERNET + ACCESS_NETWORK_STATE defensively
regardless; both are stripped via `tools:node="remove"` in AndroidManifest.xml since OCR never
makes a network call either way — `PrivacyManifestTest` verified green with them removed.
Capture uses the system camera app (`MediaStore.ACTION_IMAGE_CAPTURE`, output to a FileProvider
URI in app-private storage) and the Android 13+ Photo Picker (`PickVisualMedia`) for "choose from
gallery" — **neither requires a CAMERA or READ_MEDIA_IMAGES permission**, matching the feature
plan's "no camera permission" requirement exactly. OCR runs on-device against the captured/picked
image; the recognized text is reviewable/editable before the first save (not yet "sacred" — it
becomes sacred once saved), then goes through the normal `NoteCommandHandler.create`
(`source = PHOTO`, `ocrText`, `imagePath` pointing at the saved copy in app-private storage).

## D16 — Rules engine: keyword matcher → category action, additive derived facts
Nov 9, 2026. Pure Kotlin evaluator (`core/rules/RuleEngine.kt`, no Android types): a rule is a
keyword matcher (case-insensitive substring, any-of) and a category action; rules run in list
order and the first enabled match wins. Runs once, inside `NoteCommandHandler.create`'s existing
transaction, right after the system Inbox default is seeded. A match ADDS a second CATEGORY
derived row (`origin = rule:<id>`) alongside the system one rather than replacing it — the derived
table's primary key is (note_id, field, value, origin), so this is naturally idempotent and never
destroys the system default. Reconciling "which category wins when several exist" is a surfacing
concern (Phase 4, D11), deliberately not solved here. Rules currently apply only at capture time
("after save"), not retroactively on edits — re-evaluating on every raw-text edit is a plausible
future extension, not built because nothing asked for it yet.

## D15 — Voice capture: on-device SpeechRecognizer, RECORD_AUDIO approved
Nov 9, 2026. Owner sign-off to add `android.permission.RECORD_AUDIO` (docs/PERMISSIONS.md),
`PrivacyManifestTest` updated deliberately to allow it. Implementation: `SpeechRecognizer` with
`RecognizerIntent.EXTRA_PREFER_OFFLINE = true` (on-device, no network attempted regardless — D4
holds either way). Dedicated "Voice capture" screen shows a permission explainer before the system
prompt and a live transcript while listening. On Save, the transcript goes straight through
`NoteCommandHandler.create` (`source = VOICE`, `transcript = ...`) — same save-first path as every
other source. No raw audio is recorded or stored; only the transcript.

## D14 — Geofenced place reminders are in scope
Nov 9, 2026. Owner decision. Cost accepted: location permission (background access for reliable
triggers) and likely Google Play Services geofencing. To be recorded in `docs/PERMISSIONS.md` when
built; `PrivacyManifestTest` gets a deliberate, documented update then (never silently loosened).
Time-based reminders ship first; places follow. Still open: whether Play Services geofencing is
acceptable vs. a Play-Services-free fallback.
