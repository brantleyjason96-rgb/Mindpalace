# Plan 10 — Rules engine, version 2

**State today (D16):** `core/rules/RuleEngine.kt` is a pure evaluator. A rule is "any of these
keywords (case-insensitive substring) → this category". The first enabled match wins. It runs
once, inside `NoteCommandHandler.create`, and adds a `derived` CATEGORY row with
`origin = rule:<id>` beside the system `Inbox` row. `RulesScreen` can add, toggle, delete, and
test rules; it cannot edit one. Rule writes go straight from `MainViewModel` to `RuleDao`.
**This plan:** fix ordering, decide which category wins, add the owner's manual override, re-run
rules when text changes, and make derived changes audited and undoable.
**Sign-off needed:** none (no permission). Two owner decisions: OD-7, OD-8.
**Schema change:** v3 → v4 (follow `14-migration-test-framework.md` §3).

## 1. Problems being fixed

| ID | Problem |
|---|---|
| G1a | `RuleDao.enabled()` is `ORDER BY id`; ids are random UUIDs, so "first match" is arbitrary. |
| G1b | Substring match: keyword `art` matches "start". |
| G1c | Rules run only at capture. Editing a note, adding a rule, or toggling one changes nothing. |
| G1d | Deleting a rule leaves its `derived` rows behind, with an origin that no longer resolves. |
| G2a | No rule says which CATEGORY row is the note's category. Vault counts a filed note under both `Inbox` and its category. |
| G2b | Owner decision (D25): a hand-picked category is permanent. No command or UI exists. |
| W1 | Rule writes bypass `core/commands`; derived writes are not audited or undoable. |

## 2. Data model

### 2.1 `rules` table (v4)
Add three columns. Existing rows get defaults in the migration.

| Column | Type | Default for old rows | Meaning |
|---|---|---|---|
| `name` | TEXT NOT NULL | `''` | Label shown in the list. Empty → UI shows the keywords. |
| `position` | INTEGER NOT NULL | `0`, then renumbered | Evaluation order, ascending. |
| `created_at` | INTEGER NOT NULL | `0` | Tie-break and display. |

Migration `MIGRATION_3_4`: three `ALTER TABLE rules ADD COLUMN … NOT NULL DEFAULT …`, then
renumber so existing rules keep one stable order:
```sql
UPDATE rules SET position = (SELECT COUNT(*) FROM rules r2 WHERE r2.id < rules.id)
```
Entity fields need matching `@ColumnInfo(defaultValue = …)`.

### 2.2 Matcher and action JSON (backward compatible)
Old JSON must still decode. Every new field has a default.

```kotlin
@Serializable data class RuleMatcher(
    val keywords: List<String>,
    val mode: MatchMode = MatchMode.ANY,          // ANY | ALL
    val wholeWord: Boolean = false,               // false keeps today's behavior for old rules
    val sources: List<String> = emptyList(),      // NoteSource names; empty = any source
)
@Serializable data class RuleAction(
    val category: String? = null,                 // was non-null; null = "tags only"
    val tags: List<String> = emptyList(),
)
```
`wholeWord = true` means the keyword must be bounded by non-letter, non-digit characters or the
text edge on both sides (use `Character.isLetterOrDigit`; no regex built from user input). New
rules created in the UI default to `wholeWord = true`.
A rule with no category and no tags is invalid; `toRule()` returns `null` for it.

### 2.3 Evaluation (pure, in `RuleEngine`)
```kotlin
data class RuleInput(val text: String, val source: NoteSource)
data class RuleOutcome(val category: RuleHit?, val tags: List<RuleHit>)
data class RuleHit(val ruleId: String, val value: String)
fun evaluate(rules: List<Rule>, input: RuleInput): RuleOutcome
```
- Rules are evaluated in the order given (`position ASC, created_at ASC, id ASC` from the DAO).
- **Category:** the first enabled matching rule that has a category wins. Later category rules
  are ignored.
- **Tags:** the union of tags from **all** enabled matching rules, de-duplicated
  case-insensitively, first spelling kept.
- `text` is `title`, `rawText`, `transcript`, `ocrText`, `attachmentFileName` joined by `"\n"`
  (today it is a space; a newline stops a keyword matching across two fields).
- Keep `firstMatch` as a thin wrapper so existing tests still compile.

## 3. Derived facts: resolution, commands, audit

### 3.1 Which value wins — `DerivedResolver` (pure, `core/model`)
```kotlin
object DerivedResolver {
    fun effectiveCategory(rows: List<DerivedEntity>): DerivedEntity?
    fun tags(rows: List<DerivedEntity>): List<String>
}
```
Precedence for CATEGORY, highest first: `user` > `ai:*` > `rule:*` > `system`. Ties: newest
`created_at`. An approved AI proposal ranks above a rule because the owner approved it by hand.
**This order is owner decision OD-7**; build it as one `val PRECEDENCE` list so it is a one-line
change.

