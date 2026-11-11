# Capture tab: Stitch-mockup elements not yet grafted over

Written 2026-11-10, after the bottom-tab restructure (Capture/Timeline/Vault/Privacy & Rules)
and the first Capture-tab enrichment pass (mode chips, live draft status, live rule-preview
chip, real inbox cards in "Awaiting Triage"). This tracks what's left, in priority order as
discussed: **AI implementation first**, then the rest.

## 1. AI-assisted entity detection (do first)

Stitch's "Dynamic Smart Entity Detection Tray" showed live `@person` / `#tag` / time-phrase
chips while typing. Two tiers:

- **Tier 1 (cheap, no AI needed):** regex-based `@name` / `#tag` / date-phrase highlighting in
  the composer as you type. Not started.
- **Tier 2 (uses the existing AI scaffolding):** richer extraction via the on-device model —
  `ai/AiEngine.kt`, `ai/AiNightRunner.kt`, `ai/AiPrompt.kt`, `ai/AiProposalResponse.kt`,
  `ai/AiProposalValidator.kt`, plus the `ai_runs`/`proposals` tables.

**Status as of 2026-11-10 — the missing link is now built:** the pipeline already generated
real CATEGORY/TAG/TOPIC/PERSON/PLACE proposals (grammar-constrained llama.cpp completion,
evidence-quote validated against the note text, confidence-gated) but had **no way to ever
apply one** — `ProposalState` sat at `PENDING` forever. Added:
- `NoteCommandHandler.approveProposal(id)` / `.rejectProposal(id)` — approve writes the real
  derived fact (origin `ai:<runId>`) and, for TOPIC/PERSON/PLACE, registers the name in that
  vocabulary (same `insertIfAbsent` pattern as the manual add flows) so it shows up in Vault
  immediately. Reject only changes state. Never touches note text (rule 5).
- `ProposalDao.observeByState` (new `Flow` query) + `MainViewModel.pendingProposals` /
  `.approveProposal` / `.rejectProposal`.
- `AiProposalsScreen` (CLAUDE.md backlog: "AI: Proposals review") — lists pending proposals
  with their evidence quote, confidence %, and the source note's snippet; Approve/Reject
  buttons. Reachable from Privacy & Rules tab, with a live pending-count badge.
- 3 new `NoteCommandHandlerTest` cases (approve writes derived fact + registers vocab, approve
  on non-PENDING is a no-op, reject only changes state). 45/45 tests green.

**Still open, in order:**
1. Tier 1 regex highlighting in the composer (independent of the AI pipeline, cheap, do
   whenever).
2. **Nightly scheduling** — `AiNightRunner` doc comment says it plainly: "Runs synchronously on
   the caller's dispatcher; the WorkManager wrapper that schedules this nightly (charging +
   idle, stop on heat/battery) is the next piece, not built yet." Right now the only way to run
   it is the debug-only "Run on all notes" button in Settings.
3. **AI setup screen** (CLAUDE.md backlog) — pick the GGUF model file instead of the hardcoded
   `getExternalFilesDir(null)/model.gguf` path, plus schedule/limit controls once WorkManager
   scheduling exists.
4. **AI run history** (CLAUDE.md backlog) — a list view over `ai_runs` (model, started/finished,
   outcome, notes processed) — the data's already recorded, just not surfaced.
5. Tier 2 of entity detection in the *Capture* composer itself (live, pre-save) is a different
   shape of problem than the night-loop (which runs post-save, in batch) — worth a separate
   design pass once 1–4 are settled, not a trivial extension of the night-loop.

## 2. Attach-file capture — DONE (2026-11-10, D25)

`NoteSource.ATTACHMENT` added, schema v2→v3 (`MIGRATION_2_3`, schema JSON exported, migration
test added), a 4th "Attach" mode chip in the Composer using the system document picker, copies
into a new `attachmentsDir`, `MainViewModel.captureAttachment`, `NoteCard`/`sourceLabel` render
it, rules/AI can match on the filename. 50/50 tests pass. See D25 in `docs/DECISIONS.md`.

## 3. Handwriting capture mode — confirmed wanted, scope next

Owner decision 2026-11-10: build it. Still the largest ungrafted item — needs:
- A drawing canvas (Compose `Canvas` + stylus/touch input, or `MotionEvent` ink prediction for
  the S-Pen).
- ML Kit Digital Ink Recognition (new dependency — ask first, same as any dependency per
  CLAUDE.md).
- A new `NoteSource.INK` (or similar) + its own capture screen mirroring Voice/Photo's
  review-before-save flow (recognize → editable text → save).

Next task to scope/build, as its own focused unit of work (not bundled with anything else).

## 4. Manual notebook/category override at capture time

Owner decision 2026-11-10: a hand-picked category is a **permanent** override — once set
manually, the rule engine never touches that note's category again. Not yet built. Needs:
- A new derived-fact origin (something like `Origin.UserOrigin`) or an explicit
  `NoteEntity`/command change, and a decision on how `applyRules`/the AI pipeline should skip a
  note that already carries a user-origin category.
- A picker UI reading `model.categories` (Vault's data), wired into the composer or note detail.

## 5. Privacy Level: Biometric toggle — declined

Owner decision 2026-11-10: not building this. Nothing in the app currently needs gating behind
biometrics, and it would require a new permission + dependency. Revisit only if a concrete need
for it shows up later.

## 6. Inline hold-to-record waveform on the Capture tab

Voice capture today is a deliberate separate full screen (`VoiceCaptureScreen`) with its own
mic-permission explainer and `SpeechRecognizer` plumbing. Moving it inline (waveform +
hold-to-record directly in the Capture tab, like Stitch's big dictate button) would duplicate
that plumbing for a mostly cosmetic win.

**Recommendation:** leave as a separate screen unless Jason specifically wants the inline
experience badly enough to justify the duplicated code.
