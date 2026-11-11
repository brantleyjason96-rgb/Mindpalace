# Plan 08 — Voice capture, hardening

**State today (D15):** `VoiceCaptureScreen` (`MainActivity.kt` 3650–3891) uses Android's
`SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`. `RECORD_AUDIO` is signed off. The transcript is
saved as a `VOICE` note in the `transcript` column. No audio is recorded or stored.
**The brief asked for:** on-device speech-to-text only, a transcript field, and "save audio or
text first, never lose input" when recognition fails.
**Gap:** the third point is not met and conflicts with D15's "no audio ever written to disk".
That conflict is an owner decision (OD-5), not something to build around quietly.
**Sign-off needed:** OD-5. No new permission in any option.
**Schema change:** only if OD-5 = option B (v7: `notes.audio_path`).

## 1. Problems

| # | Problem | Where |
|---|---|---|
| 1 | If recognition fails or returns nothing, what the owner said is gone. | whole screen |
| 2 | Partial results are thrown away on error; only a final result becomes the transcript. | 3702–3706 |
| 3 | The transcript is shown as read-only text. "Review before save" cannot fix a wrong word. | 3859 |
| 4 | After one denial the screen shows text only: no retry, no link to settings. | 3771–3775 |
| 5 | Every recognizer error shows one generic message. | 3702–3706 |
| 6 | `EXTRA_PREFER_OFFLINE` is a preference, not a guarantee. With `INTERNET` now granted (D26) the system recognizer may use the network. | recognizer setup |
| 7 | No check that an offline language pack is installed. | — |
| 8 | The "Saved" snackbar never shows; `saving` is not reset on failure. | 3873–3876 |
| 9 | One long dictation ends at the recognizer's first silence timeout. | — |

## 2. Owner decision OD-5 — what "never lose input" means for voice

| Option | What is kept when recognition fails | Cost |
|---|---|---|
| **A. Text-first (recommended)** | Every partial result is written to a draft as it arrives. If recognition fails, the owner keeps everything recognized so far, editable, and can add to it by typing or dictating again. No audio is stored. | Words the recognizer never produced are still lost. Keeps D15 intact. No schema change. |
| B. Audio fallback | Option A, plus the audio of a failed session is saved as a file attached to the note, so it can be replayed. | Overturns D15's "no audio on disk". Needs a second audio path: `SpeechRecognizer` does not hand over the audio reliably, so the app must record with `AudioRecord` and feed the recognizer itself (API 33+ `EXTRA_AUDIO_SOURCE`), which is more code and more to test. Adds `notes.audio_path` (v7) and audio files to export. |
| C. Bundled recognizer (Vosk or Whisper) | Same as A or B, with the app's own model. | 40–120 MB more in the app; another native library. Only worth it if the phone's recognizer proves unusable offline. |

The steps below build option A. Section 6 lists what option B adds.

## 3. Design (option A)

