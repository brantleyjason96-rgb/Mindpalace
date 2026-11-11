# Plan 11 — Reminders, geofences, calendar

**State today (D19):** time reminders exist. `AlarmScheduler` sets an exact alarm,
`ReminderAlarmReceiver` posts a notification, `BootReceiver` re-schedules after a reboot.
Permissions `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` are signed
off. Place and person reminders, geofences, and calendar are not built. D14 puts geofenced place
reminders in scope and leaves "Play Services or not" open.

This plan has three parts. **Part A needs no sign-off and should be done first.** Parts B and C
each start with an owner sign-off.

| Part | Content | New permission | Schema |
|---|---|---|---|
| A | Make time reminders reliable | none | v6: `reminders.fire_at`, `fired_at` |
| B | Place reminders (geofences) | **`ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`** | none (columns exist on `places`) |
| C | Calendar read (optional, last) | **`READ_CALENDAR`** | none |

---

## Part A — Reliable time reminders

### A1. Problems (audit R1)

| # | Problem | Where |
|---|---|---|
| 1 | Exact alarms not allowed → `schedule` returns silently; the row is `ACTIVE` and never fires. | `AlarmScheduler.kt` 30–31; `MainActivity.kt` 2480–2484 |
| 2 | A time in the past is accepted and never scheduled. | 2617–2626 |
| 3 | Phone off at the reminder time → skipped on boot, `ACTIVE` forever. | `BootReceiver` |
| 4 | Reminder on a soft-deleted note still fires. | `ReminderAlarmReceiver` |
| 5 | Notification permission denied → notification dropped, no trace. | `NotificationHelper.show` |
| 6 | After the user grants exact alarms in system settings, existing reminders are not re-scheduled. | — |
| 7 | Small icon is the launcher mipmap (shows as a blank square on most phones). | `NotificationHelper` |
| 8 | Reminder writes go from the ViewModel to `ReminderDao`. | `MainViewModel` 397–424 |
| 9 | `spec` is an ISO string parsed on every read; the list shows the raw ISO text. | 2529, 4492 |
| 10 | A firing reminder has never been recorded as verified on the phone. | D25 |

### A2. Design

**Schema v6** (`MIGRATION_5_6`, plan 14 template): `ALTER TABLE reminders ADD COLUMN fire_at
INTEGER` and `ADD COLUMN fired_at INTEGER` (both nullable). Backfill `fire_at` in Kotlin inside
the migration: read each TIME row's `spec`, parse with `Instant.parse`, write the millis; leave
`NULL` if unparseable. `spec` stays (data contract) and keeps being written.

**Pure decision function** (new `core/reminders/ReminderPlanner.kt`, no Android types):
```kotlin
sealed interface AlarmPlan {
    data class Exact(val atMillis: Long) : AlarmPlan
    data class Inexact(val atMillis: Long) : AlarmPlan     // setAndAllowWhileIdle
    data object FireNow : AlarmPlan                        // missed while off / in the past
    data object Nothing : AlarmPlan                        // not ACTIVE, not TIME, already fired
}
fun plan(reminder: ReminderEntity, now: Long, canExact: Boolean, noteDeleted: Boolean): AlarmPlan
```
Rules: not `ACTIVE`, or `fired_at != null`, or `noteDeleted` → `Nothing`. `fire_at <= now` →
`FireNow`. Otherwise `Exact` if `canExact`, else `Inexact`.

`AlarmScheduler.schedule` applies the plan. `Inexact` uses
`setAndAllowWhileIdle(RTC_WAKEUP, …)`, which needs no permission and may be several minutes
late; that is better than never.

**Firing.** `ReminderAlarmReceiver` and the `FireNow` path call one function,
`ReminderCommands.fire(reminderId)`: checks state and that the note is not deleted, posts the
notification, sets `fired_at = now`. The reminder stays `ACTIVE` until the owner marks it done,
so it shows in the Reminders screen under a "Due" heading. If notifications are not permitted,
`fired_at` is still set and the in-app "Due" list is the fallback.

