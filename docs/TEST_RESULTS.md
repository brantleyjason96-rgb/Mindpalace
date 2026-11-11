# TEST RESULTS

## 2026-11-11 — Plan 02 §5 step 1 (baseline untagged tests) — ALL GREEN (local JVM)

**Command:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`
**Result:** `BUILD SUCCESSFUL` — **91 tests, 0 failures, 0 errors, 2 skipped**
(`@Ignore`d bug records: T-PR3 for A3, T-ORD1 for G1 — kept on record per plan 02).
Run locally on the owner's Mac (unlike the cloud-planning session, this one has the Android SDK
and a JDK via Android Studio's bundled JBR, so tests ran directly instead of waiting on CI).

Added this step: every untagged test from `docs/plans/02-missing-tests.md` §3 that was feasible
without new production code — `NoteCommandHandlerTest` (undo-of-soft-delete, no-op edits,
soft-deleted-note guards, audit snapshot contents, approve-CATEGORY/TAG/TOPIC/PLACE, approve-on-
deleted-note), `NoteDaoTest` (stale-revision guards, FTS follows edits/restore/transcript/ocr/
case-folding, FTS integrity check, foreign-key enforcement probe), new `FtsQueryTest` (sanitizer
unit tests), new `OriginTest`, new `AiProposalValidatorTest`, new `ShareReceiverActivityTest`
(non-SEND intent only — the SEND-intent cases are tagged (1a) and land with that step), new
`ManifestComponentsTest` (D25 regression guard), and four new `NoteExporterTest` cases.

Found during this pass (not yet fixed — tracked by `docs/plans/01-audit-punch-list.md`):
foreign keys ARE enforced (Room enables them by default), but the violation surfaces as a plain
`android.database.SQLException`, not `SQLiteConstraintException` — worth a note if a later plan
catches by type. `ManifestComponentsTest` had to allow-list several androidx-library components
(WorkManager's job service/diagnostics receiver, profileinstaller's install receiver, Compose
preview tooling, androidx.test instrumentation activities) that are exported by the libraries
themselves, not by this app.

## 2026-11-08 — Phase 0 + Phase 1 backend — ALL GREEN (local JVM)

**Command:** `JAVA_HOME=<AS JBR 25> ./gradlew testDebugUnitTest assembleDebug`
**Result:** `BUILD SUCCESSFUL` — **27 tests, 0 failures, 0 errors, 0 skipped**, debug APK built,
room3 schema v1 exported to `app/schemas/`.

| Suite | Tests | Failures | Covers |
|---|---|---|---|
| PrivacyManifestTest | 3 | 0 | merged manifest: no INTERNET, no ACCESS_NETWORK_STATE, no unknown permissions |
| ShareIngestTest | 6 | 0 | verbatim text, link-only → source_url, subject→title, empty→null, link-with-space ≠ link |
| NoteDaoTest | 7 | 0 | insert/fetch, revision-guard rejects stale writes, soft-delete/restore, **FTS5 search across columns (multi-token AND)**, hostile-input FTS safety, deleted excluded from search, LIKE fallback |
| NoteCommandHandlerTest | 8 | 0 | create seeds INBOX + audit; empty capture throws & writes nothing; verbatim original; **edit→undo restores previous text (rev 3)**; undo-of-create soft-deletes then restore; status undo; missing note throws; share note keeps source URL/app/title |
| NoteExporterTest | 3 | 0 | JSON export **round-trips** (decode ↔ payload), excludes deleted; markdown readable; file writes to private dir |

**FTS5 note:** tests run the real SQLite with FTS5 (BundledSQLiteDriver + `sqlite-bundled-jvm`
desktop natives) — the same engine ships in the APK, so search behavior is identical on-device.

**Apk:** `app/build/outputs/apk/debug/app-debug.apk` (~36 MB; bundling real SQLite ≈ +10 MB — D3).

## Verified on-device? NOT YET — Checkpoint 0 is Jason's

The following Checkpoint 0 items **cannot be verified from this Mac** (no phone attached) and are
still required before Phase 1 starts:
- install + launch on the S26 Ultra;
- typed note survives force-kill and reboot;
- capture-to-saved time (target < 2 s);
- end-to-end share sheet path (toast + note visible);
- airplane-mode behavior (guaranteed by design: no INTERNET permission at all).

Re-run locally anytime:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest
```

Fixes discovered during this run (kept for the record): room3 has no room2-style
`withTransaction` (→ `withWriteTransaction`); kotlinx.serialization builder property is
`ignoreUnknownKeys`; JUnit `assertThrows` can't host suspend calls (→ try/fail/catch);
BundledSQLiteDriver needs the JVM-variant artifact on the test classpath (D3).
