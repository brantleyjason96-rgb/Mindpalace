# Plan 13 — Home-screen widget and quick capture

**State today:** not built. Capture starts inside the app (Capture tab) or from another app's
share sheet (`ShareReceiverActivity`).
**Goal:** from the home screen to a saved note in two taps, following save-first: whatever the
owner typed is never lost, even if they tap away.
**Sign-off needed:** none expected. **Gate:** `PrivacyManifestTest` must stay green with no
edit. If it fails, stop (§8).
**Schema change:** none. **New dependency:** none in the recommended option.

## 1. Entry points

| # | Entry point | What the owner does | Permission |
|---|---|---|---|
| 1 | **Quick-capture screen** (`QuickCaptureActivity`) | Small sheet over whatever is on screen: text field with the keyboard up, Save, and buttons for Voice and Photo. | none |
| 2 | **Home-screen widget** | Three buttons: Note, Voice, Photo. Optional inbox count. | none |
| 3 | **App shortcuts** | Long-press the app icon → New note / Voice / Photo. | none |
| 4 | **Text-selection action** | Select text in any app → "Save to Mind Palace" in the pop-up menu (`ACTION_PROCESS_TEXT`). | none |
| 5 | Quick Settings tile | Pull down the shade → tile opens quick capture. | none as a `uses-permission` (the service is declared with `android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`, which restricts callers; it is not requested by the app) |

Build 1–4. Entry 5 is optional; do it last and only if the owner wants it.

## 2. Widget technology

| Option | New dependency | Permission effect | Notes |
|---|---|---|---|
| **A. Classic `AppWidgetProvider` + `RemoteViews` (recommended)** | none | none | A widget with three buttons and a number needs nothing more. XML layout, about 80 lines of Kotlin. |
| B. Jetpack Glance 1.2.0 | `glance-appwidget` | Glance depends on WorkManager, which merges `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`. All four are already approved in this app (D19, D24, D26), so the merged list would not change. | Compose-style code, but a new dependency (ask first, per `CLAUDE.md`) for no visible gain here. |

**Privacy on the home screen:** the widget shows **no note text**. Anyone who can see the phone's
home screen could read it. The inbox count is the only data shown, and it can be turned off in
the widget's settings (owner decision OD-11 if note previews are ever wanted).

## 3. Quick-capture screen

`capture/QuickCaptureActivity.kt`, a `ComponentActivity` with a translucent dialog-style theme
(`Theme.MindPalace.QuickCapture`: `windowIsTranslucent`, `windowIsFloating = false`, dim behind,
`windowSoftInputMode = stateAlwaysVisible|adjustResize`), `excludeFromRecents = true`,
`taskAffinity = ""`, `launchMode = singleTask`, **`exported = false`** (widgets, shortcuts, and
the tile launch it with explicit `PendingIntent`s from this app). The `ACTION_PROCESS_TEXT`
entry is a separate tiny exported activity (§5) so this one never needs exporting.

Intent extra `mode`: `TEXT` (default), `VOICE`, `PHOTO`. `VOICE` and `PHOTO` open
`MainActivity` on the matching capture screen through
`Intent(…MainActivity).putExtra(EXTRA_OPEN_SCREEN, "VoiceCapture")`, handled next to
`EXTRA_OPEN_NOTE_ID` with `navigator.resetTo(Main, <screen>)`. Only `TEXT` is handled in the
sheet itself.

### Save-first rules (the contract)

| Event | Result |
|---|---|
| Each keystroke | Text written to `DraftStore` key `quick` (debounced 300 ms; plan 08 §3.3). |
| Save tapped, or the keyboard's Done action | `commands.create(CreateNoteRequest(source = TEXT, rawText = text))` → toast "Saved to Inbox" → draft cleared → finish. |
| Tap outside, Back, Home, or the screen turns off, **with non-blank text** | **Saved automatically** as a note (same call), toast "Saved to Inbox". Nothing typed is ever discarded by accident. |
| Same, with blank text | Finish. No note. |
| "Discard" button (explicit) | Draft cleared, finish. This is the only way to throw text away. |
| Save fails | Text stays in `DraftStore`; a notification is not possible without bothering the owner, so show a toast "Could not save — your text is kept" and reopen with the draft next time. |
| Process killed mid-typing | Next open restores the draft with "Recovered unsaved note". |

The automatic save runs in `onStop` through a function on the application-scoped container
(`container.quickCapture.saveIfNeeded()`), launched on an application `CoroutineScope` so it
finishes after the activity is gone. It is guarded by a flag so Save followed by `onStop` does
not create two notes.

Pure logic, testable without Android:
```kotlin
class QuickCapture(private val commands: NoteCommandHandler, private val drafts: DraftStore) {
    fun onTextChanged(text: String)                      // drafts.write("quick", text)
    suspend fun saveIfNeeded(): SaveOutcome              // Saved(noteId) | NothingToSave | Failed
    fun discard()
    fun recoveredDraft(): String?
}
```
`saveIfNeeded` is idempotent: it reads the draft, returns `NothingToSave` when blank, creates the
note, and clears the draft only after `create` returns.

## 4. Widget

- `widget/CaptureWidgetProvider.kt` (`AppWidgetProvider`), `res/layout/widget_capture.xml`
  (horizontal row: three 48 dp buttons with content descriptions, optional count text),
  `res/xml/capture_widget_info.xml` (min 3×1 cells, resizable horizontally,
  `widgetCategory="home_screen"`, a preview layout).
