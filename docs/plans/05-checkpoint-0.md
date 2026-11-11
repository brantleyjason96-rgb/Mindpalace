# Plan 05 — Close Checkpoint 0

**Status:** deliverables are committed. The remaining work is the owner's 10-minute device run.
**Sign-off needed:** none.

| Deliverable | Path |
|---|---|
| Runnable checklist | `scripts/checkpoint0.sh` |
| Owner guide, failure table | `docs/CHECKPOINT_0.md` |
| Result log (appended by the script) | `docs/TEST_RESULTS.md` |

## What the script covers

`adb install -r`, installed-permission check, airplane mode on for the whole run, cold launch,
typed note, force-stop persistence, share-in (scripted and from a real app, with a 2 s timing
check), search, reboot persistence, and a database-file check. Every step is logged as PASS, FAIL
or SKIP with a detail.

## Safety rules built in

- The script never uninstalls and never clears app data.
- A signature mismatch stops with an explanation instead of the usual "uninstall first" advice
  (`01-audit-punch-list.md` B1).
- `--pull-export` copies private export files off a debug build as an interim way off the phone.

## Known limits

- **Untested on a device.** It was syntax-checked only (`bash -n`). The first run is also the
  script's own test; fix what breaks and note it in `DECISIONS.md`.
- `cmd connectivity airplane-mode enable` may be refused on some One UI builds; the script then
  asks the owner to switch it by hand.
- The placeholder UI has no export button, so `--pull-export` finds nothing until plan 15 or a
  temporary debug button exists.
- Steps 5, 6, 7b, 7c, 8 and 9 rely on the owner's eyes.

## Done when

- [ ] One run with every row PASS is committed in `docs/TEST_RESULTS.md`.
- [ ] The README checklist boxes are ticked.
- [ ] `docs/CHANGELOG.md` has a "Checkpoint 0 passed on device" line with the date and commit.

## Follow-up for the faster model (only after the owner's first run)

Files allowed: `scripts/checkpoint0.sh`, `docs/CHECKPOINT_0.md`.
Apply the fixes the owner reports. Do not add steps that uninstall, clear data, or grant
permissions.