### 3.1 Strictly on-device recognizer
Replace `SpeechRecognizer.createSpeechRecognizer` with:
```kotlin
if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
else null   // show "On-device speech recognition is not available on this phone"
```
`createOnDeviceSpeechRecognizer` (API 31+, the app's minimum is 33) never uses a network
recognizer. Do **not** fall back to the network recognizer. Keep `EXTRA_PREFER_OFFLINE = true`.
Before listening, call `checkRecognitionSupport` (API 33) for the device language; if the
language is listed under "installable", show "The offline language pack for <language> is not
installed" with a button that calls `triggerModelDownload`. The download is done by the system
service, not by this app.
**Not verified:** which on-device recognition service One UI on the S26 Ultra provides. The
device check in §5 answers it.

### 3.2 Pure session state (new `capture/VoiceSession.kt`, no Android types)
```kotlin
data class VoiceSession(
    val committed: String = "",      // text from finished segments, plus the owner's edits
    val partial: String = "",        // current segment, not yet final
    val status: Status = Status.IDLE,
    val error: VoiceError? = null,
) {
    enum class Status { IDLE, LISTENING, PAUSED, UNAVAILABLE }
    val draftText: String get() = listOf(committed, partial).filter { it.isNotBlank() }.joinToString(" ")
    fun onPartial(text: String): VoiceSession
    fun onFinal(text: String): VoiceSession          // committed += text; partial = ""
    fun onError(code: Int): VoiceSession             // committed += partial (never dropped); partial = ""
    fun onEdited(text: String): VoiceSession         // owner typed in the field
}
enum class VoiceError { NO_SPEECH, NO_MATCH, AUDIO, BUSY, LANGUAGE_MISSING, PERMISSION, OTHER }
```
`onError` maps `SpeechRecognizer.ERROR_*` codes: `ERROR_SPEECH_TIMEOUT` → `NO_SPEECH`,
`ERROR_NO_MATCH` → `NO_MATCH`, `ERROR_AUDIO` → `AUDIO`, `ERROR_RECOGNIZER_BUSY` → `BUSY`,
`ERROR_LANGUAGE_NOT_SUPPORTED` / `ERROR_LANGUAGE_UNAVAILABLE` → `LANGUAGE_MISSING`,
`ERROR_INSUFFICIENT_PERMISSIONS` → `PERMISSION`, everything else → `OTHER`. Each has its own
string resource saying what to do next.

### 3.3 Draft that survives the process
New `capture/DraftStore.kt`: one small JSON file per capture mode in `filesDir/drafts/`
(`voice.json`, and `text.json` for the composer in plan 07).
```kotlin
class DraftStore(dir: File) {
    fun read(key: String): String?
    fun write(key: String, text: String)   // write to key.tmp, then rename
    fun clear(key: String)
}
```
The screen writes `draftText` on every `onPartial`/`onFinal`/`onEdited` (debounced 300 ms, and
immediately in `onStop`). On open, a non-empty draft is restored with a "Recovered unsaved
dictation" line. The draft is cleared only after `commands.create` returns.

### 3.4 Screen changes (`VoiceCaptureScreen`)
1. The transcript is an editable `OutlinedTextField` bound to `committed`; the live partial text
   is shown under it in a lighter style.
2. Mic button toggles LISTENING ↔ PAUSED. A segment ending (final result or timeout) returns to
   PAUSED with the text kept; tapping the mic adds another segment to the same draft.
3. Save is enabled whenever `draftText` is not blank, **including after an error**.
4. Back with a non-empty draft asks: Save / Keep draft / Discard.
5. Permission denied: show the explainer with "Try again" (re-request) and, once the system stops
   asking (`shouldShowRequestPermissionRationale` false after a denial), "Open settings"
   (`ACTION_APPLICATION_DETAILS_SETTINGS`). The owner can always type into the field instead.
6. Save goes through `launchCommand` (plan 01 X1); on failure the text stays and an error shows;
   `saving` resets. Show "Saved" from the screen the app returns to.
7. `MainViewModel.captureVoice` is unchanged except for error handling.

Voice search in the Timeline (FEATURE_GAP_PLAN phase 4) reuses `VoiceSession` and the on-device
recognizer and writes into the search box. Build it only after this plan is verified on the
phone.

## 4. Tests

`VoiceSessionTest` (plain JUnit):

| ID | Test name | Assert |
|---|---|---|
| T-V1 | `partials replace each other and finals accumulate` | partial "he", partial "hello", final "hello" , final "world" → `committed == "hello world"`. |
| T-V2 | `an error keeps the partial text` | partial "buy oat" then `onError(ERROR_NETWORK)` → `committed == "buy oat"`, `partial == ""`. |
| T-V3 | `an error with nothing said leaves the draft empty and sets the error` | `NO_SPEECH`. |
| T-V4 | `error codes map to distinct errors` | one assertion per code in §3.2. |
| T-V5 | `edits replace committed text and later finals append` | `onEdited("Buy oat milk")`, final "tomorrow" → "Buy oat milk tomorrow". |
| T-V6 | `draftText joins committed and partial with one space` | no leading or double spaces. |

`DraftStoreTest` (plain JUnit, temp dir): T-V7 write then read; T-V8 clear removes the file;
T-V9 a leftover `.tmp` file is ignored; T-V10 unicode round trip.

`NoteCommandHandlerTest`: T-V11 `voice note stores the transcript verbatim` (leading and
trailing spaces kept).

No Robolectric test drives the real recognizer; it has no usable shadow. The device check covers it.

## 5. Device check (owner) — add to `docs/CHECKPOINT_0.md`

1. Airplane mode on. Dictate one sentence → text appears; save → note exists. (Proves on-device
   recognition and names the service: note the result in `DECISIONS.md`.)
2. Dictate, stay silent until it stops, tap the mic, dictate more → both parts are in one note.
3. Dictate half a sentence, then swipe the app away. Reopen Voice → the half sentence is offered
   back.
4. Deny the microphone twice → "Open settings" appears and works.

## 6. If OD-5 = option B (audio fallback), add

- **Sign-off record:** update D15 and `docs/PERMISSIONS.md` (`RECORD_AUDIO` scope changes from
  "never stored" to "stored only when recognition fails").
- Schema v7: `ALTER TABLE notes ADD COLUMN audio_path TEXT`; `CreateNoteRequest.audioPath`;
  `hasCaptureContent()` counts it; `NoteSnapshot.audioPath`.
- Record with `AudioRecord` (16 kHz mono PCM) into `filesDir/audio/<uuid>.wav` and feed the
  recognizer through `RecognizerIntent.EXTRA_AUDIO_SOURCE` (API 33). Delete the file when
  recognition succeeds and the note is saved; keep it when the session ends in an error.
- Note detail: a play button for notes with `audio_path`. Export includes the file (plan 15).
- Stop and report if the phone's on-device recognizer ignores `EXTRA_AUDIO_SOURCE`.

## 7. Files allowed (option A)

`MainActivity.kt` (`VoiceCaptureScreen` only, plus error handling in `captureVoice`), new
`capture/VoiceSession.kt`, new `capture/DraftStore.kt`, `MindPalaceApplication.kt` (expose
`draftStore`), `strings.xml`, `AndroidManifest.xml` (**only** a `<queries>` entry for
`android.speech.RecognitionService` if the recognizer cannot be found without it; no permission),
tests.

## 8. Done when

- Tests pass in CI; the permission list is unchanged.
- The four device checks are logged as PASS.
- `DECISIONS.md`: OD-5 answer, on-device-only recognizer, draft file. `CHANGELOG.md` updated.

## 9. Stop and report if

- `isOnDeviceRecognitionAvailable` is false on the owner's phone. Do not switch to the network
  recognizer; report, and OD-5 option C becomes the question.
