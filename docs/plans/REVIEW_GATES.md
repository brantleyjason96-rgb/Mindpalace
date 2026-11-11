# Review gates

Run this after every executed plan step, before merging. The executing model fills in the
"Report" block at the bottom; the reviewer (the planning model or the owner) checks each line
independently. A gate with any unchecked **Required** line is a "do not merge".

Time: about 10 minutes for the reviewer, plus the device checks.

## Gate A — Every step (Required)

| # | Check | How to verify |
|---|---|---|
| A1 | CI is green on the exact commit being merged. | Open the "Android build" run for the head commit. All steps green, not cancelled. |
| A2 | The test count did not go down, and the new tests named in the plan exist. | Test report artifact → total. Compare with the last entry in `docs/TEST_RESULTS.md`. Search the diff for each test name. |
| A3 | No test was weakened. | In the diff: no deleted assertions, no new `@Ignore` beyond those the plan names, no widened tolerances. |
| A4 | `PrivacyManifestTest` is green **and was not edited** (unless the plan is a signed-off permission change). | `git diff <base>..<head> -- app/src/test/java/com/mindpalace/app/PrivacyManifestTest.kt` is empty. |
| A5 | No new permission. | `git diff <base>..<head> -- app/src/main/AndroidManifest.xml` shows no `uses-permission` line; CI step "Merged manifest permissions are all signed off" is green. |
| A6 | No new dependency the plan did not name. | `git diff <base>..<head> -- gradle/libs.versions.toml app/build.gradle.kts`. |
| A7 | Only allowed files changed. | `git diff --stat <base>..<head>` against the plan's "Files allowed". |
| A8 | Canonical writes still go through `core/commands`. | Search the diff for `Dao().` / `Dao.` write calls (`insert`, `upsert`, `update`, `delete`, `setState`, `setEnabled`) outside `core/commands`, `core/db`, `ai/AiNightRunner.kt`, and `src/debug`. Once `ArchitectureTest` exists, it is green. |
| A9 | Note text is never rewritten outside an audited user edit. | Search the diff for `raw_text`, `rawText =`, `transcript =`, `ocrText =` assignments; each must be inside a handler command that writes an audit row. |
| A10 | `docs/DECISIONS.md` has a new entry (next number, ascending) for every non-obvious choice. | Read it. It says what, why, and what was rejected. |
| A11 | `docs/CHANGELOG.md` has a line; `docs/TEST_RESULTS.md` has the new count and the CI run link. | Read them. |
| A12 | The report lists what the executing model was unsure about. | "Nothing" is acceptable only for mechanical steps. Spot-check one claim against the code. |

## Gate B — Steps that change the schema (Required when applicable)

| # | Check | How to verify |
|---|---|---|
| B1 | `@Database(version)` went up by exactly one. | Diff of `MindPalaceDatabase.kt`. |
| B2 | A new `app/schemas/…/<N>.json` is committed; older JSON files are byte-identical. | `git diff --stat` shows one added schema file and no modified ones. |
| B3 | The migration is in `ALL_MIGRATIONS`. | Diff of `Migrations.kt`. |
| B4 | A migration test for N−1 → N exists and `LATEST` was bumped. | Diff of `MigrationTest.kt`; T-MIG1…T-MIG6 green. |
| B5 | The `notes` table was not rebuilt; no `DROP TABLE`; no `fallbackToDestructiveMigration`. | Read the migration SQL. |
| B6 | `versionCode` and `versionName` were bumped (D12). | Diff of `app/build.gradle.kts`. |
| B7 | The owner has a current export before installing. | Ask. Until plan 15 is merged this is a text-only export; say so. |

## Gate C — Steps that touch permissions (Required when applicable)

| # | Check | How to verify |
|---|---|---|
| C1 | The owner's sign-off is written down with a date, in the owner's words, **before** the code was written. | `docs/PERMISSIONS.md` entry; the matching SO-/OD- row in `INDEX.md`. A model's summary of a conversation is not a sign-off. |
| C2 | `docs/PERMISSIONS.md`, the manifest, and `PrivacyManifestTest`'s approved list changed in the same commit. | One commit touches all three. |
| C3 | The in-app permission explainer says what the permission is for and what happens if it is refused; refusing does not dead-end. | Device check: refuse once, refuse twice. |

## Gate D — Device checks (Required for user-visible changes)

| # | Check | How to verify |
|---|---|---|
| D1 | Installed **over** the existing app without uninstalling. Existing notes are still there. | `scripts/checkpoint0.sh` step 1 and a look at the Timeline. |
| D2 | The plan's own "Device check" list passed. | Each row logged in `docs/TEST_RESULTS.md`. |
| D3 | The regression run passed. | `scripts/checkpoint0.sh` → every row PASS (SKIP allowed for the reboot only when the change cannot affect boot). |
| D4 | After a schema change: an old note opens, search finds an old word, a reminder set before the update still shows. | By hand. |

## Gate E — Spot checks the reviewer rotates through (Recommended)

Pick two per review.

| # | Check |
|---|---|
| E1 | Read one new test and confirm it would fail if the feature were removed. |
| E2 | Read the largest changed function end to end for an unhandled exception path. |
| E3 | New strings are in `strings.xml`; no user-visible literal in Kotlin. |
| E4 | New interactive elements have a label and a 48 dp target (after plan 16: T-A7 covers this). |
| E5 | New lists use `LazyColumn`; nothing new collects all notes. |
| E6 | Files written to disk (photos, drafts, exports) use write-to-temp-then-rename and are cleaned up on failure. |
| E7 | KDoc and `CLAUDE.md` still describe what the code does. |

## What to do when a gate fails

- **A1–A9, B, C:** do not merge. Send the step back with the failing line quoted.
- **A10–A12:** fix the documents in the same branch, then merge.
- **D:** do not merge. If the failure is in the device script rather than the app, fix the script
  in a separate commit and re-run.
- Never resolve a failure by loosening the gate in the same change.

## Report block (the executing model fills this in)

```
Plan and step:
Base branch / head commit:
CI run URL and result:
Tests: before <n> → after <n>.  New test names:
Files changed (git diff --stat):
Gate A:  A1 [ ] A2 [ ] A3 [ ] A4 [ ] A5 [ ] A6 [ ] A7 [ ] A8 [ ] A9 [ ] A10 [ ] A11 [ ] A12 [ ]
Gate B (if schema): B1 [ ] B2 [ ] B3 [ ] B4 [ ] B5 [ ] B6 [ ]     B7 is the owner's
Gate C (if permission): C1 [ ] C2 [ ]                            C3 is the owner's
Gate D: device checks the owner must run:
Decisions recorded (D-numbers):
Unsure about / deviations from the plan:
```
