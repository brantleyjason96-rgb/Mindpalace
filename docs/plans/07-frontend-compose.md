# Plan 07 — Frontend: restructure the Compose UI screen by screen

**State today:** the UI is built. `MainActivity.kt` is one 4,533-line file holding one
`MainViewModel`, sixteen screens, and all shared components. Four bottom tabs (Capture,
Timeline, Vault, Privacy & Rules). Navigation is fifteen `rememberSaveable` booleans plus
`openNoteId`, resolved by a fixed-priority `when` (664–708). Strings are in `strings.xml` (225
of them); state collection is lifecycle-aware; the theme has light and dark.
**The brief asked for:** screens, ViewModel boundaries against `AppContainer`, navigation, state
classes, and previews, buildable screen by screen. Because the screens exist, this plan is a
**restructure with behavior fixes**, not a rebuild. Look and feel do not change
(`docs/intent/frontend-design.md` stays the design reference).
**Sign-off needed:** none. **Schema change:** none. **New dependencies:** none (see §3).

## 1. Problems this plan fixes

| # | Problem | Where |
|---|---|---|
| 1 | One file; a faster model cannot safely edit a 4,500-line file screen by screen. | whole file |
| 2 | No back stack. A note opened from Settings → Reminders stays hidden behind Settings. Opening a note from a notification does not clear other screens. | 664–708, 745 |
| 3 | `NoteDetailScreen` closes itself while `allNotes` is still empty. | 2056–2064 |
| 4 | One ViewModel for everything; every screen pulls `allNotes` (all notes, full text). | 450 and six collectors |
| 5 | No error handling on writes (audit X1). | 22 launches |
| 6 | Undo snackbars issue hand-written inverse commands, duplicated in two tabs, with no staleness check and no timeout; `commands.undo` is never used. Delete and status change in note detail have no undo. | 827–833, 1059–1065, 2095, 2123 |
| 7 | The composer clears its text before the save has finished. Leaving note detail discards unsaved edits without asking. No draft survives the app being killed. | 1999–2001, 2054 |
| 8 | No Compose previews; no ViewModel tests. | — |
| 9 | Long lists are plain `Column`s inside `verticalScroll`. | rules, vocab, proposals, runs, reminders, audit, links |
| 10 | Title cannot be edited; category and tags cannot be set by hand. | `NoteDetailScreen` |

## 2. Target structure

```
app/src/main/java/com/mindpalace/app/
├── MainActivity.kt                 # setContent, intent handling, nothing else (< 120 lines)
├── ui/
│   ├── AppRoot.kt                  # Navigator + AnimatedContent + root snackbar host
│   ├── nav/AppScreen.kt            # sealed interface + Navigator
│   ├── common/                     # NoteCard, SwipeableNote, Chip, Header, SearchField,
│   │                               # EmptyState, StatusFilterRow, StatusPickerRow, UndoHost
│   ├── main/MainTabsScreen.kt      # bottom bar + four tabs
│   ├── capture/                    # CaptureTab, Composer, CaptureViewModel, CaptureUiState
│   │   ├── voice/                  # VoiceCaptureScreen (+ plan 08)
│   │   ├── photo/                  # PhotoCaptureScreen (+ plan 09)
│   │   └── ink/                    # HandwritingCaptureScreen (+ plan 09)
│   ├── timeline/                   # TimelineTab, TimelineViewModel, TimelineUiState
│   ├── detail/                     # NoteDetailScreen + sections, NoteDetailViewModel, NoteDetailUiState
│   ├── vault/                      # VaultTab, VocabScreen and its four wrappers, VaultViewModel
│   ├── rules/                      # RulesScreen, RuleRow, RulesViewModel
│   ├── reminders/                  # RemindersScreen, ReminderSection, RemindersViewModel
│   ├── ai/                         # AiProposalsScreen, AiSetupScreen, AiRunHistoryScreen, AiViewModel
│   ├── settings/                   # SettingsScreen, PrivacyRulesTab, TrashScreen, SettingsViewModel
│   ├── theme/Theme.kt              # unchanged
│   └── Buttons.kt, CaptureIcons.kt, TabIcons.kt   # unchanged
```

## 3. Navigation (no new library)

`CLAUDE.md` says to ask before adding dependencies. Navigation 3 (1.2.1) and navigation-compose
(2.10.2) are both stable, but a 60-line back stack does the job and keeps the existing
`AnimatedContent` transitions. Owner may choose a library later (OD-10); the screen files will
not care.

