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

## D21 — Visual/motion polish pass: custom icons, tinted shadows, haptics, screen transitions
Nov 9, 2026. No new dependency: `core/ui/CaptureIcons.kt` hand-draws the mic/camera glyphs via
Canvas (emoji don't tint to the theme or render consistently across devices/dark-mode), and
`core/ui/Buttons.kt` holds a shared `rememberPressScale()` + `CircleIconButton` used everywhere a
tactile circular icon button is needed. Cards and list rows across the app (NoteCard, Rule/Vocab/
Link/Reminder rows) got a brand-tinted `Modifier.shadow` (ambient/spot = `colorScheme.primary`)
instead of flat default-black elevation. Voice capture's mic button now draws live concentric
rings driven by `onRmsChanged` (real mic level, not a fake loop) plus an ambient idle pulse.
Top-level navigation (`MainActivity`) now computes a sealed `AppScreen` and renders it through one
`AnimatedContent` (slide+fade) instead of an instant `when`-swap Surface — note the content lambda
must branch on the captured `target` parameter, not live mutable booleans, or the outgoing slot
re-renders as the new screen mid-transition instead of animating out.

Haptics: every new interactive moment (mic/camera tap, filter chips, rule toggle, add/remove,
reminder done/cancel, link select, restore) calls `performHapticFeedback`. Initially used the
newer semantic constants (`Confirm`, `Reject`, `ToggleOn/Off`, `SegmentTick`) per their Compose
intent, but switched everything to `HapticFeedbackType.LongPress` after on-device testing (Samsung
S26 Ultra/One UI) found the newer constants produced no felt vibration while `LongPress` (already
used by the pre-existing swipe gesture) reliably does — One UI's haptic composer appears to only
implement motor patterns for a subset of the standard Android constants. `LongPress` is the one
proven to work on the actual owner device, so it's now the single haptic language app-wide.

Composer fix: the mic/camera buttons now hide whenever either field is focused (`!expanded`), not
only once there's text (`!canSave` previously) — they were staying visible while the keyboard was
open with empty fields, cluttering the one moment the user is clearly mid-type.

## D20 — Editable categories: new `categories` table, schema v1 → v2
Nov 9, 2026. Closes the last gap in the original screen backlog's Settings item ("editable
categories (6 defaults in DEFAULT_CATEGORIES)"). New `categories` table (name TEXT PRIMARY KEY,
position, created_at), added via `MIGRATION_1_2` which also seeds the six original defaults in
their original order — `DEFAULT_CATEGORIES` in Model.kt is now only that seed list, not the live
source of truth. Settings > Categories reuses the existing `VocabScreen` (same shape as
Topics/People/Places, D18): add-only, no rename/delete, for the same reason — rule actions and
derived CATEGORY facts already reference categories by name string, and renaming/deleting would
orphan those references without a cascade story this doesn't need yet. Rules' "New rule" category
picker now reads from this table instead of the hardcoded constant, so a category added in
Settings immediately becomes available there too. First real schema migration in this project;
added `androidx.room3:room3-testing` + a `MigrationTestHelper`-based test verifying the seed data
survives the 1→2 migration in order. One non-obvious wiring note: Robolectric's local JVM unit
tests reuse the **main** `debug` variant's merged assets directory (confirmed via
`test_config.properties` → `android_merged_assets`), not a test-only one — so the exported schema
JSON had to be added to the `main` sourceSet's assets, not `test`, for `MigrationTestHelper` to
find it at all.

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

## D22 — AI spike: llama.cpp vendored + built via NDK/CMake, measured on-device speed confirms D13
Nov 9, 2026. Per D13/CLAUDE.md's instruction to "spike speed/battery before building around it,"
before writing any proposals/WorkManager scaffolding: vendored llama.cpp source (pinned tag v0.9.0,
`.git` stripped, `models/`+`docs/` pruned, ~68 MB) under `app/src/main/cpp/llama.cpp/`, added
`app/src/main/cpp/CMakeLists.txt` (builds `llama`/`llama-common` as a subdirectory — its own
examples/tools/tests/server stay off since those options default to `${LLAMA_STANDALONE}`, false
when used as a subdirectory) and a minimal JNI bridge `ai_spike.cpp` exposing just
`init`/`load`/`benchModel`/`unload`/`shutdown` (adapted from the upstream `examples/llama.android`
reference app bundled in the vendored source, trimmed to the perf-measurement path only — no chat
loop, no grammar, no sampling yet). Wired `ndkVersion`, `abiFilters` (arm64-v8a + x86_64), and
`externalNativeBuild.cmake` into `app/build.gradle.kts`, with `GGML_BACKEND_DL=ON` +
`GGML_CPU_ALL_VARIANTS=ON` so ggml picks the best CPU kernel (KleidiAI/SME2 etc.) for the actual
device at runtime instead of a single compile-time target.

