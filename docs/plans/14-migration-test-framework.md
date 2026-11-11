# Plan 14 — Migration test framework

**State today:** the harness already exists. `MigrationTest` uses Room 3's `MigrationTestHelper`
with `BundledSQLiteDriver` under Robolectric; schema files `1.json`, `2.json`, `3.json` are
committed; `MIGRATION_1_2` and `MIGRATION_2_3` each have one test. The schema folder is wired into
the `main` assets so the helper can find it (D20).
**This plan:** close the gaps and turn the existing tests into a template every later schema
bump copies.
**Sign-off needed:** none. **Schema change:** none.

## 1. Gaps

| # | Gap | Why it matters |
|---|---|---|
| G1 | No test migrates 1 → latest with data in **every** v1 table. | A later migration could drop or mangle rows in a table no test fills. |
| G2 | No test opens the migrated file with the real `MindPalaceDatabase` builder. | `runMigrationsAndValidate` checks the schema, not that `AppContainer`'s migration list is complete. |
| G3 | The FTS index is never checked after a migration. | A table rebuild on `notes` silently detaches `notes_fts` and its triggers. |
| G4 | `AppContainer` lists migrations by hand. | Easy to write a migration and forget to register it; the app then crashes on upgrade. |
| G5 | Nothing asserts that destructive fallback is never enabled. | One line (`fallbackToDestructiveMigration`) would erase every note on upgrade. |
| G6 | Schema JSON files ship inside the APK as assets. | Harmless but unintended; about 100 KB. |
| G7 | `MIGRATION_1_2` stamps `System.currentTimeMillis()`. | Fine in production; noted so tests do not assert on `created_at`. |

## 2. Changes

### 2.1 One list of migrations (G4)
In `core/db/Migrations.kt` add:
```kotlin
/** Every migration, in order. AppContainer and all tests use this list — nothing else. */
val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)
```
In `MindPalaceApplication.kt` replace `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` with
`.addMigrations(*ALL_MIGRATIONS.toTypedArray())`.

### 2.2 Shared fixture
New file `app/src/test/java/com/mindpalace/app/core/db/MigrationFixtures.kt`:
```kotlin
object MigrationFixtures {
    /** Inserts one row into every table that exists in schema v1. Raw SQL only. */
    fun seedV1(connection: SQLiteConnection)
    /** Row counts per table name, for before/after comparison. */
    fun rowCounts(connection: SQLiteConnection, tables: List<String>): Map<String, Long>
    val V1_TABLES = listOf("notes", "derived", "topics", "note_links", "reminders", "people",
        "places", "rules", "ai_index", "proposals", "ai_runs", "audit", "jobs")
}
```
`seedV1` inserts two notes (`n1` with `raw_text = 'seismograph reading'`, `n2`), one `derived`
row for `n1`, one link `n1 → n2`, one reminder on `n1`, and one row in each remaining table.
Take the column lists from `app/schemas/…/1.json`; every `NOT NULL` column must be given a value.

### 2.3 Tests to add to `MigrationTest`

| ID | Test name | Steps and assertions |
|---|---|---|
| T-MIG1 | `migrating 1 to latest keeps every row` | `createDatabase(1)`; `seedV1`; record `rowCounts`; close. `runMigrationsAndValidate(LATEST, ALL_MIGRATIONS)`; `rowCounts` equal for all `V1_TABLES`; `n1.raw_text` unchanged. |
| T-MIG2 | `fts still finds old notes after migrating` | After T-MIG1's migration: `SELECT count(*) FROM notes_fts WHERE notes_fts MATCH '"seismograph"'` = 1. Then `INSERT INTO notes_fts(notes_fts) VALUES('integrity-check')` does not throw. |
| T-MIG3 | `fts triggers survive migration` | After migration, insert a new note by raw SQL with `raw_text = 'isobar'`; the MATCH query for `"isobar"` returns 1. |
| T-MIG4 | `real database opens a migrated file` | Migrate as in T-MIG1, close. Build `Room.databaseBuilder(context, MindPalaceDatabase::class.java, <same file name>).setDriver(BundledSQLiteDriver()).addMigrations(*ALL_MIGRATIONS.toTypedArray()).build()`; `noteDao().countActive() == 2`; `categoryDao().observeAll().first().size == 6`; close. |
| T-MIG5 | `migration list is contiguous and reaches the current version` | Plain assertions: `ALL_MIGRATIONS.map { it.startVersion to it.endVersion }` equals `(1..LATEST-1).map { it to it + 1 }`. `LATEST` is read from the `@Database` annotation: `MindPalaceDatabase::class.java.getAnnotation(Database::class.java).version`. If the annotation is not visible at runtime, hard-code `LATEST` in the test with a comment and assert a schema file `LATEST.json` exists. |
| T-MIG6 | `a schema file exists for every version` | For `v in 1..LATEST`: `File("schemas/com.mindpalace.app.core.db.MindPalaceDatabase/$v.json").exists()` (path relative to the `app` module, which is the unit-test working directory). |
| T-MIG7 | `destructive fallback is never configured` | Plain JUnit source scan: no file under `app/src/main/java` contains `fallbackToDestructiveMigration`. |