```kotlin
// ui/nav/AppScreen.kt
sealed interface AppScreen {
    data object Main : AppScreen
    data object VoiceCapture : AppScreen; data object PhotoCapture : AppScreen
    data object HandwritingCapture : AppScreen
    data object Trash : AppScreen; data object Rules : AppScreen
    data object Categories : AppScreen; data object Topics : AppScreen
    data object People : AppScreen; data object Places : AppScreen
    data object Reminders : AppScreen
    data object AiProposals : AppScreen; data object AiSetup : AppScreen; data object AiRunHistory : AppScreen
    data object Settings : AppScreen
    data class NoteDetail(val noteId: String) : AppScreen
    fun encode(): String                    // "Main", "NoteDetail:<id>"
    companion object { fun decode(s: String): AppScreen? }
}

class Navigator(initial: List<AppScreen> = listOf(AppScreen.Main)) {
    val stack: SnapshotStateList<AppScreen>
    val current: AppScreen get() = stack.last()
    fun push(screen: AppScreen)             // no-op if screen == current
    fun pop(): Boolean                      // false when only Main is left
    fun resetTo(vararg screens: AppScreen)  // used by notification opens: resetTo(Main, NoteDetail(id))
    companion object { val Saver: Saver<Navigator, List<String>> }
}
```
`AppRoot` holds `rememberSaveable(saver = Navigator.Saver) { Navigator() }`, one
`BackHandler(enabled = stack.size > 1) { navigator.pop() }`, and `AnimatedContent(targetState =
navigator.current)`. Per-screen `BackHandler`s are removed except where a screen must intercept
(dirty editor, non-empty voice draft). The selected tab stays a `rememberSaveable` inside
`MainTabsScreen`.

Notification open: `MainActivity` reads `EXTRA_OPEN_NOTE_ID` in `onCreate` **only when
`savedInstanceState == null`** and in `onNewIntent`, then removes the extra and calls
`navigator.resetTo(Main, NoteDetail(id))`.

## 4. ViewModels and state classes

Rule for every screen: one immutable `UiState` data class, one ViewModel exposing
`val state: StateFlow<UiState>` and plain functions for events, and a **stateless** composable
`XContent(state, on…)` that previews can call. The stateful wrapper `XScreen(viewModel,
navigator)` only collects state and forwards events. ViewModels receive the narrow pieces they
need from `AppContainer` through a shared `viewModelFactory { }` helper; none receives
`MindPalaceDatabase`.

All writes go through:
```kotlin
abstract class CommandViewModel(private val errors: UiErrors) : ViewModel() {
    protected fun launchCommand(block: suspend () -> Unit)   // catches Exception → errors.report(UiError)
}
sealed interface UiError { data object Conflict; data object Missing; data object Empty
                           data object UndoStale; data class Unknown(val message: String?) }
class UiErrors { val flow: SharedFlow<UiError>; fun report(e: UiError) }   // one instance in AppContainer
```
`AppRoot` collects `UiErrors.flow` and shows a snackbar with a string resource per error.

| ViewModel | Dependencies | State class |
|---|---|---|
| `CaptureViewModel` | `commands`, `noteDao` (inbox preview), `ruleDao`, `draftStore`, `captureFiles` | `CaptureUiState(title, body, mode, saving, rulePreview: String?, inbox: List<NoteListItem>, draftRecovered: Boolean)` |
| `TimelineViewModel` | `commands`, `noteDao`, `noteSearch` | `TimelineUiState(query, statusFilter, items: List<NoteListItem>, searching: Boolean, justSavedId: String?)` |
| `NoteDetailViewModel(noteId)` | `commands`, `noteDao`, `auditDao`, `derivedDao`, `noteLinkDao`, `proposalDao`, `categoryDao` | `sealed NoteDetailUiState { Loading; Missing; Ready(note, editTitle, editBody, dirty, effectiveCategory, tags, derived, history: List<RevisionItem>, links, pendingProposals, categories) }` |
| `VaultViewModel` | `vocabCommands`, the four vocabulary DAOs, `derivedDao` | `VaultUiState(categories, topics, people, places: List<VocabItem>)` with `VocabItem(name, noteCount, createdAt)` |
| `RulesViewModel` | `ruleCommands`, `ruleDao`, `categoryDao` | `RulesUiState(rules: List<RuleItem>, categories, sampleText, sampleOutcome)` |
| `RemindersViewModel` | `reminderCommands`, `reminderDao`, `noteDao` | `RemindersUiState(due, upcoming: List<ReminderItem>, canExact: Boolean, canNotify: Boolean)` |
| `AiViewModel` | `commands` (proposals), `proposalDao`, `aiRunDao`, `modelInstaller` | `AiUiState(pending: List<ProposalItem>, runs: List<RunItem>, model: ModelStatus, installing: InstallProgress?)` |
| `SettingsViewModel` | `exporter`, `importer` (plan 15), `noteDao` | `SettingsUiState(noteCount, trash: List<NoteListItem>, exporting, lastExport: String?)` |

