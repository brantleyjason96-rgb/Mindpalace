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