`LATEST` is a `const val` at the top of the test file (`3` today). Bumping it is step 5 of the
template below.

### 2.4 Schema assets (G6) — optional, low priority
Move `assets.srcDirs("$projectDir/schemas")` from `main` to the `debug` source set
(`getByName("debug")`). Unit tests run against the debug variant, so the helper still finds the
files, and release APKs stop carrying them. If `MigrationTest` cannot find the schema afterwards,
revert this one change and report.

## 3. Template for every future schema bump

Copy this checklist into the plan that changes the schema (plans 08, 10, 11, 12, 15 point here).

1. Change the entity. Bump `version` in `@Database`. **Never** edit an existing migration or an
   existing schema JSON.
2. Add `val MIGRATION_N_M` to `Migrations.kt` and append it to `ALL_MIGRATIONS`.
   - Adding a nullable column: `ALTER TABLE t ADD COLUMN c TYPE`.
   - Adding a `NOT NULL` column: `ALTER TABLE t ADD COLUMN c TYPE NOT NULL DEFAULT x`, and put the
     same default on the entity with `@ColumnInfo(defaultValue = "x")`, or validation fails.
   - New table or index: copy the exact `createSql` from the new schema JSON.
   - **Do not rebuild the `notes` table** (create-copy-drop-rename). It detaches the FTS index.
     If a change seems to need it, stop and report.
3. Build once so Room writes `app/schemas/…/M.json`. Commit it.
4. Add one test `migrate N to M <what it proves>`: `createDatabase(N)`, insert rows that exercise
   the change, `runMigrationsAndValidate(M, ALL_MIGRATIONS)`, assert old rows are intact and new
   columns hold their defaults.
5. Bump `LATEST` in `MigrationTest`. T-MIG1…T-MIG6 now cover the new version automatically.
6. Bump `versionCode`/`versionName` (D12). Add a `DECISIONS.md` entry and a `CHANGELOG.md` line.
7. CI's "schema file is unchanged by the build" step must pass (it fails if step 3 was skipped).

## 4. Expected schema versions

These are the numbers each plan uses in its own text. **Whichever plan runs first takes v4**;
each later one takes the next free number and renames its migration to match (`INDEX.md` §2).

| Version | Plan | Change |
|---|---|---|
| 4 | 10 Rules | `rules`: add `name`, `position`, `created_at` |
| 5 | 12 AI | `jobs`: add `last_error` |
| 6 | 11 Reminders (Part A) | `reminders`: add `fire_at`, `fired_at` |
| 7 | 08 Voice | `notes`: add `audio_path` — **only if** owner decision OD-5 keeps audio |

## 5. Files allowed

`core/db/Migrations.kt`, `MindPalaceApplication.kt` (the one builder line),
`app/build.gradle.kts` (only §2.4), `app/src/test/…/core/db/MigrationTest.kt`,
new `MigrationFixtures.kt`. Nothing else.

## 6. Done when

- T-MIG1…T-MIG7 pass in CI; total test count goes up by 7.
- `app/schemas` is unchanged (no entity was touched).
- `DECISIONS.md` has a short entry: "`ALL_MIGRATIONS` is the single registration point".

## 7. Stop and report if

- `MigrationTestHelper` cannot run two tests on the same file name (use a unique file per test).
- T-MIG2 or T-MIG3 fails: that is a real finding about FTS after migration, not a test bug.
