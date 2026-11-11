# Plan 05 — Device checklist (Checkpoint 0 and after)

**Status:** Checkpoint 0 is already closed. The README records all seven items passed on the
S26 Ultra on 2026-11-09. The brief asked for a runnable version of that checklist; it is built,
and its lasting use is as the **regression run after every executed plan** (`REVIEW_GATES.md`
Gate D).
**Sign-off needed:** none.

| Deliverable | Path |
|---|---|
| Runnable checklist | `scripts/checkpoint0.sh` |
| Owner guide and failure table | `docs/CHECKPOINT_0.md` |
| Result log (the script appends to it) | `docs/TEST_RESULTS.md` |

## What the script covers

| Step | Automatic or by eye |
|---|---|
| 1 Install with `adb install -r` | automatic |
| 2 Every permission the installed app requests has a sign-off entry in `docs/PERMISSIONS.md` | automatic |
| 3 Airplane mode on for the whole run | automatic, with a manual fallback |
| 4 Cold launch (time logged) | automatic |
| 5 Typed note saves | by eye |
| 6 Note survives a force stop | automatic stop, by eye |
| 7 Share-in: scripted share under 2 s; note visible; share from a real app | automatic timing, by eye |
| 8 Search finds the note | by eye |
| 9 Note survives a reboot; still offline afterwards | automatic reboot, by eye |
| 10 Database file exists (size logged) | automatic |

Every step is logged as PASS, FAIL, or SKIP with a detail, under a dated heading with the device
model and commit.

## Safety rules built in

- The script never uninstalls and never clears app data.
- A signature mismatch stops with an explanation instead of the usual "uninstall first" advice
  (audit B1). Uninstalling deletes the notes, photos, and the AI model.
- `--pull-export` copies private export files off a debug build with `adb run-as`.

## Known limits

- **Never run against a phone.** It was syntax-checked only (`bash -n`). The first run is also
  the script's own test. Likely trouble spots: the `dumpsys package` permission parsing in step 2
  (the section headings differ between Android versions), and `cmd connectivity airplane-mode`
  on One UI.
- Airplane mode blocks the one-time handwriting model download, by design. Do the first
  handwriting use outside this run.
- Six steps rely on the owner's eyes.
- Feature-specific device checks live in each plan ("Device check" sections of plans 07–13, 15,
  16). When a plan is executed, its checks are appended to `docs/CHECKPOINT_0.md` under a
  heading for that feature; they are not added to the script unless they can be automated.

## Done when

- [ ] One full run with every row PASS is committed in `docs/TEST_RESULTS.md`.
- [ ] Any fixes the first run needed are committed, with a line in `DECISIONS.md`.

## Follow-up for the faster model (only after the owner's first run)

Files allowed: `scripts/checkpoint0.sh`, `docs/CHECKPOINT_0.md`.
Apply the fixes the owner reports. Do not add steps that uninstall, clear data, grant or revoke
permissions, or turn airplane mode off without asking.
