# Wyze Vacuum Connect — TODO / Planned Enhancements

Not yet implemented. Tracked here so they survive across sessions.

## ~~1. Skip in-progress rooms when `cleanNextRooms()` is called again~~ — DONE (1.8.0)

Confirmed live: calling `cleanNextRooms()` again while a batch was still
mid-clean re-selected the same in-progress rooms and re-dispatched an
identical command, which visibly did nothing (the vacuum was already doing
exactly that). Fixed by excluding `state.activeCleanRun[mac].roomIds` from
the candidate pool in `previewNextRooms()`.

**Still open, needs live observation:** how does the physical vacuum/Wyze
firmware behave when a brand-new room-clean command arrives while mid-job on
a *different* set of rooms (not this same-rooms case, which is now avoided)?
Does it abandon the current room and switch immediately? No evidence of
command queuing in the reverse-engineered API, so "abandons and switches" is
the working assumption, but unconfirmed.

## ~~11. Missing start/finish notifications + poll-mode flapping~~ — DONE (1.15.0)

Real bug, confirmed live twice: (1) user reported "still not getting
notifications when cleaning starts... I do get error alerts, but that's it"
-- both notifyCleaningStarted and notifyCleaningFinished were silently not
firing; (2) live log showed `rescheduleDynamicPoll: switched to idle polling`
while the vacuum was visibly still Cleaning (status/mode both confirmed
"Cleaning" on the device page at the time).

Root cause: `rescheduleDynamicPoll()`'s "is anything cleaning" check relied
solely on `state.lastKnownStatus`, which is only set from a *confirmed* poll
response. Two consequences: (a) with rooms-per-run now defaulting to 1 and
the idle interval defaulting to 15 min, a short single-room clean could
start and finish entirely between two idle-interval polls, so
`lastKnownStatus` never saw "Cleaning" at all -- no transition ever
detected, no notifications, no room credit; (b) even once fast polling
engaged, a single noisy/transient status read could flip it straight back
to idle for up to 15 min.

Fixed by also trusting `state.activeCleanRun` (set synchronously at dispatch
time, cleared only on a genuine finish) as evidence of "cleaning," not just
the latest single poll reading -- and calling `rescheduleDynamicPoll()`
directly from `dispatchRoomClean`/`dispatchLearningRoom` so fast polling
engages immediately at dispatch, without waiting on a poll to confirm it
first.

Also enriched notification content per the same request: "started cleaning"
now names the room(s) (or "whole house" for a plain `start()`); "finished
cleaning" now breaks down completed vs. not-completed rooms, using
`finishActiveCleanRun`'s (now Map-returning) result instead of a generic
elapsed-minutes-only message. `notifyCleaningStarted` default flipped to
true (was false, inconsistent with the other two notification toggles which
already defaulted on). Also added a `log.info` line recording each
single-room dispatch's measured clean time, independent of debug logging,
per a related user request.

## ~~9. Battery-forced-return "fix" — tried, disproved by live data, reverted~~

Shipped in 1.13.0: treated a return-to-charge at/below a battery threshold as
"not a genuine finish" (Wyze's own low-battery behavior cutting the room
short), leaving the run active with elapsed time carried forward instead of
crediting it. **Live testing immediately falsified this**: a room legitimately
finished (confirmed against the map) with the battery down at 21% -- low
battery at dock time is apparently unremarkable, not evidence of an
interruption. Reverted in 1.14.0 back to the original, simpler rule: any
non-Paused/Error exit is trusted as a genuine finish. Noted here as a
recorded dead end so it isn't re-attempted the same way later without new
evidence.

## ~~10. Rotation sweep — auto-continue to the next due room~~ — DONE (1.14.0)

User asked directly, after a room finished and the vacuum just sat there:
"we're still 'on', shouldn't we be cleaning the next room?" Confirmed with
the user which stopping condition they wanted (auto-continue through
everything due, then stop on its own -- not indefinitely until manually
turned off, and not left as a fully manual per-room trigger).

Implementation: `cleanNextRooms()` sets `state.rotationSweepActive[mac] =
true`. On every genuine finish (`handleCleaningSessionEnd`'s non-learning
branch), `continueSweepIfNeeded()` checks `pendingRoomCount(mac)` -- if
anything's still actually due, it schedules `continueSweepDispatch` (`runIn`,
5s) to call `cleanNextRooms()` again; otherwise it clears the flag and stops.
`continueSweepDispatch` re-checks the flag before dispatching, so if
`dock()`/`pause()`/`start()` cleared it in the meantime (all three now do),
the scheduled continuation quietly no-ops instead of reactivating a sweep the
user just stopped. Verified via standalone simulations: a 3-room sweep
draining to completion and stopping on its own, and a dock()-during-sweep
cancellation correctly no-op'ing the pending continuation.

## ~~8. Default to 1 room per run + manual room-time overrides~~ — DONE (1.12.0)

User feedback: the high-traffic-tier/urgency-fraction machinery above (item
7) is more than actually needed for the core "know each room's real clean
time" goal -- if rotation dispatches exactly one room per run, every run is
already ground truth (see `finishActiveCleanRun`'s single-room branch), and
since rotation always advances to the currently-most-overdue room, repeated
`cleanNextRooms()` triggers naturally cycle through every room over time.
So: `rotationCount_${mac}` now defaults to 1 instead of 2, with a paragraph
explaining to raise it once every room has real timing data. Also added a
manual override UI (`<vacuum> — Room Timing`): one editable minutes field
per discovered room + a "Save Room Times" button, so timing data can be
restored/corrected by hand (state.roomAvgMinutes is app-local and doesn't
survive an app reinstall).

## ~~7. High-traffic rooms — clean some rooms more often than others~~ — DONE (1.11.0)

Added a "High-traffic rooms" multi-select per vacuum with its own (shorter)
cycle-length setting, distinct from the normal-traffic `rotationCycleDays_${mac}`.
Room selection in `previewNextRooms()` now sorts by relative overdue-ness
(elapsed time ÷ that room's own cycle length) instead of raw last-cleaned
time, so a 3-day-cycle room naturally surfaces ~2-3x as often as a 7-day-cycle
one across repeated `cleanNextRooms()` triggers — no hard-gated separate
queue, and it degrades to the old plain oldest-first behavior when every
room shares one cycle length. `pendingRoomCount`/`roomsPendingThisCycle`
updated to use each room's own cycle length too. Verified via a standalone
simulation (3-day vs 7-day room, triggered every 3 days over 21 days):
5 picks vs 2 picks, matching the intended "multiple times/week vs. once/week"
pacing as a soft heuristic, not an exact schedule.

## ~~6. Rough job-completeness metric from room time estimates~~ — DONE (1.10.0)

Added `lastRunCompleteness`: sums each dispatched room's learned/estimated
clean time and compares that total to the run's actual elapsed minutes, so
you get a rough "how much of this job actually got done" percentage without
needing a completion record for every individual room. Separate from (and
complementary to) the existing per-room credit/no-credit heuristic in
`finishActiveCleanRun` — that logic is unchanged.

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
