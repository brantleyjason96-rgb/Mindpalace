# Plan 16 — Accessibility and multilingual pass

**State today:** nearly all UI text is in `strings.xml` (225 strings, 3 plurals), paddings use
start/end, state collection is lifecycle-aware, and the theme has light and dark. `supportsRtl`
is on. There is one language (English) and no accessibility testing.
**Goal:** the app is usable with TalkBack, at 200% font size, and in a right-to-left language;
and it is ready to be translated.
**Sign-off needed:** none. **Schema change:** none. **Depends on:** plan 07 step 1 (files split),
so line numbers below are from today's `MainActivity.kt` and names are what to search for.

## 1. Findings

### 1.1 Blocking for TalkBack and keyboard users

| # | Problem | Where |
|---|---|---|
| A1 | A note card expands through a raw `pointerInput` tap with no click semantics. "Open note" appears only after expanding. A TalkBack or keyboard user most likely cannot open a note from any list. | `SwipeableNote` 1624–1626, `NoteCard` 1812 |
| A2 | Swipe to delete and swipe to advance status have no accessibility actions. The only alternative is inside note detail, which A1 blocks. | 1627–1668 |
| A3 | Handwriting colour swatches have no label, and the selected one is shown by border only. Stroke-width buttons have no label. | 4290–4325 |
| A4 | The handwriting canvas accepts a stylus only; nothing tells the user. A drawing surface has no non-visual equivalent. | 4228 |
| A5 | Snackbars with an action never time out and have no dismiss action. | 827–833, 1059–1065 |

### 1.2 Touch targets under 48 dp
`ToolbarIconButton` 36 dp (2019); colour swatches 32 dp (4295); width buttons 28 dp (4318).

### 1.3 Font scaling (200%)
No fixed text sizes in screens (`Theme.kt` 101 sets one 11 sp label style). Rows that will
squeeze or overflow: header 1390–1437, reminder row 2524–2549, vocabulary row 3619–3633 (no
`weight`), export buttons 2764–2779. Single-line truncation at 3143 and 3150 hides proposal text.

### 1.4 Right to left
Paddings are start/end. Not mirrored: swipe direction and the side its label sits on (1684);
the slide direction of screen transitions; hand-drawn `Canvas` icons with a direction (check
each in `CaptureIcons.kt`, `TabIcons.kt`).

### 1.5 Text that is not translatable or not locale-aware

| Text | Where | Fix |
|---|---|---|
| Enum name shown to the user | 3125 (`ProposalKind`) | string resource per constant |
| Raw `run.outcome` | 3015 | map outcomes to strings (plan 12 §6) |
| Raw ISO reminder time | 2529, 4492 | formatted date (plan 11 A2) |
| Date pattern `"EEE, MMM d, h:mm a"` | 2501, 4448 | `DateFormat.getBestDateTimePattern(locale, "EEEMMMdjmm")` (respects 24-hour setting) |
| Raw exception message | 4357 | string resource |
| `"user link"` stored in the database | 382 | store a stable key (`"user"`); show a string resource |
| `"attachment"`, `"?"`, curly quotes, bare counts | 1077, 2710, 3147, 1269, 1357 | resources; counts through `NumberFormat` |
| Default category names (`DEFAULT_CATEGORIES`) are English rows in the database | `Model.kt`, `MIGRATION_1_2` | leave as data; they are the owner's labels and are editable. Do **not** translate stored values. |

### 1.6 Recognition languages (content, not interface)
The owner's notes may be multilingual. Today: OCR is Latin script only; handwriting is `en-US`
only; speech uses the system language; search folds diacritics (`café` = `cafe`) but cannot find
a substring inside unspaced scripts such as Chinese or Japanese. These are capability limits, not
bugs; §5 lists them as decisions.

## 2. Standards to meet

- Every interactive element: a role, a label, and a 48 × 48 dp target.
- Every gesture has a visible or accessibility-action alternative.
- No information carried by colour or border alone.
- Text contrast at least 4.5:1 (3:1 for large text) in both themes.
- At 200% font scale no text is clipped and every action is reachable (scrolling is fine).
- In RTL, layout, swipe meaning, and transitions mirror; numbers and note text keep their own
  direction.
- No user-visible literal in Kotlin code.

## 3. Changes