Every screen that shows or counts a category uses the resolver:
- `DerivedFactsSection` marks the effective row and greys the others.
- Vault counts change from "rows per value" to "notes whose effective category is this value".
  New query for the counts:
  ```sql
  SELECT note_id AS noteId, value, origin, created_at AS createdAt
  FROM derived WHERE field = 'CATEGORY'
  ```
  resolved in Kotlin with the resolver (fine up to tens of thousands of rows; plan 17 measures).

### 3.2 New handler commands (`NoteCommandHandler`)
All run in one write transaction and write one audit row.

| Command | Effect |
|---|---|
| `setCategory(noteId, category, expectedRevision?)` | Deletes this note's `CATEGORY` rows with origin `user`, inserts one `user` row. Does not touch rule, AI, or system rows. `category` must exist in `categories`, else `IllegalArgumentException`. |
| `clearCategoryOverride(noteId)` | Deletes the `user` CATEGORY row; rules win again. |
| `addTag(noteId, tag)` / `removeTag(noteId, tag)` | `user`-origin TAG row. `removeTag` on a rule- or AI-made tag inserts nothing and deletes that row (the audit keeps it). |
| `reapplyRules(noteId, actor = RULE)` | Deletes the note's rows whose origin starts with `rule:` (CATEGORY and TAG), evaluates, inserts the outcome. No-op (no audit row) if the before and after sets are equal. |
| `reapplyRulesToAll(actor = USER): Int` | Calls the above for every active note, in chunks of 200 per transaction; returns how many notes changed. |

The manual override satisfies D25 through precedence: rules still record what they would have
chosen, but the `user` row always wins. Record this reading of D25 in `DECISIONS.md` (**OD-8**
if the owner wants rules to skip such notes entirely instead).

### 3.3 Audit and undo for derived changes
- `entity = "derived"`, `entity_id = noteId`. New `AuditAction` constants: `DERIVED_SET`.
- `before` / `after` are JSON arrays of
  `DerivedSnapshot(field, value, origin, confidence, createdAt)` holding **all** of the note's
  derived rows for the affected field(s) before and after the command.
- `undo(auditId)` for a `derived` row: read the note's current rows for those fields; if they do
  not equal `after`, throw `UndoStaleException`; otherwise replace them with `before` and write
  an `UNDO` audit row. This reuses the rule from plan 01 U1.
- `create` keeps seeding `Inbox` and running rules inside its own transaction without a separate
  derived audit row (the CREATE row covers it). Switch its `applyRules` to `evaluate`.

### 3.4 When rules run

| Trigger | Action |
|---|---|
| Note created | As today, inside `create`. |
| `updateRawText`, `updateTitle` | Call `reapplyRules(noteId)` in the same transaction, after the text change. |
| Rule added, edited, toggled, deleted, reordered | UI offers "Apply to existing notes" → `reapplyRulesToAll`. Not automatic. |
| Rule deleted | In the same transaction, delete all `derived` rows with `origin = 'rule:<id>'`. |

### 3.5 `RuleCommands` (new, `core/commands/RuleCommands.kt`)
Moves the three ViewModel DAO writes behind one class. Not audited (rules are settings, and they
are exported by plan 15), but every change bumps `version`.
```kotlin
class RuleCommands(db: MindPalaceDatabase, now: () -> Long, newId: () -> String) {
    suspend fun add(name: String, matcher: RuleMatcher, action: RuleAction): Rule   // position = max + 1
    suspend fun update(id: String, name: String, matcher: RuleMatcher, action: RuleAction)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun delete(id: String)                // also removes its derived rows
    suspend fun move(id: String, toPosition: Int) // renumbers 0..n-1 in one transaction
}
```
`RuleDao`: change both list queries to `ORDER BY position ASC, created_at ASC, id ASC`; add
`maxPosition()`, `updatePosition(id, position)`; add `DerivedDao.deleteByOrigin(origin)` and
`deleteForNoteByOriginPrefix(noteId, prefix)` (`origin LIKE :prefix || '%'`).

## 4. UI changes (`MainActivity.kt`, `RulesScreen` 3243–3420 and `NoteDetailScreen`)

1. `RulesScreen`: tap a row to edit it in the same form used for "new rule"; name field; "match
   all keywords" switch; "whole words only" switch (on for new rules); optional tags field;
   up/down buttons to reorder (buttons, not drag, so it works with TalkBack).
2. After any rule change show a snackbar: "Apply to existing notes?" → runs
   `reapplyRulesToAll`, then "Updated N notes" with Undo disabled (bulk undo is out of scope;
   say so in the snackbar: "This cannot be undone in one step").
3. Test box: show the full `RuleOutcome` (category and tags, with the rule that produced each).
4. `NoteDetailScreen`: a category picker (from `model.categories`) that calls `setCategory`;
   a "Let rules decide" item that calls `clearCategoryOverride`; tag chips with add and remove.
5. `MainViewModel`: `addRule`, `setRuleEnabled`, `deleteRule` call `RuleCommands`; add
   `updateRule`, `moveRule`, `setCategory`, `clearCategoryOverride`, `addTag`, `removeTag`,
   `reapplyRulesToAll`. All through `launchCommand` (plan 01 X1).