`NoteListItem` is the projection from audit D1 (id, title, 240-character preview, status,
source, timestamps, revision, hasImage, attachmentFileName). Row items (`RuleItem`,
`ReminderItem`, `ProposalItem`, `RunItem`, `RevisionItem`) are plain data classes with every
display string already resolved to a value or a string-resource id, so composables do no lookups
(this removes the `allNotes.find` calls at 2387, 3085, 4478).

New DAO queries (in `core/db/Daos.kt`): `NoteDao.observeList(status, limit)`,
`observeById(id)`, `observeDeleted(limit)`, `observeCountActive()`,
`observeByIds(ids)`. Search uses `NoteSearch` from plan 01 step 1c, exposed as a `Flow`.

## 5. Undo, done once

1. Handler mutators return what the UI needs:
   `data class CommandResult(val noteId: String, val auditId: Long, val revision: Int)`.
   `create` keeps returning `NoteEntity` and gains `createWithResult`. Existing callers that
   ignore the result keep compiling.
2. `ui/common/UndoHost.kt`: `class UndoController { fun offer(messageRes: Int, auditId: Long) }`
   held by `AppRoot`. It shows a snackbar with `SnackbarDuration.Long`, an Undo action, and a
   dismiss action. Undo calls `commands.undo(auditId)` through `launchCommand`; an
   `UndoStaleException` shows "This note changed since — open it to review".
3. Every delete, status change, and save offers undo through the controller: both tabs **and**
   note detail. The two `showUndo` copies are deleted.
4. Depends on plan 01 step 1b (stale-safe `undo`).

## 6. Draft and edit safety

- **Composer:** `title`/`body` live in `CaptureViewModel`, are written to `DraftStore`
  (`text.json`, plan 08 §3.3) debounced 300 ms, and are cleared **after** `create` returns.
  On open, a non-empty draft is restored and `draftRecovered` shows a one-line notice.
- **Note detail:** `dirty = editBody != note.rawText || editTitle != note.title`. Back with
  `dirty` asks Save / Discard / Keep editing. Save passes `expectedRevision = note.revision`; a
  `Conflict` error offers "Reload" (loses local edits after confirmation) or "Copy my text".
  If the note changes underneath while not dirty, the fields follow it.
- **Title:** editable field calling `updateTitle` (plan 01 H2).
- **Category and tags:** picker and chips from plan 10 §4.

## 7. Previews

Each stateless `XContent` gets, in the same file:
```kotlin
@Preview(name = "light") @Preview(name = "dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "font 200%", fontScale = 2f) @Preview(name = "RTL", locale = "ar")
annotation class ScreenPreviews
```
and at least three previews using fixed sample data from `ui/common/PreviewData.kt`: empty,
typical (5 items), and stress (long title, 40-word preview, longest category name). Previews
must compile without a database, a `Context` service, or a ViewModel.

## 8. Steps (one commit each; CI must be green before the next)