Build gotcha: `ggml_backend_load_all_from_path()` dlopens the per-CPU-variant backend `.so` files
from `context.applicationInfo.nativeLibraryDir` at runtime — but AGP's modern default packages
native libs page-aligned straight into the APK without extracting them to that directory, so the
dir existed but was empty and model load failed with "no backends are loaded". Fixed with
`packaging.jniLibs.useLegacyPackaging = true`, which forces real extraction.

Result on Jason's Samsung S26 Ultra, Qwen3 4B Q4_K_M (2.32 GiB, 4.02B params), 4 threads: **25.3
tok/s prompt processing, 14.1 tok/s token generation** — matches/exceeds D13's PocketPal-reported
~10 tok/s estimate. Budget math holds: ~300-token prompt + ~80-token JSON proposal ≈ 18 s/note ≈
~200 notes/hour, inside the 150–250/hour target. **Spike passed — proceed to the real GBNF
grammar + proposals/WorkManager architecture next**, building on this native toolchain rather than
re-deriving it.

A temporary debug-only "AI spike" button lives in Settings (reads the model from
`getExternalFilesDir(null)/model.gguf`, a fixed path for `adb push` convenience) — remove once the
real AI setup screen (file picker) exists.

## D23 — Night-loop v1: GBNF grammar + proposals pipeline, summary cutoff at 50 words
Nov 9, 2026. Built the real completion pipeline on top of D22's engine: `AiEngine` (renamed from
the spike's `AiSpike`) now exposes `loadModel`/`complete`/`unloadModel`, where `complete()` is one
grammar-constrained, non-streaming JNI call (`runCompletion` in `ai_spike.cpp`) — tokenizes a
system+user prompt through the model's own chat template (`common_chat_templates_init` /
`common_chat_format_single`, same approach as the upstream reference app), decodes it, then samples
token-by-token with a `common_sampler` constrained by a user GBNF grammar until EOG or a token cap.
No chat history is kept between calls — each note gets an independent request, matching "no
big-picture judgment" from the plain-English description given to Jason.

`AiPrompt.GRAMMAR` constrains the model to exactly `{"summary": string|null, "proposals": [{"kind":
CATEGORY|TAG|TOPIC|PERSON|PLACE, "value": string, "evidence": string, "confidence": 0.00-1.00}]}`.
`AiProposalValidator` then enforces what the grammar can't: `evidence` must be a literal substring
of the note's own text (CLAUDE.md's anti-hallucination rule), confidence must clear 0.5, and the
summary is dropped unless `AiPrompt.needsSummary()` independently agrees the note is over 50 words
— the model is never trusted to have obeyed that instruction on its own (owner request, Nov 9).

`AiNightRunner` is the orchestrator: one model load, then one `AiEngine.complete()` per note from
`NoteDao.changedSince(lastRun)`, writing validated TOPIC/PERSON/PLACE hits + any summary into
`ai_index` (ungated, auto-shown) and every proposal into `proposals` as `PENDING` (gated, needs
approval) — never touching the note itself. Logs start/finish/outcome to `ai_runs`. Wired into
`AppContainer` as `aiNightRunner`; triggered for now only from the same debug Settings section as
the spike ("Run on all notes", `since = 0`) — no WorkManager scheduling, no review UI, no file
picker yet. Those are the next pieces, in that order.

