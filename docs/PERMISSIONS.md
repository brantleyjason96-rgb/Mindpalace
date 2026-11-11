# Permissions

Every entry here required the owner's explicit sign-off before the manifest changed (CLAUDE.md
rule 2). `PrivacyManifestTest` is updated deliberately in the same commit as any addition.

## RECORD_AUDIO
- **Added:** 2026-11-09, D15.
- **Why:** Voice capture — Android's on-device `SpeechRecognizer` (offline) needs the mic to
  produce a transcript.
- **Scope:** Used only while the Voice capture screen is in the foreground and actively listening.
  No raw audio is ever written to disk; only the resulting transcript text is saved, as a note.
- **Sign-off:** owner, 2026-11-09 (in-session).

## POST_NOTIFICATIONS
- **Added:** 2026-11-09, D19.
- **Why:** Reminders need to show a notification when a reminder's time arrives.
- **Scope:** Only ever posts a notification for a reminder the user created themselves.

## SCHEDULE_EXACT_ALARM
- **Added:** 2026-11-09, D19.
- **Why:** A reminder set for a specific time should fire at that time, not "sometime around
  then" (the inexact-alarm default can drift by many minutes).
- **Scope:** Only used to schedule reminders the user created; granted via a system Settings page
  (not a runtime dialog) — the app checks `canScheduleExactAlarms()` and sends the user there.

## RECEIVE_BOOT_COMPLETED
- **Added:** 2026-11-09, D19.
- **Why:** Android clears all `AlarmManager` alarms on reboot. Without this, every reminder set
  before a reboot silently never fires.
- **Scope:** On boot, re-schedules every still-active TIME reminder whose time is still in the
  future. No network, no other side effects.

## WAKE_LOCK
- **Added:** 2026-11-10, D24.
- **Why:** Transitively required by `androidx.work:work-runtime` (nightly AI scheduling). Held
  briefly by WorkManager itself while a scheduled job runs, so the system doesn't suspend the CPU
  mid-run.
- **Scope:** Only held during the once-daily AI night-loop job, which only runs while charging,
  idle, and battery isn't low. No network, no other side effects.
- **Sign-off:** owner, 2026-11-10 (in-session).

## FOREGROUND_SERVICE
- **Added:** 2026-11-10, D24.
- **Why:** Declared unconditionally in `androidx.work:work-runtime`'s own manifest. Mind Palace
  does not use WorkManager's expedited/foreground-service work mode (only a plain
  `PeriodicWorkRequest`), so this permission is never actually exercised — it ships because the
  library declares it defensively for apps that do use that mode.
- **Scope:** Unused by this app in practice. No network, no other side effects.
- **Sign-off:** owner, 2026-11-10 (in-session).

## INTERNET
- **Added:** 2026-11-10, D26.
- **Why:** Handwriting capture's ML Kit Digital Ink Recognition has no bundled/offline model
  variant (unlike the OCR text-recognition model, D17) — the handwriting model can only be
  obtained via a one-time dynamic download. This is the one deliberate exception to Mind
  Palace's zero-network design; CLAUDE.md rule 1 updated accordingly.
- **Scope:** Used only to download the ink recognition model (once per language, cached
  afterward). Recognition itself, and everything else in the app, still runs fully on-device —
  no note content, transcript, or OCR/ink text is ever sent anywhere.
- **Sign-off:** owner, 2026-11-10 (in-session, explicit — confirmed understanding that this
  retires the absolute "never" in rule 1 for this one path).

## ACCESS_NETWORK_STATE
- **Added:** 2026-11-10, D26.
- **Why:** ML Kit's `RemoteModelManager`/`DownloadConditions` checks network state (e.g. to
  require Wi-Fi) before attempting the one-time model download above.
- **Scope:** Same as INTERNET — read-only connectivity check, no other use.
- **Sign-off:** owner, 2026-11-10 (in-session).