| Step | What | Behavior change? |
|---|---|---|
| 0 | Add `MainViewModelTest` (§9) against today's code as a safety net. | none |
| 1 | **Move only.** Cut composables out of `MainActivity.kt` into the files in §2, unchanged. `private` becomes `internal` where needed. `MainViewModel` moves to `ui/MainViewModel.kt` unchanged. | none; diff must be moves plus imports |
| 2 | `AppScreen`, `Navigator`, `AppRoot`; delete the fifteen booleans; fix notification open. | back works from any depth |
| 3 | `CommandViewModel`, `UiErrors`, root error snackbar; `VocabCommands` for add topic/person/place/category and `LinkCommands.addLink`. | errors show instead of crashing |
| 4 | `UndoController`; handler returns `CommandResult`. | undo is stale-safe and everywhere |
| 5 | `TimelineViewModel` + `TimelineContent` + previews; `NoteListItem`; `LazyColumn`. | Timeline no longer loads full text |
| 6 | `CaptureViewModel` + `CaptureContent`/`Composer` + previews; draft. | draft survives kill |
| 7 | `NoteDetailViewModel` + content + previews; Loading/Missing; dirty prompt; title edit. | notification taps open the note |
| 8 | `VaultViewModel`, `RulesViewModel` + contents + previews. | none beyond plan 10 if merged |
| 9 | `RemindersViewModel`, `AiViewModel`, `SettingsViewModel` + contents + previews; `LazyColumn`s. | none |
| 10 | Delete `MainViewModel`; add `ArchitectureTest` (no DAO writes outside `core/commands`, `core/db`, `ai/AiNightRunner.kt`); update `CLAUDE.md` "Where things are". | none |

Steps 5–9 are independent of each other after step 4 and can go in any order. Plans 08, 09, 10,
11, 12 each name composables by their current line ranges; after step 1 the same names live in
the files of §2.

## 9. Tests

`MainViewModelTest` → later split per ViewModel. Robolectric, in-memory database, the **real**
`NoteCommandHandler` (no mocks; failures are produced by closing the database),
`StandardTestDispatcher`, `Dispatchers.setMain`. At step 0, write only the tests that today's
`MainViewModel` can pass (T-UI3, T-UI5); add each of the others with the step that makes it pass.

| ID | Test name | Assert |
|---|---|---|
| T-UI1 | `capture saves and clears the draft only after the save` | call `capture()`; before `advanceUntilIdle()` the draft file still holds the text; after it, one note exists, `state.body == ""`, and the draft file is gone. |
| T-UI2 | `a failed save keeps the text and reports an error` | close the database first → `state.body` unchanged, draft file still present, `UiErrors` emitted `Unknown`. |
| T-UI3 | `search debounces and drops stale results` | set "a", "ab", "abc" within 100 ms → one query for "abc". |
| T-UI4 | `search updates when a note is added elsewhere` | create through `commands` directly → results change without a ViewModel call. |
| T-UI5 | `status filter limits the list` | INBOX filter → only inbox items. |
| T-UI6 | `undo after the note changed reports UndoStale and does not crash` | delete, edit elsewhere, undo → error emitted. |
| T-UI7 | `note detail is Loading, then Ready, and Missing for an unknown id` | three states in order. |
| T-UI8 | `note detail save with a stale revision reports Conflict and keeps the edit` | `dirty` still true. |
| T-UI9 | `a recovered draft is offered once` | write `text.json`; new ViewModel → `draftRecovered == true`. |
| T-UI10 | `navigator pops in order and never pops Main` | push A, B; pop → A; pop → Main; pop → false. |
| T-UI11 | `navigator survives save and restore` | `Saver` round trip keeps `NoteDetail("n1")`. |
| T-UI12 | `resetTo replaces the stack` | from [Main, Settings, Trash] → [Main, NoteDetail]. |
| T-UI13 | `AppScreen encode and decode round trip for every case` | all sixteen. |

## 10. Files allowed

Everything under `app/src/main/java/com/mindpalace/app/ui/` and `MainActivity.kt`;
`core/db/Daos.kt` (the read queries in §4 only); `core/commands/*` (result type,
`VocabCommands`, `LinkCommands`); `MindPalaceApplication.kt`; `strings.xml`; tests; `CLAUDE.md`
(step 10). **Not:** entities, migrations, `ai/*`, `core/reminders/*`, the manifest, Gradle files.

## 11. Done when

- `MainActivity.kt` is under 120 lines; no file under `ui/` is over 600 lines.
- Every screen has a stateless content composable with the four-way preview set.
- CI green; `ArchitectureTest` passes.
- Device check (owner): the regression checklist in `docs/CHECKPOINT_0.md`, plus: tap a reminder
  notification → the note opens; Settings → Reminders → open a note → Back returns to Reminders;
  type in the composer, swipe the app away, reopen → the text is back.
- `DECISIONS.md` entry: navigation approach, ViewModel boundaries, undo controller.

## 12. Stop and report if

- Step 1 cannot be done as pure moves (a composable depends on file-private state).
- Any step needs a new Gradle dependency.
- A preview needs a real `Context` service to render.
