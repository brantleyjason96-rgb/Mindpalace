# Device checklist (about 10 minutes)

Checkpoint 0 itself passed on the S26 Ultra on 2026-11-09 (see `README.md`). This is the same
checklist as a script, to be re-run after every change that reaches the phone. It proves the app
still installs over the existing copy, saves, survives a force stop and a reboot, takes shared
text, searches, and requests only signed-off permissions.

`scripts/checkpoint0.sh` runs the steps, asks you to confirm what only a person can see, and
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
what happened. Use `--skip-reboot` if you cannot reboot right now; the step is logged as SKIP.

## What it checks

| # | Step | Checked by | Pass means |
|---|---|---|---|
| 1 | Install | script | `adb install -r` reports Success. |
| 2 | Permissions signed off | script | Every permission the installed app requests has its own entry in `docs/PERMISSIONS.md`. |
| 3 | Airplane mode on | script (or you) | Airplane mode is on for the whole run. |
| 4 | Launch | script | The app starts from cold; start time is logged. |
| 5 | Typed note saves | you | The note appears in the Timeline. |
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
| Step 2 fails with "could not read the permission list" | The script could not parse this Android version's output. | Run `adb shell dumpsys package com.mindpalace.app` and report the "requested permissions" section. |
| Step 2 names a permission | The app requests something with no sign-off entry. | Do not ignore it. See `docs/plans/REVIEW_GATES.md` Gate C. |
| Step 10 fails with `run-as: package not debuggable` | A release build is installed. | Expected for release builds; record it and move on. |

### Signature mismatch — do not uninstall

Each computer signs debug builds with its own key. If the phone already has Mind Palace from
another computer (or from a CI download), Android refuses the update. The usual advice is
"uninstall first". **Uninstalling deletes every note, photo, drawing, attachment, and the AI model file**, because
backups are off by design (`DECISIONS.md` D4).

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
3. If any row is FAIL, do not merge the change that was being checked.

## Feature checks

Each executed plan adds its own short device checks here, under a heading for that feature
(see the "Device check" section of the plan).

### Reliable time reminders (plan 11 Part A, 2026-11-11)

1. Set a reminder a few minutes ahead; lock the phone → notification arrives with the note's
   text, posted exactly once. **PASS** (SM-S948U, 2026-11-11: set for 4 minutes out, fired while
   locked, correct text "checkpoint share verify", `numPostedByApp=1`).
2. Power the phone off with a reminder still pending; power on → notification arrives after boot
   without opening the app. **Not run this pass** — needs a live reboot with a pending reminder
   timed around it; do on the next device session that touches this path.
3. Turn off "Alarms & reminders" for Mind Palace in system settings; set a reminder → the app
   offers approximate timing; it fires within about 15 minutes. **Not run this pass** — needs the
   UI half (plan 11 Part A's picker/permission-recheck work), not yet built.
4. Schema v3 → v4 migration preserves existing data. **PASS** (SM-S948U, 2026-11-11: installed
   over the existing app, pre-existing note and its full revision history intact after upgrade).
5. Notification uses a real icon, not a blank square. **PASS** (SM-S948U, 2026-11-11: confirmed
   visually in the notification shade).
