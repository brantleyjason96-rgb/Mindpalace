# Plan 09 — Camera, OCR, handwriting, and attachments: hardening

**State today:** `PhotoCaptureScreen` (3899–4107) uses the system camera app and the photo
picker, so no camera permission is needed (D17). OCR is ML Kit's bundled Latin-script model
(`OcrEngine`). The image goes to `filesDir/photos`, the text to `ocr_text`.
`HandwritingCaptureScreen` (4131–4438) uses ML Kit Digital Ink, whose model must be downloaded;
that is why `INTERNET` exists (D26). Attachments are copied to `filesDir/attachments` (D25).
**This plan:** stop losing captures, clean up files, make OCR testable, and record the permission
position. It also covers handwriting and attachments because they share the same file handling.
**Sign-off needed:** none for §3–§5. §6 lists decisions that would need one.
**Schema change:** none (`image_path`, `ocr_text` already exist).

## 1. Permission decision (as built, restated)

| Need | How it is met | Permission |
|---|---|---|
| Take a photo | `MediaStore.ACTION_IMAGE_CAPTURE` → system camera app writes to a FileProvider URI | none. **Never declare `CAMERA`:** once declared but not granted, the capture intent throws `SecurityException`. |
| Pick a photo | `PickVisualMedia` (system photo picker) | none |
| Pick a file | `OpenDocument` | none |
| OCR | bundled model in the APK | none |
| Handwriting model | one download per language | `INTERNET`, `ACCESS_NETWORK_STATE` (D26) |

An in-app camera (CameraX 1.6.2) would need `CAMERA` and a sign-off. Not recommended: the system
camera does the job and costs no permission.

## 2. Problems

| # | Problem | Where |
|---|---|---|
| 1 | The photo is not saved as a note until the owner taps Save after OCR. If OCR fails, hangs, or the app is killed, the photo is an orphan file and no note exists. This is not save-first. | 3915–3944, 4089 |
| 2 | `photoFile` and `pendingCameraFile` are plain `remember`. Rotation loses the photo; if Android recreates the activity while the camera is open, the result is dropped. | 3906–3907, 3921 |
| 3 | Handwriting strokes are plain `remember`; rotation, font-size change, or a split-screen resize erases the drawing. | 4137–4146 |
| 4 | Files are written before save and never deleted when the owner backs out. A picked file that cannot be read is saved as an empty file. | 1079, 3931, 3944, 4350 |
| 5 | Full-size bitmaps are decoded on the main thread; a 200 MP photo can freeze or crash the app. | 4056, 4378 |
| 6 | `OcrEngine` never closes its recognizer and cannot be faked in tests. | `OcrEngine.kt` |
| 7 | OCR reads Latin script only. Arabic, Chinese, Devanagari, Japanese, and Korean text returns nothing useful. | `TextRecognizerOptions.DEFAULT_OPTIONS` |
| 8 | Handwriting accepts the stylus only; a finger does nothing and nothing says why. | 4228 |
| 9 | Handwriting model download has no progress, no "needs a connection" message, and shows the raw exception text. | 4346–4358 |
| 10 | Soft-deleted notes keep their image and attachment files forever; nothing ever removes a file. | — |
| 11 | "Saved" never shows; `saving` is not reset on failure. | 4089–4092, 4419–4422 |

## 3. Design

### 3.1 Save first, recognize after
Change the order for photos:

1. Camera or picker returns → copy/confirm the file in `photosDir` →
   **`commands.create(PHOTO, imagePath)` immediately**. `CreateNoteRequest.hasCaptureContent()`
   counts `attachmentPath` today but not `imagePath`; add `imagePath` so a photo alone can save.
2. The review screen opens **on the saved note**, runs OCR, and shows the text in an editable
   field.
3. "Save text" calls a new command `attachOcrText(noteId, text, expectedRevision)`; "Skip"
   leaves `ocr_text` empty; "Discard photo" soft-deletes the note (undoable).
4. If OCR fails, the note and photo are already safe; the screen says "Could not read text —
   type it or try again".