### 3.1 Note cards (A1, A2)
On `SwipeableNote`/`NoteCard`:
```kotlin
Modifier
  .clickable(onClickLabel = stringResource(R.string.a11y_open_note)) { onOpen(note.id) }   // tap opens
  .semantics(mergeDescendants = true) {
      contentDescription = cardDescription          // "Title. Preview. Status Inbox. Category Classroom. Voice note."
      customActions = listOfNotNull(
          advanceLabel?.let { CustomAccessibilityAction(it) { onAdvance(); true } },
          CustomAccessibilityAction(deleteLabel) { onDelete(); true },
          CustomAccessibilityAction(expandLabel) { onToggleExpand(); true },
      )
  }
```
Tap opens the note (the common action). Expand moves to a visible chevron `IconButton` (48 dp)
on the card. The swipe gestures stay for touch users. Build `cardDescription` in a pure function
`noteCardDescription(item, strings)` so it can be unit-tested.

### 3.2 Handwriting (A3, A4)
- Swatches and width buttons: `Modifier.selectable(selected, role = Role.RadioButton)`, a
  `contentDescription` naming the colour or width ("Blue ink", "Thick line"), 48 dp, and a check
  mark on the selected one.
- Canvas: `contentDescription` "Handwriting area. Drawing needs touch or a pen." Finger input
  toggle (plan 09 §3.6). Add a visible "Type instead" button that opens the text composer.

### 3.3 Snackbars (A5)
Through the `UndoController` of plan 07: `SnackbarDuration.Long`, `withDismissAction = true`.
For accessibility services the system extends the time automatically; do not use `Indefinite`.

### 3.4 Targets, roles, headings, live regions
- Raise the three small controls to 48 dp (`Modifier.minimumInteractiveComponentSize()` or an
  explicit size).
- Section titles get `Modifier.semantics { heading() }` (Capture, Timeline, each Settings card,
  each note-detail section).
- Status and source chips: decorative inside a merged card; standalone chips get
  `Role.Button` or `Role.Tab` and `selected` state (`StatusFilterRow`, `StatusPickerRow`).
- "Saved", "Recovered draft", recognition status, and run progress use
  `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`.
- Tab bar items: `Role.Tab` with `selected`.
- Preview images: a real `contentDescription` (plan 09 §3.4).
- Canvas icons that stand alone as buttons already get a description from `CircleIconButton`;
  verify each call site passes one.

### 3.5 Font scaling
- Replace fixed-width rows with `FlowRow`, or stack label over actions when
  `LocalDensity.current.fontScale > 1.3f` (helper `isLargeFont()` in `ui/common`).
- Give text in rows `Modifier.weight(1f)` and `overflow = Ellipsis` with at least 2 lines; remove
  `maxLines = 1` on proposal value and evidence (3143, 3150).
- Fixed-height boxes (3824, 4066, 4212, 4386) hold images or canvases, not text: leave, but make
  sure the screen scrolls.
- Change the 11 sp label style in `Theme.kt` to 12 sp minimum.

### 3.6 RTL
- Swipe: compute direction with `LocalLayoutDirection`; "towards the end edge" advances,
  "towards the start edge" deletes; background label alignment uses `Alignment.CenterStart/End`.
- Screen transitions: multiply the slide offset by `if (rtl) -1 else 1`.
- Directional Canvas icons: wrap in `Modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f)`
  only where the glyph has a direction (back arrows, send); not for mic or camera.
- Note text fields: set `textDirection = TextDirection.Content` so Arabic or Hebrew notes render
  right-to-left even in an English interface, and English notes left-to-right in an RTL one.

### 3.7 Getting ready for translation
- Move the literals in §1.5 to resources. Use `plurals` for every count.
- Add `res/xml/locales_config.xml` listing `en` and reference it with
  `android:localeConfig` in the manifest (no permission; enables the per-app language setting).
  Add a language only when a full `values-<lang>/strings.xml` exists.
- Add `values-en-rXA` / `values-ar-rXB` **pseudolocales** for debug builds
  (`android { buildTypes { debug { isPseudoLocalesEnabled = true } } }`): `en-XA` lengthens every
  string by about a third and adds accents; `ar-XB` forces RTL. Both are for testing only.
- Which real languages to translate into is owner decision OD-12. Spanish is the likely first
  one; no translation is part of this plan.

