# Wyze Vacuum Connect — TODO / Planned Enhancements

Not yet implemented. Tracked here so they survive across sessions.

## 1. Skip in-progress rooms when `cleanNextRooms()` is called again

**Untested — try this first before assuming it's broken.**

Current `cleanNextRooms()` re-sorts the *entire* rotation list by "last cleaned"
timestamp every time it's called. A room's timestamp only updates on confirmed
completion (`finishActiveCleanRun`/`markRoomsCleaned`), not at dispatch. So if
you call `cleanNextRooms()` again while a batch is still mid-clean, the
in-progress rooms still look like the most-overdue candidates and will very
likely get re-selected — not advance to a new group. Worse, `dispatchRoomClean()`
overwrites the single-slot `state.activeCleanRun[mac]`, so the first (still
running) batch's bookkeeping gets clobbered rather than properly finalized.

**Fix:** exclude `state.activeCleanRun[mac].roomIds` from the candidate pool
in `cleanNextRooms()` so a repeat call picks the *next* least-recently-cleaned
group instead of re-picking the current one.

**Open question, needs live observation, not just a code fix:** how does the
physical vacuum/Wyze firmware actually behave when a brand-new room-clean
command arrives while it's already mid-job on a previous one? Does it abandon
the current room and switch immediately, queue it, or something else? No
evidence of command queuing in the reverse-engineered API, so "abandons and
switches" is the working assumption, but unconfirmed.

## 2. Attribute showing rooms currently being cleaned

`lastCleanedRooms` only reflects *confirmed-completed* rooms (as of the
partial-credit fix) — there's no attribute showing what's actively being
cleaned right now. Add e.g. `currentlyCleaningRooms`:
- Populate from `state.activeCleanRun[mac].roomIds` (resolved to names) when a
  room-scoped clean is dispatched (`dispatchRoomClean` / `dispatchLearningRoom`).
- Clear it (e.g. to `"none"`) when that run ends (`finishActiveCleanRun` /
  `handleLearningRoomEnd`), or on whole-house `start()`/`dock()`/`pause()`.

## 3. Battery-aware full-rotation auto-sweep mode

A new mode that works through the **entire** rotation room list across
multiple battery cycles in one go, rather than one batch per trigger:

1. Clean the configured group size (per the existing count/time rotation mode).
2. Check battery: **above** the configured threshold → advance immediately to
   the next group.
3. **At/below** threshold → return to dock, wait for battery to climb back
   above the threshold, then resume with the next group.
4. Repeat until every rotation room has been cleaned this cycle, **or** the
   user presses `dock()` — which should abort the whole sweep, not just stop
   the current group.

Needed pieces:
- A persistent "sweep in progress" state: the *remaining room queue across the
  whole rotation list*, distinct from a single dispatch batch (Learning Mode's
  queue/advance machinery is a reasonable structural starting point, but gated
  on battery level with auto-resume-after-charging instead of advancing
  immediately after each room).
- A configurable battery threshold setting (per vacuum).
- Polling-driven logic to detect "charged back above threshold" and
  auto-resume the sweep.
- `dock()` needs to cancel the whole sweep, not just the in-progress group —
  currently `dock()` has no concept of an active sweep to cancel.

## 4. Enrich notification content with rooms and next-up info

- "Notify when cleaning starts" should say *which rooms* are being cleaned
  (from `state.activeCleanRun[mac].roomIds` resolved to names, or "whole
  house" for a plain `start()`).
- "Notify when cleaning finishes" / returning-to-charge should include
  current charge status (battery %, charging true/false) and which rooms
  are next in the rotation queue (the next N candidates `cleanNextRooms()`
  would pick).

Mostly enriching the existing `sendVacuumNotification(...)` call sites in
`pollVacuum`/`handleCleaningSessionEnd`/`dispatchRoomClean` using data
that's already available. Shares underlying data with #2 above
(`currentlyCleaningRooms`).

## 5. Actionable Stop/Skip links in push notifications

Let a push notification (e.g. Pushover) include a tappable HTTP link that
triggers "Stop" or "Skip these rooms" directly, without opening Hubitat.

Needed pieces:
- Enable `oauth: true` on the app (currently `false`) and add mapped HTTP
  endpoints, mirroring Volvo's `/callback` pattern:
  `mappings { path("/stop"){action:[GET:"webStopVacuum"]} path("/skipRooms"){action:[GET:"webSkipRooms"]} }`
- Secure the same way Volvo's callback is — Hubitat's built-in per-app
  `access_token` embedded in the URL.
- Embed the plain URL as text in the notification body — most notification
  apps (Pushover included) auto-link bare URLs in message text, no special
  action-button API needed.
- "Stop" maps directly to `dockVacuum(mac)`.
- "Skip these rooms" needs product thinking before implementing: does it
  just dock (same as Stop, relying on the existing partial-credit logic to
  leave the rooms pending), or does it need to explicitly *defer* those
  rooms so they don't immediately get picked again next cycle (distinct
  from "cleaned" or plain "interrupted")? Decide this before implementing.
