# PLAN.md — section-reference key (reconstructed)

> **This is not the original Mind Palace build plan.** The original is not in the repo. This file
> was reconstructed on 2026-11-11 from every `§` citation in the code, tests, and docs, so that a
> reader (or a faster model) can resolve a reference like "§3.7" without the original.
>
> **Owner action:** skim the table once. Where it disagrees with the real plan, the real plan
> wins — fix the row here and add a line to `docs/DECISIONS.md`. Rows marked *unknown* were never
> cited in the repo; nothing is known about them and nobody should guess.

## How to use this file

- A code comment that says `§N` means "see row N below".
- "Evidence" lists where the repo states the rule, so you can read the exact wording.
- If a task needs a section marked *unknown* or *partial*, **stop and ask the owner**.

## Section key

| § | Topic (as cited) | What the repo says it requires | Evidence |
|---|---|---|---|
| 1 | *unknown* | Never cited. | — |
| 2 | Scope and defaults | Six default categories: Task/Errand, Classroom, Curriculum/Pacing, App idea, Personal, Inbox — "not yet confirmed; use these and make them editable". Offline rule: a shared link is stored as link + title only; the article is never fetched. | `core/model/Model.kt`, `core/commands/NoteCommands.kt` |
| 3.1 | Save first | Capture persists immediately in one transaction. Nothing waits on rules, indexing, or AI. | `NoteCommandHandler.create`, `ShareReceiverActivity` |
| 3.2 | Originals are sacred | `raw_text`, `transcript`, `ocr_text` are stored verbatim. Only the user may change them, and only through the command handler. Deletes are soft. | `NoteEntities.kt`, `NoteCommandHandler` |
| 3.4 | Validate before storing | AI output is structured JSON so a validator can check it before it is stored. | `AiEntities.kt` (`AiIndexEntity`) |
| 3.6 | Private by construction | Originally: no `INTERNET` permission, enforced by a test. Since D26 (2026-11-10) the rule is "only owner-approved permissions": `INTERNET` exists for the handwriting model download only, and the test checks an approved list. Export is manual and user-triggered. | `PrivacyManifestTest`, `docs/PERMISSIONS.md`, D4, D26 |
| 3.7 | Everything reversible | Every mutation writes an audit row with before/after snapshots and can be undone. Concurrent edits fail loudly (revision guard) instead of overwriting. | `NoteCommandHandler`, `Daos.kt` |
| 4 | Tech choices | "Room + FTS5 for search". Realized as Room 3 + `BundledSQLiteDriver` (D3). | `NoteEntities.kt`, D3 |
| 5 | Architecture | Module list (`core/model`, `core/db`, `core/commands`, `export`, …); "packages first, split to modules only if needed". The command handler is the only mutator of canonical data. | D1, `NoteCommandHandler` |
| 6 | Data model | Tables: `notes` (UUID id), `derived` (with origin), `topics`, `note_links`, `reminders`, `people`, `places`, `rules`, `ai_index`, `proposals`, `ai_runs`, `audit` (actors user/rule/ai), `jobs`. | `core/db/*`, `app/schemas/…/1.json`, D5–D7 |
| 7 | People | People are never auto-merged. Two plausible matches must ask, not guess. | `SupportEntities.kt` (`PersonEntity`) |
| 8 | AI tiers and guardrails | **Tier A:** auto-generated index (summary, keywords, entities), always labelled "AI", never replaces user fields, user can delete it. **Tier B:** proposals that need approval; must carry an evidence quote that is a literal substring of the note; confidence threshold; rejected proposals are not re-proposed; AI picks from the existing topic vocabulary first. **Guardrails:** jobs are idempotent per (note revision, task, prompt version); runs are logged in `ai_runs`. | `AiEntities.kt`, `SupportEntities.kt` |
| 9, 10 | *unknown* | Never cited. | — |
| 11 | Links | Obsidian-style links: explicit `[[name]]`, rule-made (shared person/tag/place), AI-suggested. States: suggested → confirmed / rejected. | `SupportEntities.kt` (`NoteLinkEntity`) |
| 12 | Sharing in | Link-only shares store link + title (offline rule). | D9 |
| 13 | *unknown* | Never cited. | — |
| 14 | Testing | Every schema version bump needs a `MigrationTest`. | D3, `MindPalaceDatabase.kt` |
| 15 | Risks and mitigations | If FTS5 is unavailable or hostile input breaks it, fall back to a `LIKE` query. | D10, `Daos.kt` (`searchLike`) |
| 16 | Process rules | 16: a test must fail if an unapproved permission appears (originally: if `INTERNET` appears; see §3.6). 16.6: keep a chronological decision log. 16.8: verify versions, do not assume. | `PrivacyManifestTest`, `DECISIONS.md` header, D2 |

## Checkpoints cited in the repo

| Checkpoint | What is known | Evidence |
|---|---|---|
| 0 | On-device: install, save, survive kill and reboot, share-in works, search works, airplane mode, capture-to-saved under 2 s. **Passed 2026-11-09** (README). | `README.md`, `docs/CHECKPOINT_0.md` |
| 1 | "Export round-trips." | `NoteExporterTest` |
| 3 | "Every derived field shows its origin." | `SupportEntities.kt` (`DerivedEntity`) |
| 2, 4, 5 | *unknown* | — |

## Phases cited in the repo

| Phase | Content (as cited) | State on 2026-11-11 |
|---|---|---|
| 0 | Project, schema, privacy gate, packaging. | Done |
| 1 | Capture: typed, share, voice; FTS search; export; app UI. | Built; hardening in plans 01, 07, 08, 15 |
| 2 | Photo capture and OCR. | Built (plus handwriting and attachments); plan 09 |
| 3 | Rules engine, editable categories. | First version built; plan 10 |
| 4 | Surfacing: reminders, geofences, calendar. | Time reminders built; plan 11 |
| 5 | On-device AI and the night loop. | First version built; plan 12 |

## Rule for new code comments

Cite this file's rows (`PLAN.md §3.7`) or a decision (`D4`). Do not cite a section marked
*unknown*.
