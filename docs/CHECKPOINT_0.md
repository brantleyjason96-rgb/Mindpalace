# Checkpoint 0 — device checklist (about 10 minutes)

Checkpoint 0 proves the app works on the real phone: it installs, saves, survives a force stop
and a reboot, takes shared text, searches, and does all of it offline. It must pass before any
Phase 1 UI work starts.

`scripts/checkpoint0.sh` runs the checklist, asks you to confirm what only a person can see, and
appends a pass/fail table to `docs/TEST_RESULTS.md`.

> **Status of the script:** written on 2026-11-11 without a phone attached. It has been
> syntax-checked only. Expect to adjust it on the first real run and note what you changed.

## Before you start

1. Phone: Settings → Developer options → **USB debugging** on. Connect by USB and accept the
   prompt on the phone.
2. Mac: build the APK.
   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ```
   ```bash
   ./gradlew assembleDebug
   ```
3. Check that the Mac sees the phone (one line ending in `device`).
   ```bash
   adb devices
   ```

## Run it

```bash
scripts/checkpoint0.sh
```

Answer each question with `y` (yes), `n` (no), or `s` (skip). After an `n`, type one line saying
what happened. Use `--skip-reboot` if you cannot reboot right now; the step is logged as SKIP and
Checkpoint 0 is not complete until it passes.

## What it checks

| # | Step | Checked by | Pass means |
|---|---|---|---|
| 1 | Install | script | `adb install -r` reports Success. |
| 2 | No network permission | script | The installed app holds neither `INTERNET` nor `ACCESS_NETWORK_STATE`. |
| 3 | Airplane mode on | script (or you) | Airplane mode is on for the whole run. |
| 4 | Launch | script | The app starts from cold; start time is logged. |
| 5 | Typed note saves | you | "Saved (rev 1)" shows and the note is in the list. |
| 6 | Survives force stop | script + you | The note is still there after a force stop. |
| 7a | Share saves in under 2 s | script | A scripted share finishes in under 2000 ms. |
| 7b | Shared note visible | you | The scripted share appears in the list. |
| 7c | Share from a real app | you | Share → "Save to Mind Palace" shows the toast and the note. |
| 8 | Search finds a word | you | Searching the tag shows the matching notes. |
| 9 | Survives reboot | script + you | Both notes are there after a reboot. |
| 9b | Still offline after reboot | script | Airplane mode is still on. |
| 10 | Database file exists | script | `mind_palace.db` is on disk; size is logged. |

Step 7a times the scripted share only. It is a stand-in for "capture-to-saved under 2 s", not a
measurement of typing speed.

## If something fails

| Message | Meaning | What to do |
|---|---|---|
| `Expected exactly 1 connected device` | The Mac does not see the phone, or sees two. | Unlock the phone, re-plug, accept the debugging prompt. |
| `APK not found` | The app has not been built. | Run `./gradlew assembleDebug`. |
| `signature mismatch` | See below. | **Do not uninstall.** |
| Step 3 asks you to set airplane mode by hand | The phone refused the command. | Turn it on in Quick Settings, answer `y`. |
| Step 10 fails with `run-as: package not debuggable` | A release build is installed. | Expected for release builds; record it and move on. |

### Signature mismatch — do not uninstall

Each computer signs debug builds with its own key. If the phone already has Mind Palace from
another computer (or from a CI download), Android refuses the update. The usual advice is
"uninstall first". **Uninstalling deletes every note**, because backups are off by design
(`DECISIONS.md` D4).

Do this instead:

1. Rebuild and install from the computer that installed the app before, **or**
2. Save your notes first. On the computer that can still install, trigger an export in the app,
   then copy the files off the phone:
   ```bash
   scripts/checkpoint0.sh --pull-export
   ```
   The files land in `exports-pulled/` (ignored by git). They contain your notes in plain text.
   Only after you have opened one and seen your notes should you uninstall. Until import exists
   (`docs/plans/15-export-import.md`), the export is a readable copy, not a restore.

## After the run

1. Open `docs/TEST_RESULTS.md` and read the new section at the bottom.
2. Commit it.
   ```bash
   git add docs/TEST_RESULTS.md
   ```
   ```bash
   git commit -m "Checkpoint 0 device run"
   ```
3. If every row is PASS, tick the boxes in `README.md` and add a line to `docs/CHANGELOG.md`.
   Checkpoint 0 is then closed.