Validated on-device (Jason's S26 Ultra, real 5-note database, Qwen3 4B Q4_K_M): run completed in
~113s (≈22.5s/note, matching D22's budget math), produced 3 valid `CATEGORY` proposals with
evidence quotes that are exact substrings of the actual transcripts, 0 `ai_index` rows (all 5 notes
were under 50 words, so no summary was correctly expected) — confirms the grammar, validator, and
word-count cutoff all behave as designed on real hardware, not just in theory.

## D14 — Geofenced place reminders are in scope
Nov 9, 2026. Owner decision. Cost accepted: location permission (background access for reliable
triggers) and likely Google Play Services geofencing. To be recorded in `docs/PERMISSIONS.md` when
built; `PrivacyManifestTest` gets a deliberate, documented update then (never silently loosened).
Time-based reminders ship first; places follow. Still open: whether Play Services geofencing is
acceptable vs. a Play-Services-free fallback.

## D24 — Nightly AI scheduling via WorkManager; WAKE_LOCK + FOREGROUND_SERVICE approved
Nov 10, 2026. `AiNightRunner` (D23) had no scheduler — the only way to run it was the debug "Run
on all notes" button in Settings. Added `androidx.work:work-runtime` 2.12.0 and `AiNightWorker`
(`CoroutineWorker`), scheduled as a daily `PeriodicWorkRequest` with `setRequiresCharging(true)` +
`setRequiresDeviceIdle(true)` + `setRequiresBatteryNotLow(true)` — the standard "maintenance
window" shape, so the night loop never costs the user anything they'd notice. `since` comes from
the new `AiRunDao.lastSucceededFinishedAt()` query (null → process everything). A best-effort
`PowerManager.currentThermalStatus` check retries rather than runs while the phone is hot — there's
no `Constraints` flag for thermal state. If no model file exists at `AiModelConfig.modelPath`
(shared with the debug buttons so they can't drift apart), the worker is a silent no-op rather than
a failure WorkManager would keep retrying.

The library transitively requires `android.permission.WAKE_LOCK` (briefly held while work runs, so
the system doesn't suspend the CPU mid-run) and declares `android.permission.FOREGROUND_SERVICE`
in its own manifest (defensive — only actually used for expedited/long-running work, which this
app doesn't request). Both local-only, no network, no new data access. **Owner sign-off: 2026-11-10
(in-session)** — see `docs/PERMISSIONS.md`; `PrivacyManifestTest`'s approved list updated in the
same commit.

Tried first: manually disabling WorkManager's default ContentProvider-based init via the
documented manifest `tools:node="remove"` recipe, so test and prod would take the same manual-init
path. The merger reported "no other declaration present" for that removal (silently not applying)
under this AGP/Robolectric combination, and manual `WorkManager.initialize()` then collided with
the default init that already happened ("already initialized"). Reverted the manifest change;
`MindPalaceApplication.ensureWorkManagerInitialized()` instead tries `getInstance()` first and
only calls `initialize()` if that throws — correct on a real device (provider already ran) and
under Robolectric (it doesn't).

Still open, in order (next pieces): AI setup screen (model-file picker instead of the hardcoded
path, schedule/limit controls), AI run history view over `ai_runs`.

## D25 — Attach-file capture; a pre-existing BOOT_COMPLETED crash found and fixed
Nov 10, 2026. Added a fourth capture mode alongside Quick Text/Voice/Scan: `NoteSource.ATTACHMENT`
— any file type via the system document picker (`ActivityResultContracts.OpenDocument`), copied
into a new app-private `attachmentsDir` (mirrors `photosDir`), no review step (unlike Voice/Photo,
there's no transcript/OCR text to correct before saving). Schema v2 → v3: two new nullable columns
on `notes` (`attachment_path`, `attachment_file_name`) via `MIGRATION_2_3`, schema JSON exported
(`app/schemas/.../3.json`), migration test added. `NoteCommandHandler.applyRules` and
`AiNightRunner`'s searchable-text join both now include the attachment filename, so rules/AI can
match on it (e.g. "invoice" in a filename → Finance category) same as any other note text.

Also, owner decisions recorded for two other backlog items while scoping this: manual
category-override-at-capture-time (when built) will be a **permanent** override — a hand-picked
category always wins, the rule engine never touches that note's category again. Handwriting
capture (S-Pen ink → text) is confirmed wanted; scoped as its own follow-up task, not bundled into
this one (new canvas UI + ML Kit Digital Ink Recognition dependency + new capture screen is a
bigger unit of work). Privacy Level: Biometric toggle — declined, not building it.

**Unrelated pre-existing bug found and fixed in the same pass:** a real device reboot during this
session's testing surfaced `BootReceiver` and `ReminderAlarmReceiver` manifest-declared at the
wrong path — `.reminders.X` (resolves to `com.mindpalace.app.reminders.X`, package root) instead
of `.core.reminders.X` (their actual package, `com.mindpalace.app.core.reminders`). Any
`BOOT_COMPLETED` broadcast crashed the whole app (`ClassNotFoundException` in `handleReceiver`,
force-finishing the foreground activity too); reminder alarms firing would have hit the same
`ClassNotFoundException` for `ReminderAlarmReceiver`, meaning **no reminder notification has ever
actually fired** since D19. Neither path is covered by Robolectric's manifest parsing (it doesn't
validate that a declared receiver class resolves the way a real device's `PackageManager` does at
broadcast-dispatch time), which is why 50 passing unit tests never caught it. Fixed by correcting
both `android:name` paths in `AndroidManifest.xml`; confirmed on-device (a real
`BOOT_COMPLETED` no longer crashes the app). No test added for this specific failure mode — would
need an instrumented (not Robolectric) test to actually exercise manifest-to-class resolution;
flagged here instead so it doesn't silently regress if someone "simplifies" these paths back.

## D26 — Handwriting capture; the first (and only) exception to "no INTERNET, ever"
Nov 10, 2026. Scoping handwriting capture (confirmed wanted, D25) surfaced a real conflict:
ML Kit Digital Ink Recognition has **no bundled/offline model**, unlike the OCR text-recognition
model (D17) — the handwriting model can only be obtained via `RemoteModelManager`'s dynamic
download, which needs `INTERNET` + `ACCESS_NETWORK_STATE`. This directly contradicted rule 1's
absolute "never," so it went back to the owner for an explicit decision rather than being quietly
built around. Two rounds of confirmation: first a general question surfaced the keyboard-based
alternative (Samsung's own S-Pen handwriting-to-text IME, zero dependency/permission); owner chose
the real ML Kit path anyway. Given the scale of what that overturns, asked a second, more explicit
confirmation naming exactly what changes (INTERNET permission, `PrivacyManifestTest`'s dedicated
"no INTERNET" test, rule 1's wording, the in-app "100% On-Device & Private" promise) — owner
confirmed understanding and approved.

Scope of the exception, deliberately narrow: INTERNET/ACCESS_NETWORK_STATE exist **only** to
download the ink recognition model, once per language, cached on-device afterward. Recognition
itself runs fully on-device; no note content, transcript, or recognized text is ever transmitted.
Any future use of these permissions for anything else needs its own sign-off, not a free pass from
this one.

Implementation reuses PHOTO's exact data shape rather than adding new columns: `NoteSource.INK`,
the rendered ink strokes saved as a PNG via the existing `imagePath` column (same `photosDir`),
recognized text via the existing `ocrText` column — same "an image + its recognized text" shape,
so no schema migration needed. `HandwritingCaptureScreen` mirrors Voice/Photo's review-before-save
flow: draw → Recognize → editable text field → Save. English-only for now (`en-US` hardcoded) —
ponytail: add a language picker when multi-language actually matters, not speculatively.

Updated in the same pass: `PrivacyManifestTest` (removed the two dedicated "no INTERNET"/"no
ACCESS_NETWORK_STATE" tests, which would now permanently fail; added both to the approved-list
test with a new test asserting they're present), `docs/PERMISSIONS.md`, and CLAUDE.md rule 1's
wording (from an absolute "never" to "default to no, with this one documented, scoped exception").

## D27 — Plan 02 §5 step 1: ran locally instead of waiting on CI; two findings recorded, not fixed
Nov 11, 2026. Executing `docs/plans/INDEX.md` order item 1 (the untagged baseline tests from
`docs/plans/02-missing-tests.md`). Unlike the planning session that wrote these plans, this
execution runs on the owner's own Mac, which has the Android SDK and a usable JDK via Android
Studio's bundled JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) — so
`./gradlew testDebugUnitTest` runs directly and the plans' "push, then wait for CI" loop wasn't
needed for this step.

Two things the writing-up surfaced, worth recording so they're not rediscovered as surprises:
1. Room **does** enforce foreign keys by default (T-FK1's probe), but a violation throws a plain
   `android.database.SQLException`, not `android.database.sqlite.SQLiteConstraintException` —
   relevant if a later plan catches by exception type.
2. `ManifestComponentsTest` (new, T-MAN1/T-MAN2) needed an allow-list for several exported
   components this app doesn't declare: `androidx.work.impl.background.systemjob.SystemJobService`
   and `androidx.work.impl.diagnostics.DiagnosticsReceiver` (WorkManager), `androidx.profileinstaller.ProfileInstallReceiver`,
   `androidx.compose.ui.tooling.PreviewActivity`, and three `androidx.test` instrumentation
   activities — all exported by their own libraries, not by this app's manifest.

Z2.12 (the stray "quilks" word in `NoteFtsEntity`'s KDoc, `core/db/NoteEntities.kt`) was fixed in
this step alongside the tests that exercise that file, even though plan 01's step 1a — which also
lists Z2.12 — scopes its "files allowed" to `core/model/Model.kt` and not `core/db`. Recorded here
rather than left silent: a one-word doc typo fix is low enough risk that stopping entirely over
the mismatch wasn't worth it, but the discrepancy between plan 01's execution table and its own
files-allowed list is real and should be reconciled there.

No production code changed in this step beyond that one doc comment — test files only otherwise
(plus this entry, `CHANGELOG.md`, and `docs/TEST_RESULTS.md`). `docs/plans/01-audit-punch-list.md`
step 1a is next.