## 4. Tests

Plain JUnit:

| ID | Test name | Assert |
|---|---|---|
| T-A1 | `note card description includes title, preview, status, category and source` | for a sample item, in that order; no "null". |
| T-A2 | `note card description falls back when there is no title` | starts with the preview. |
| T-A3 | `no user-visible string literals in ui code` | Source scan of `ui/**.kt`: no `Text("…")` or `contentDescription = "…"` with a literal containing a letter, outside `@Preview` functions and `PreviewData.kt`. Allow-list by exact line where truly needed. |
| T-A4 | `every string has no trailing whitespace and every plural has "other"` | parse `strings.xml`. |
| T-A5 | `date format follows the locale's hour cycle` | pattern for `Locale.US` contains `a`; for `Locale.GERMANY` it does not. |

Robolectric + Compose UI test (`createComposeRule`; needs `androidx.compose.ui:ui-test-junit4`
and `ui-test-manifest` as **test-only** dependencies from the Compose BOM — ask the owner first,
per `CLAUDE.md`; if refused, keep T-A1…T-A5 and rely on §5):

| ID | Test name | Assert |
|---|---|---|
| T-A6 | `a note card exposes click, delete and advance actions` | semantics node has `OnClick` and two `CustomActions` with the expected labels. |
| T-A7 | `every clickable node on each screen is at least 48 dp and has a label or text` | walk the semantics tree of each stateless `XContent` with typical preview data. |
| T-A8 | `screens render at font scale 2 without clipped text nodes` | `Density(fontScale = 2f)`; assert no node reports `hasVisualOverflow` on key labels; screen is scrollable. |
| T-A9 | `swipe towards the end edge advances in both directions` | `LayoutDirection.Rtl` and `Ltr`. |
| T-A10 | `colour swatches report selected state and a name` | exactly one selected. |

## 5. Manual check (owner or reviewer, 20 minutes) — add to `docs/CHECKPOINT_0.md`

| # | Setup | Do | Pass when |
|---|---|---|---|
| 1 | TalkBack on | Capture a note, find it in Timeline, open it, change status, delete it, undo | Every step is possible by swipe-and-double-tap; nothing unlabelled is read as "button". |
| 2 | Font size and display size at maximum | Visit every tab and Settings sub-screen | No clipped text; every button reachable. |
| 3 | Developer options → Force RTL layout | Swipe a card both ways; open and close screens | Swipe meaning and animation follow the mirrored layout. |
| 4 | Debug build, app language `en-XA` | Visit every screen | No English-only (un-accented) text remains; nothing truncated. |
| 5 | Dark theme | Read chips, hints, disabled buttons | All readable. Check contrast of the accent colours with a contrast checker if in doubt. |
| 6 | Accessibility Scanner app (Google, free) | Scan each main screen | No "touch target" or "item label" findings left. |

## 6. Decisions that are not part of this plan

| ID | Question |
|---|---|
| OD-12 | Which interface languages to translate into, and who checks the translation. |
| OD-9 | Which scripts OCR should read (plan 09 §6). |
| — | Handwriting languages beyond `en-US`: each needs its own model download (uses the D26 network exception again; that is the same purpose, so no new sign-off, but tell the owner). |
| — | Search inside unspaced scripts needs a different FTS tokenizer (`trigram`), which is a schema change and a re-index. Only if the owner writes notes in such a script. |

## 7. Files allowed

Everything under `ui/` (or `MainActivity.kt` before the split), `strings.xml`, new
`res/xml/locales_config.xml`, `AndroidManifest.xml` (**only** the `android:localeConfig`
attribute), `app/build.gradle.kts` (**only** `isPseudoLocalesEnabled` and, if approved, the two
test dependencies), `ui/theme/Theme.kt` (the 11 sp style), `MainViewModel.addLink` (the stored
`"user link"` literal), tests.

## 8. Done when

- T-A1…T-A5 pass in CI (and T-A6…T-A10 if the test dependencies were approved).
- The six manual checks are logged as PASS in `docs/TEST_RESULTS.md`.
- `DECISIONS.md`: tap-opens-note change, RTL swipe rule, pseudolocales.

## 9. Stop and report if

- Making the card clickable breaks the swipe gesture on the phone.
- A fix seems to need a new library other than the two Compose test artifacts.