- Each button is a `PendingIntent.getActivity(…, FLAG_IMMUTABLE)` to `QuickCaptureActivity` with
  its `mode`.
- Count: `noteDao.countByStatus(INBOX)` read in `onUpdate` with `goAsync()`. Refresh by calling
  `CaptureWidgetProvider.requestUpdate(context)` (an `AppWidgetManager.updateAppWidget` call)
  from one place: an application-scoped collector of `noteDao.observeCountByStatus(INBOX)`,
  debounced 2 s. No periodic updates (`updatePeriodMillis = 0`), no WorkManager.
- Manifest: one `<receiver android:exported="true">` with the
  `android.appwidget.action.APPWIDGET_UPDATE` filter and the metadata. A widget receiver must be
  exported so the launcher can reach it; it accepts only that system action.

## 5. Shortcuts and text selection

- `res/xml/shortcuts.xml` with three static shortcuts targeting `QuickCaptureActivity` with a
  `mode` extra; referenced from `MainActivity`'s `<meta-data android:name="android.app.shortcuts">`.
  Static shortcuts may only target exported activities, so route them through `MainActivity`
  with `EXTRA_OPEN_SCREEN = "QuickText" | "VoiceCapture" | "PhotoCapture"`; `MainActivity`
  starts `QuickCaptureActivity` for `QuickText` and finishes itself.
- `capture/ProcessTextActivity.kt`: `exported = true`, `Theme.NoDisplay`, intent filter
  `ACTION_PROCESS_TEXT` + `text/plain`. Reads `EXTRA_PROCESS_TEXT` with
  `getCharSequenceExtra(...)?.toString()`, maps it through `ShareIngest.toRequest(text, null,
  referrer?.host)`, saves with the same error handling as `ShareReceiverActivity` (plan 01 C1),
  toasts, finishes. Add it to the exported allow-list in `ManifestComponentsTest` (T-MAN2).

## 6. Tests

`QuickCaptureTest` (Robolectric, in-memory DB, temp `DraftStore`):

| ID | Test name | Assert |
|---|---|---|
| T-W1 | `typing writes the draft` | `drafts.read("quick") == "milk"`. |
| T-W2 | `saveIfNeeded saves non-blank text verbatim and clears the draft` | one note, `rawText == " milk "` if that was typed; draft gone. |
| T-W3 | `saveIfNeeded twice creates one note` | count 1; second returns `NothingToSave`. |
| T-W4 | `blank text saves nothing` | count 0. |
| T-W5 | `a failed save keeps the draft` | closed database → `Failed`; draft still readable. |
| T-W6 | `discard clears the draft and saves nothing` | count 0. |
| T-W7 | `a leftover draft is offered on next open` | `recoveredDraft() == "half a thought"`. |

`QuickCaptureActivityTest` (Robolectric):

| ID | Test name | Assert |
|---|---|---|
| T-W8 | `stopping with text saves it` | type, `controller.stop()` → count 1. |
| T-W9 | `save then stop does not double save` | count 1. |
| T-W10 | `the activity is not exported` | `PackageManager` activity info `exported == false`. |

`ProcessTextActivityTest`: T-W11 selected text is saved with source `SHARE`; T-W12 empty
selection saves nothing and toasts.

`CaptureWidgetProviderTest` (Robolectric): T-W13 `onUpdate` sets three click intents targeting
`QuickCaptureActivity` with modes TEXT, VOICE, PHOTO; T-W14 the layout contains no note text
view (only the count id).

`PrivacyManifestTest` and `ManifestComponentsTest` run unchanged except the exported allow-list
gains `ProcessTextActivity` and `CaptureWidgetProvider`.

## 7. Device check (owner) — add to `docs/CHECKPOINT_0.md`

1. Add the widget. Tap Note, type "widget test", tap outside the sheet → toast; the note is in
   the Timeline.
2. Tap Note, type, press Home → the note is saved.
3. Tap Note, type, tap Discard → no note.
4. Long-press the app icon → three shortcuts; each opens the right capture.
5. In a browser, select a sentence → "Save to Mind Palace" appears in the menu (it may be under
   the ⋮ overflow) → the note is saved.
6. Lock the phone: the widget shows no note text anywhere.

## 8. Files allowed

New: `capture/QuickCaptureActivity.kt`, `capture/QuickCapture.kt`, `capture/ProcessTextActivity.kt`,
`widget/CaptureWidgetProvider.kt`, `res/layout/widget_capture.xml`,
`res/xml/capture_widget_info.xml`, `res/xml/shortcuts.xml`, widget drawables. Changed:
`AndroidManifest.xml` (two activities, one receiver, one meta-data; **no `uses-permission`
line**), `res/values/themes.xml` (one theme), `strings.xml`, `MainActivity.kt`
(`EXTRA_OPEN_SCREEN` handling only), `MindPalaceApplication.kt`, `core/db/Daos.kt`
(`countByStatus`, `observeCountByStatus`), tests.

## 9. Done when

- Tests pass in CI; **`PrivacyManifestTest` passes without being edited**.
- The six device checks are logged as PASS.
- `DECISIONS.md`: widget technology, no note text on the home screen, save-on-dismiss rule.

## 10. Stop and report if

- The merged manifest gains any permission (check the CI step "Merged manifest permissions are
  all signed off").
- The owner's launcher does not show `ACTION_PROCESS_TEXT` entries; report it and skip entry 4.
- Depends on plan 08's `DraftStore`. If plan 08 is not merged, create `DraftStore` here exactly
  as specified in plan 08 §3.3.