6. New strings go in `strings.xml`; no hard-coded text.

## 5. Tests

`RuleEngineTest` (plain JUnit):

| ID | Test name | Assert |
|---|---|---|
| T-RL1 | `whole word match does not hit inside a longer word` | `art` vs "start the car" → no match; vs "art class" → match; vs "Art." → match. |
| T-RL2 | `ALL mode needs every keyword` | `["quiz","friday"]` ALL vs "quiz today" → no; vs "Friday quiz" → yes. |
| T-RL3 | `source filter limits a rule` | `sources=["VOICE"]` does not match a TEXT note. |
| T-RL4 | `first category rule wins and tags are unioned` | rules A (cat X, tag a), B (cat Y, tag b) both match → category X by A; tags a, b. |
| T-RL5 | `tags de-duplicate case-insensitively` | "Lab" and "lab" → one tag "Lab". |
| T-RL6 | `old json without new fields still decodes` | `{"keywords":["a"]}` and `{"category":"X"}` → `mode ANY`, `wholeWord false`, tags empty. |
| T-RL7 | `a rule with no category and no tags is skipped` | `toRule()` returns null. |
| T-RL8 | `a keyword does not match across two fields` | title "oat", text "milk", keyword "oat milk" → no match. |

`DerivedResolverTest` (plain JUnit):

| ID | Assert |
|---|---|
| T-DR1 | user beats ai beats rule beats system, in every pairing. |
| T-DR2 | two rule rows → newest `createdAt` wins. |
| T-DR3 | only a system row → `Inbox`. Empty list → null. |
| T-DR4 | tags are the union across origins, in first-seen order. |

`NoteCommandHandlerTest` (Robolectric):

| ID | Test name | Assert |
|---|---|---|
| T-RC1 | `rules are evaluated in position order` | two matching rules, positions 1 and 0 → the position-0 rule's category. Remove `@Ignore` from T-ORD1 in plan 02. |
| T-RC2 | `editing text re-evaluates rules` | note "hello"; rule "quiz"→Classroom; `updateRawText("quiz friday")` → rule row present; edit back → rule row gone. |
| T-RC3 | `manual category wins and survives a rule re-run` | rule gives Classroom; `setCategory("Personal")`; `reapplyRules` → effective category Personal; rule row still Classroom. |
| T-RC4 | `clearing the override lets the rule win again` | effective → Classroom. |
| T-RC5 | `setCategory rejects an unknown category` | `IllegalArgumentException`; nothing written. |
| T-RC6 | `setCategory is audited and undoable` | audit `entity == "derived"`, action `DERIVED_SET`; undo → user row gone; second undo throws `UndoStaleException`. |
| T-RC7 | `undo of a derived change is refused after a later change` | setCategory(A) (audit X); setCategory(B); `undo(X)` throws. |
| T-RC8 | `reapplyRules with no change writes no audit` | audit count unchanged. |
| T-RC9 | `deleting a rule removes its derived rows` | via `RuleCommands.delete`. |
| T-RC10 | `reapplyRulesToAll returns the number of notes changed` | 3 notes, 2 match a new rule → 2. |
| T-RC11 | `move renumbers positions without gaps` | three rules; move last to 0 → positions 0,1,2 in the new order. |

`MigrationTest`: `migrate 3 to 4 keeps rules and gives them distinct positions` — insert two
rules at v3; after migration both exist, `name == ''`, positions are `{0, 1}`. Bump `LATEST`.

## 6. Files allowed

`core/rules/RuleEngine.kt`, `core/commands/NoteCommandHandler.kt`, new
`core/commands/RuleCommands.kt`, `core/model/Model.kt` (new enum constants, `DerivedSnapshot`,
`MatchMode`), new `core/model/DerivedResolver.kt`, `core/db/Daos.kt`, `core/db/SupportEntities.kt`
(`RuleEntity` only), `core/db/Migrations.kt`, `core/db/MindPalaceDatabase.kt` (version),
`MindPalaceApplication.kt` (expose `ruleCommands`), `MainActivity.kt` (only `MainViewModel`,
`RulesScreen`, `RuleRow`, `NoteDetailScreen`, `DerivedFactsSection`, `VaultTab` counts),
`strings.xml`, the three test files, `app/schemas/…/4.json` (generated).

**Do not touch:** `ai/*`, reminders, capture screens, the FTS entity, `1.json`–`3.json`.

## 7. Done when

- All tests above pass in CI; `PrivacyManifestTest` unchanged and green.
- On the phone: a rule with keyword `art` no longer files a note containing "start"; picking a
  category by hand sticks after editing the note; Vault shows each note under one category.
- `DECISIONS.md`: one entry covering rule order, whole-word default, precedence (OD-7), the
  reading of D25 (OD-8), and derived audit. `CHANGELOG.md` updated; version bumped (D12).

## 8. Stop and report if

- The v4 migration needs anything other than `ALTER TABLE … ADD COLUMN` and `UPDATE`.
- The owner has not answered OD-7. Default to the order in §3.1 and flag it in the report.