New handler command:
```kotlin
suspend fun attachOcrText(noteId: String, text: String, expectedRevision: Int? = null,
                          actor: AuditActor = AuditActor.USER)
```
Audited as `AuditAction.SET_OCR_TEXT`, revision-guarded, re-applies rules (plan 10 §3.4).
The first fill and later edits are both user actions on this screen, so actor is `USER`.
Rule 4 is respected: the text becomes "original" at the first `attachOcrText`; changes after
that are audited edits like any other.

Handwriting follows the same order: render the PNG → `create(INK, imagePath)` → recognize →
`attachOcrText`.

### 3.2 State that survives rotation and process death
- **Photo:** the only state is the note id (after 3.1). Keep it in `rememberSaveable`. The
  pending camera file path also goes in `rememberSaveable` (a `String`), so a recreated activity
  can still accept the camera result.
- **Handwriting:** move strokes into a `HandwritingViewModel` (survives rotation). For process
  death, save strokes to `filesDir/drafts/ink.json` on `onStop` through `DraftStore` (plan 08
  §3.3) as a list of `{color, width, alpha, points:[x,y,t]}`; restore on open. Cap the draft at
  5 MB; beyond that keep the newest strokes and say so.
- Appending a point must not copy the whole stroke list: keep the in-progress stroke in a
  mutable buffer and publish it to state at most once per frame.

### 3.3 File hygiene (new `capture/CaptureFiles.kt`)
```kotlin
class CaptureFiles(val photosDir: File, val attachmentsDir: File) {
    /** Copies to <uuid>.part then renames. Returns null and leaves nothing behind on failure or 0 bytes. */
    suspend fun import(input: InputStream?, dir: File, extension: String): File?
    /** Files in both dirs that no notes row references and that are older than 24 hours. */
    suspend fun findOrphans(referenced: Set<String>, now: Long): List<File>
    suspend fun deleteOrphans(referenced: Set<String>, now: Long): Int
}
```
- All three copy sites (attachment 1071–1089, gallery 3929–3939, camera output) use `import`.
  A `null` result shows "Could not read that file" and saves nothing.
- `deleteOrphans` runs once per app start on a background dispatcher. `referenced` comes from a
  new query `SELECT image_path FROM notes WHERE image_path IS NOT NULL UNION SELECT
  attachment_path …` — **including soft-deleted notes**, so Trash can still restore them.
- Files of soft-deleted notes are kept. Removing them is part of "empty Trash", which does not
  exist and is not in this plan.

### 3.4 Images
New `ui/ImageLoading.kt`: `suspend fun loadSampled(path: String, maxPx: Int): ImageBitmap?`
— reads bounds first, picks `inSampleSize`, decodes on `Dispatchers.IO`, returns `null` on
failure. Both previews use it with `maxPx = 1600`. Give each preview a `contentDescription`
("Photo attached to this note").

### 3.5 OCR seam
```kotlin
interface TextRecognizerEngine { suspend fun recognize(context: Context, imageUri: Uri): String }
```
`OcrEngine` implements it, creates one recognizer lazily, and closes it in a `close()` called
from `Application.onTerminate`-equivalent cleanup (or keeps it for the process lifetime; either
is fine, but not one per call). `AppContainer.ocr: TextRecognizerEngine`. Tests use a fake.

### 3.6 Handwriting usability
- Accept finger input when no stylus has been seen in this session; once a stylus touches,
  ignore fingers (palm rejection). Add a toggle "Draw with finger".
- Model download: show "Downloading handwriting model (one time, needs a connection)…", and on
  failure a string resource that says to connect once and try again. Never show a raw exception.
- Label the colour swatches and width buttons for TalkBack and raise them to 48 dp (plan 16).

## 4. Tests

`NoteCommandHandlerTest`:

| ID | Test name | Assert |
|---|---|---|
| T-P1 | `a photo with no text is a valid capture` | `create(PHOTO, imagePath = "photos/a.jpg")` → saved, `ocrText == null`. |
| T-P2 | `attachOcrText fills the text, bumps the revision, and is audited` | action `SET_OCR_TEXT`; revision 2; FTS finds a word from the text. |
| T-P3 | `attachOcrText with a stale revision is rejected` | `NoteConflictException`. |
| T-P4 | `attachOcrText is undoable` | undo → `ocrText == null`. |
| T-P5 | `rules run when OCR text arrives` | rule "invoice" → category appears after `attachOcrText("Invoice 42")`. |

