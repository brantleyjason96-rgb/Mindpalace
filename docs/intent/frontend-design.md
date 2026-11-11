# Frontend design intent (confirmed 2026-11-09)

Captured via interview before starting the frontend redesign. Downstream work (the screen
build-out) should check back against this rather than re-litigating it.

## Outcome
Redesign Mind Palace's visual identity (not just polish the existing one) and build out the
next four screens on top of a new, shared design language:

1. Capture/browse (deepen the existing screen)
2. Status triage — Inbox / Active / Done / Archived
3. Note detail/edit (tap a card to open it)
4. Search (FTS5 already wired in the backend)
5. Settings/export (JSON/Markdown export already wired in the backend)

## Mood
Calm, tactile, private — analog-notebook / quiet-room feel. Warm neutrals, a serif accent on
titles only (body stays sans for readability). Explicitly **not** glossy SaaS, dark-dashboard,
or neon-AI-app energy.

## Motion
Mostly subtle, physical/spring-based everyday motion. A small, deliberate number of
**signature moments** stand out above that baseline — not uniform restraint, not flashy
everywhere:

- **Save** — a freshly saved note "settles" into the list (slight scale overshoot + a brief
  warm tint that relaxes into the normal card surface). Implemented in `MainActivity.kt`
  (`NoteCard`'s `justSaved` path).
- **Triage swipe** — still to build with the status triage screen; should have real physical
  weight (rotate while dragging, resist at the edges, throw off-screen on commit), closer to a
  physical card than the stock `SwipeToDismissBox` fade.

Everything else (list reorder, search filter, navigation) uses the calm default spring — no
extra flourish layered on.

## Palace metaphor
Decorative/motion only. It shows up in how saving/organizing *feels*, never in navigation
structure. Information architecture stays simple: lists, filters, tabs — no rooms-as-navigation.

## Constraints (carried over from `docs/DECISIONS.md`)
- Frontend-only: no touching `NoteCommandHandler`, DAOs, schema, or manifest/permissions.
- Zero permissions, offline-only, save-first command handler as the only writer.
- Single-module/package layout, manual DI — no new architecture introduced for this work.

## Out of scope (for this pass)
- Rules engine, AI/night-loop, reminders/geofences — all later phases, untouched.
- Restructuring navigation around literal "rooms."

## Reference notes
21st.dev's catalog skews SaaS/dashboard and had no direct match for this mood (no "theme" hit
for warm/paper/serif at all). Two results were useful as *interaction* references, not visual
ones:
- **Card Stack / Swipe Deck** — physical drag-to-decide with undo; model for the triage swipe.
- **Blog Cards** — dashed-separator editorial minimalism; model for note-card typography.

## Progress
- [x] Step 1 — design system (new palette + serif-accent typography in `Theme.kt`, window
      background colors) + save "settle" signature moment on the capture/browse screen.
      Builds clean (`assembleDebug`, 2026-11-09). Added a light/dark/auto theme toggle
      (`ThemePrefs`, SharedPreferences) after the owner found the default too dark; defaults to
      Light.
- [x] Step 2 — status filter chips (All/Inbox/Active/Done/Archived) added to the existing
      capture/browse screen rather than a separate destination (keeps IA flat). Triage swipe
      (signature motion #2) added alongside the existing delete swipe: swipe left still
      deletes (unchanged), swipe right advances status one step with real physical weight
      (rotation while dragging, resistance past the commit threshold, throws off-screen before
      the write lands), with Undo. Builds clean, 27/27 unit tests still green, 2026-11-09.
- [x] Step 3 — note detail/edit screen. Tap still expands the card in place (confirmed working
      after fixing a tap-vs-swipe gesture conflict); an "Open note" button appears in the
      expanded state and navigates to a dedicated detail screen (simple in-memory nav state in
      `MainActivity`, no navigation-compose dependency added). Detail screen: editable body
      (`updateRawText`, already existed), status picker, delete, system back support. Title is
      shown read-only — there's no `updateTitle` command in `NoteCommandHandler` yet, and adding
      one would touch the backend, which is out of scope for this pass. Builds clean, 27/27
      tests green, installed, 2026-11-09.
- [x] Step 4 — search. Already functionally present as inline search-as-you-type at the top of
      the capture/browse screen (FTS5-backed, query debounced 120ms) rather than a separate
      destination — results reuse the exact same card, swipe, and "Open note" affordances as
      browse, so nothing had to be duplicated and IA stayed flat. Added the two pieces that were
      actually missing: a clear ("×") button on the search field, and highlighted match terms in
      result titles/bodies (best-effort substring highlight; FTS5's stemming tokenizer means it
      won't always align with the literal matched text, acceptable trade-off). Builds clean,
      installed, 2026-11-09.
- [x] Step 5 — settings/export screen. Reachable via a "Settings" button next to the theme
      toggle in the header. Shows note count and app version, exports to JSON or Markdown
      (`NoteExporter`, already existed) and opens the system share sheet on the result. Sharing
      needed a `FileProvider` — the one manifest change in this whole pass — confirmed with the
      owner first since it crossed the "frontend-only, no manifest" line from the original
      intent; no permission added, `PrivacyManifestTest` still green (27/27). Builds clean,
      installed, 2026-11-09.

All five steps from the original plan are done. Frontend pass complete pending owner review.

## Final review pass (2026-11-09)
A code-review agent reviewed the full uncommitted diff. Confirmed: constraints honored (no
backend/DAO/schema touches, only manifest change is the approved FileProvider, zero permissions,
27/27 tests green). Three real issues found and fixed:
- Export errors (disk full, IO error) were unhandled — could wedge the export buttons disabled
  forever and crash the app. Now wrapped in try/catch/finally with a failure snackbar.
- Rapid consecutive saves could leave an earlier note's "settle" animation visibly stuck
  mid-scale/tint — `justSaved` was in the `LaunchedEffect` key, so a second save cancelled the
  first note's in-flight animation. Fixed by capturing `playSettle` once at first composition
  and keying the effect on `note.id` only.
- `PackageManager.getPackageInfo` in Settings was unguarded against the (unlikely) checked
  exception. Wrapped in `runCatching`.
One accepted trade-off, left as a `ponytail:` comment rather than fixed: export files accumulate
unbounded in the app's private dir — not urgent, revisit if export becomes a frequent action.