**Catch-up.** One function `ReminderCommands.rescheduleAll(context)` runs `plan` over
`activeTimeReminders()`. Call it from: `BootReceiver`; `MindPalaceApplication.onCreate`; and a
new manifest receiver for
`android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (no permission needed;
register it in the manifest with `exported="false"`; confirm on the phone that it is delivered,
and if it is not, rely on the `onCreate` call). `MY_PACKAGE_REPLACED` is covered by the
`onCreate` call.

**`ReminderCommands`** (new, `core/commands/ReminderCommands.kt`): `add(noteId, atMillis)`
(rejects a time less than 30 s ahead with `IllegalArgumentException`), `cancel`, `complete`,
`snooze(id, untilMillis)` (sets `fire_at`, clears `fired_at`), `fire`, `rescheduleAll`. Not
audited (D19 keeps reminders outside the note audit), but exported (plan 15). The ViewModel's
three functions call it.

**UI** (`ReminderSection` 2469–2638, `RemindersScreen` 4442–4533):
- Run the exact-alarm check on every path into the date picker, including the notification
  permission callback. If exact alarms are off, offer "Allow exact timing" and "Use approximate
  timing" (saves with `Inexact`).
- Reject past times in the picker with an inline message.
- Show times with `DateFormat.getBestDateTimePattern` so the 24-hour setting is respected; never
  show the raw `spec`.
- "Due" section with Done and Snooze (1 hour, tomorrow 8:00).
- Notification: a monochrome vector small icon (`res/drawable/ic_stat_reminder.xml`), and
  "Done" and "Snooze 1h" actions handled by `ReminderAlarmReceiver`.

### A3. Tests

`ReminderPlannerTest` (plain JUnit): T-RM1 future + canExact → `Exact`; T-RM2 future + no exact
→ `Inexact`; T-RM3 past → `FireNow`; T-RM4 `DONE`/`CANCELLED` → `Nothing`; T-RM5 `fired_at` set
→ `Nothing`; T-RM6 note deleted → `Nothing`; T-RM7 exactly `now` → `FireNow`.

`ReminderCommandsTest` (Robolectric, `ShadowAlarmManager`, `ShadowNotificationManager`):

| ID | Test name | Assert |
|---|---|---|
| T-RM8 | `add schedules one alarm at fire_at` | `shadowAlarmManager.scheduledAlarms.single().triggerAtTime == at`. |
| T-RM9 | `add rejects a past time` | throws; no row. |
| T-RM10 | `fire posts a notification and stamps fired_at once` | call twice → one notification, `fired_at` set. |
| T-RM11 | `fire does nothing for a deleted note` | no notification. |
| T-RM12 | `rescheduleAll fires missed reminders and schedules future ones` | one past, one future → one notification, one alarm. |
| T-RM13 | `cancel removes the alarm` | no scheduled alarms left. |
| T-RM14 | `snooze re-arms a fired reminder` | `fired_at == null`, alarm at the new time. |

`MigrationTest`: `migrate 5 to 6 backfills fire_at from spec` — row with
`spec = '2026-12-01T13:00:00Z'` → `fire_at == 1796130000000`; row with `spec = 'junk'` →
`fire_at IS NULL`.

Also T-MAN1 from plan 02 (manifest receivers resolve to classes) must exist before this part
starts.

### A4. Device check (owner, 5 minutes) — add to `docs/CHECKPOINT_0.md`
1. Set a reminder 2 minutes ahead; lock the phone → notification arrives within a minute of the
   time.
2. Set one 3 minutes ahead; power the phone off for 5 minutes; power on → notification arrives
   after boot without opening the app.
3. Turn off "Alarms & reminders" for Mind Palace in system settings; set a reminder → the app
   offers approximate timing; it fires within about 15 minutes.

---

## Part B — Place reminders (geofences)

### B1. Sign-off points (stop at each)

| # | Decision | Options |
|---|---|---|
| **SO-B1** | Add `ACCESS_FINE_LOCATION` and `ACCESS_BACKGROUND_LOCATION`. | Required for any geofence that fires while the app is closed. Android asks in two steps; background access is granted on a system settings page ("Allow all the time"). |
| **SO-B2** | Which engine (D14's open question). | **(a) Framework `LocationManager.addProximityAlert`** — no new library, works with no network, uses GPS and so more battery; alerts are cleared on reboot and must be re-registered. **(b) Play Services geofencing** (`play-services-location`) — better battery and accuracy, needs Google Play services on the phone, adds a Google library to the app. Recommended: (a) first; it matches the app's "no Google services required" stance and the S26 Ultra handles it. |
| **SO-B3** | How a place gets coordinates. | "Use where I am now" (one location fix, foreground only) — recommended. Typing an address needs a geocoder, which needs network: not offered. A map picker needs map tiles: not offered. |

Research note (not verified on a device): `play-services-location` 19.0.0 reportedly declares no
permissions of its own, from a single unconfirmed source. Check the merged manifest if option (b)
is chosen.

### B2. Design (after sign-off)
- `places` already has `latitude`, `longitude`, `radius_meters`, `external_ref`. No migration.
- `ReminderType.PLACE` reminders store the place id in `spec`.
- New `core/reminders/GeofenceRegistrar` interface with `register(place)`, `unregister(place)`,
  `registerAll()`; one implementation per SO-B2 choice; a fake for tests.
- `registerAll()` joins `rescheduleAll` (boot, app start).
- A new receiver handles the proximity broadcast: find `ACTIVE` PLACE reminders for that place
  → `ReminderCommands.fire`. Re-arm on exit so it can fire again on the next visit only if the
  reminder is still `ACTIVE` and not `fired_at`-stamped.
- Default radius 150 m, minimum 100 m (smaller radii miss).
- Permission flow screen: explain → request fine location → explain "Allow all the time" →
  open the system page → on return, re-check. If background access is refused, keep the reminder
  and label it "Only checks while the app is open".
- `PlacesScreen`: add "Set location" (uses the current position) and show whether a place has
  coordinates.
- Offline: geofences need no network. Say in the UI that GPS accuracy indoors can delay a trigger.

### B3. Tests
`GeofencePlanTest` (pure): which places need registering (has coordinates, has an `ACTIVE`
PLACE reminder). `ReminderCommandsTest`: entering a place fires its reminders once; a `DONE`
reminder does not fire; `registerAll` after boot registers each place once (fake registrar).
`PrivacyManifestTest` and `docs/PERMISSIONS.md` updated in the same commit as the manifest.

---

## Part C — Calendar read (optional, last)

**SO-C1:** add `READ_CALENDAR`. Purpose: show "you have Period 3 Earth Science in 20 minutes" on
notes tagged for a class, or offer event times when setting a reminder. No calendar writes.
- Reads `CalendarContract.Instances` for the next 7 days, on demand, on-device; nothing stored
  except an event id in `ReminderEntity.spec` for "remind me at this event".
- Offline: the calendar provider is local; works in airplane mode with whatever has synced.
- This is the least valuable part. Recommendation: do not build until the owner asks for a
  specific use.

`ReminderType.PERSON` (remind me when I mention or meet a person) has no trigger without contacts
or calendar access; leave it unbuilt and say so in the UI plan.

---

## Files allowed (Part A)

`core/reminders/*` (plus new `ReminderPlanner.kt`), new `core/commands/ReminderCommands.kt`,
`core/db/Daos.kt` (`ReminderDao`), `core/db/SupportEntities.kt` (`ReminderEntity`),
`core/db/Migrations.kt`, `MindPalaceDatabase.kt` (version), `MindPalaceApplication.kt`,
`AndroidManifest.xml` (one receiver; **no** `uses-permission` line), `MainActivity.kt`
(`MainViewModel` reminder functions, `ReminderSection`, `RemindersScreen` only),
`res/drawable/ic_stat_reminder.xml`, `strings.xml`, tests, generated schema JSON.

## Done when (Part A)

- All Part A tests pass in CI; the permission list is unchanged.
- The three device checks in A4 are logged as PASS in `docs/TEST_RESULTS.md`.
- `DECISIONS.md` entry: inexact fallback, fire-on-boot for missed reminders, `fire_at` column.

## Stop and report if

- Any step seems to need a `uses-permission` line not already in the manifest.
- `ShadowAlarmManager` cannot observe `setExactAndAllowWhileIdle` (then test through
  `ReminderPlanner` only and say so).
