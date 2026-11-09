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