`CaptureFilesTest` (plain JUnit, temp dirs):

| ID | Test name | Assert |
|---|---|---|
| T-P6 | `import copies bytes and leaves no part file` | content equal; no `.part`. |
| T-P7 | `import of a null or empty stream returns null and creates nothing` | directory empty. |
| T-P8 | `import that fails midway leaves nothing behind` | stream throws after 10 bytes → directory empty. |
| T-P9 | `orphans are unreferenced files older than a day` | referenced file kept; fresh unreferenced kept; old unreferenced listed. |
| T-P10 | `files of soft-deleted notes are not orphans` | Robolectric: note deleted → its path is in the referenced set. |

`PhotoCaptureFlowTest` (Robolectric, fake `TextRecognizerEngine`, tests the ViewModel functions,
not the composable):

| ID | Test name | Assert |
|---|---|---|
| T-P11 | `the note exists before OCR finishes` | fake OCR suspends → `countActive() == 1` already. |
| T-P12 | `an OCR failure keeps the note and the photo` | fake throws → note exists, file exists, UI state is `OcrFailed`. |
| T-P13 | `discarding soft-deletes the note` | `deletedAt != null`; file still on disk. |

`InkDraftTest` (plain JUnit): T-P14 strokes → JSON → strokes equal; T-P15 a corrupt draft file
yields an empty drawing, no exception.

## 5. Device check (owner) — add to `docs/CHECKPOINT_0.md`

1. Take a photo of a worksheet → rotate the phone on the review screen → photo and text remain.
2. Take a photo, then swipe the app away before saving text → reopen → the photo note is in the
   Timeline.
3. Pick a 200 MP photo from the gallery → no freeze.
4. Draw with the S Pen → rotate → drawing remains. Draw with a finger with the toggle on.
5. Airplane mode, first-ever handwriting use → clear "needs a connection once" message.

## 6. Not in this plan (each needs a decision first)

| Item | Why it waits |
|---|---|
| Non-Latin OCR (Arabic is not offered by ML Kit's bundled models; Chinese, Devanagari, Japanese, Korean are separate bundled libraries, a few MB each) | APK size per script; which scripts the owner needs. Tesseract4Android (Apache-2.0, no permissions) covers Arabic at 4–15 MB per language. Owner decision OD-9. |
| Sharing an image **into** the app (`ACTION_SEND image/*`) | Small manifest change, no permission; do after plan 07 step 2. |
| Replacing ML Kit Digital Ink so `INTERNET` can be removed | See OD-4 in `INDEX.md`. |
| In-app camera | Needs `CAMERA` sign-off; not recommended. |
| Emptying Trash and deleting files for good | Hard delete conflicts with "soft delete only" (PLAN.md §3.2); needs its own decision. |

## 7. Files allowed

`MainActivity.kt` (`PhotoCaptureScreen`, `HandwritingCaptureScreen`, the attachment picker block
in `CaptureTab` 1069–1090, `capturePhoto`/`captureInk`/`captureAttachment` in `MainViewModel`),
`core/ocr/OcrEngine.kt`, `core/ink/InkRecognitionEngine.kt`, `core/commands/NoteCommandHandler.kt`
(`attachOcrText`), `core/commands/NoteCommands.kt` (`hasCaptureContent`), `core/model/Model.kt` (`SET_OCR_TEXT`), `core/db/Daos.kt` (one query), new
`capture/CaptureFiles.kt`, new `ui/ImageLoading.kt`, `MindPalaceApplication.kt`, `strings.xml`,
tests. **Not** the manifest.

## 8. Done when

- Tests pass in CI; the permission list is unchanged; `app/schemas` unchanged.
- The five device checks are logged as PASS.
- `DECISIONS.md`: save-before-OCR order, orphan cleanup rule, finger input. `CHANGELOG.md`.

## 9. Stop and report if

- Saving before OCR needs a `uses-permission` or a schema change.
- The system camera returns no file on the owner's phone when the activity is recreated.
