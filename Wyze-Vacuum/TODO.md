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

## ~~18. Rotation sweep silently self-cancelling via bare unschedule()~~ — DONE (1.18.4)

User: "nobody is home... something must have turned it off" -- Kitchen had
genuinely finished, `Rooms Pending This Cycle` still showed 3, Low Battery
Protection was confirmed disabled (ruling out that theory), yet `switch`
was `off` and nothing advanced to Hallway. Traced by re-reading the code
rather than more live back-and-forth: `handleCleaningSessionEnd` (called on
every genuine finish) calls `continueSweepIfNeeded()`, which schedules
`runIn(5, "continueSweepDispatch", ...)` to dispatch the next room. Right
after that, in the *same* `handleVacuumStatusResponse` call,
`rescheduleDynamicPoll()` runs (since cleaning just ended, polling should
slow back down) -- and it called bare `unschedule()`, which cancels *every*
pending one-shot job for the app instance, not just the recurring poll.
That wiped out the just-scheduled Hallway dispatch before it ever fired,
silently, with zero logging (matching the user's "nothing turned it off
that I can see" experience).

Fixed by unscheduling only the poll job by name (`unschedule("pollAllVacuums")`)
in both `rescheduleDynamicPoll()` and `updated()` (the latter has the same
risk whenever settings are saved while a sweep continuation is pending).
This is the second time a bare/overly-broad Hubitat scheduler or Groovy
truthiness gotcha has caused a hard-to-see silent failure in the sweep path
this session (see #15) -- worth remembering as a pattern for future review:
prefer named/scoped variants of `unschedule`/state-clearing operations over
blanket ones whenever multiple independent scheduled jobs can coexist.

## ~~17. status vs. charging cross-poll race (cosmetic)~~ — DONE (1.18.3)

Confirmed live right after the 1.18.2 fault-code fix: with mode/charging
both finally working correctly, `status` still briefly showed `Standby`
instead of `Docked` immediately after the vacuum actually docked (device
page showed `mode: Idle`, `charging: true`, but `status: Standby`) --
purely cosmetic, both are non-`Cleaning` states so nothing functional was
affected, but undermines confidence after all the status-accuracy work
this session. Cause: `charging` was read from the separately-polled props
attribute, which can lag a poll behind the status poll's `mode` reading
since they're two independent async calls (same root class of issue as
the original 1.17.0 charging-override work, now actually fully closed).
Fix: read `charge_state` directly from the *same* status-poll response
`mode` came from (confirmed present there in the same raw dump that
revealed `mode`), instead of the cross-poll device attribute. Verified via
simulation against the real payload shape.

## ~~16. Add fault code 2102 to the default ignore list~~ — DONE (1.18.2)

First seen right after the sweep-continuation fix went live and actually
worked (Kitchen cleaned automatically for the first time). Confirmed twice
now, both times firing right as the vacuum returned to charge after a room
finished, no visible problem either time -- same pattern as 2103/2105.
Added to the default `ignoredFaultCodes` value. `2101` stays off the list
on purpose (see existing note) -- it was tied to a genuine low-battery
recharge-and-resume cycle, which may carry real information rather than
being purely benign like 2102/2103/2105.

Note for existing installs: changing the code's `defaultValue` only affects
*new* app installs -- an already-configured instance keeps whatever's
already saved in the "Fault codes to treat as normal" field regardless of
this change, and needs `2102` added by hand to pick it up.

## ~~15. Groovy Truth bug in 1.18.0's own mode extraction~~ — DONE (1.18.1)

1.18.0 shipped, user re-imported (confirmed 1.18.0 in the code editor),
refreshed -- `status` was *still* stuck on "Cleaning". Fresh logs revealed
why: the brand new 1.17.1 "no mode" warning was firing on every single
poll, even though the pasted `heartBeat` dump clearly had `mode:0` right
there in it. Root cause: `statusData?.heartBeat?.mode ?: statusData?.eventFlag?.mode`
-- Groovy Truth treats `0` as falsy, so a genuine `mode:0` ("Idle") got
discarded by `?:` and fell through to `eventFlag.mode`, which doesn't even
exist as a key in that response, landing on `null` and triggering the "no
mode" warning for perfectly valid data. This is the exact same bug class as
the earlier `?.foo?[bar]` parser trap and the false-positive `code != "1"`
string/int comparison bug from earlier in this project -- a Groovy
truthiness/type gotcha, not a logic error. Fixed with explicit `if (x ==
null)` checks instead of `?:`. Verified with a standalone simulation using
the actual live payload shape (`mode:0` correctly extracts as `0`, not
`null`). Swept the rest of the file for the same `a?.x ?: b?.x` pattern --
no other instances found.

## ~~14. status frozen on "Cleaning" for hours~~ — ROOT-CAUSED AND FIXED (1.18.0)

After 1.17.0 shipped (charging overrides a conflicting "Cleaning" status),
live logs still showed `status=Cleaning` unchanged for 2.5+ hours straight
(11:20am - 1:13pm, confirmed 1.17.0 was actually loaded), even though the
Wyze app itself showed the vacuum fully charged/docked/idle the whole time.
Notably, the existing unconditional `log.info ".. vacuum_work_status=... ->
status=..."` line -- which should print every single poll if
`handleVacuumStatusResponse` runs to completion -- never once appeared in
~130 lines of pasted logs spanning that whole window, while the *separate*
props poll (fault_code logging, battery draining 100%->86%) fired
correctly every single minute without fail.

Leading theory: the status poll's response is coming back without
`vacuum_work_status` under some condition (e.g. once idle for a while, or a
changed response shape), hitting the silent `if (newStatus == null) return`
at the very top of `handleVacuumStatusResponse` -- before the log.info
line, before the charging-override fix, before transition detection, all
of it. That would explain every symptom at once: status frozen, no
notifications ever, sweep never continuing -- while props polling looks
completely normal, making it look like "everything's polling but nothing
updates."

1.17.1's diagnostic (`log.warn` with the raw response shape) confirmed it
immediately on the next poll: `heartBeat` really has no `vacuum_work_status`
field at all, only `mode`/`charge_state`/`battery`/etc -- the exact same
shape as the props poll. `vacuum_work_status` isn't stale or occasionally
missing, it's simply never sent by this endpoint for this vacuum. The
entire `status` pipeline had been silently no-op'ing since day one whenever
this code path ran (which, per the log evidence, was apparently *always*).

Fixed in 1.18.0 by deriving `status` from `mode` instead -- a field that
genuinely is present in both the props and status polls, using the same
code groups as the existing `mode` attribute's own mapping (`vacuumModeDescription`,
already cross-validated once live: mode 11 during an actual low-battery
recharge cycle), collapsed to the coarse states the rest of the app needs.
`mode`'s "idle" group doesn't distinguish parked-on-the-dock from genuinely
off it, so `charging` (a direct boolean) breaks that tie -- this subsumes
and replaces the narrower charging-override added in 1.17.0 for item #13
below, which was patching a symptom of this same root cause rather than
the cause itself. `vacuumStatusDescription()` (the old, now-provably-wrong
mapping) and the `vacuum_work_status` field references are removed
entirely rather than left as dead/misleading code.

Verified via a standalone simulation using the actual live data point
(`mode=0, charging=true -> Docked`, matching the user's confirmed real
state: fully charged, docked, idle in the Wyze app) plus several other
mode codes.

## ~~13. status vs. charging disagreement blocking sweep continuation~~ — DONE (1.17.0), superseded by #14

Real bug, confirmed live via device page screenshot: `status: Cleaning` while
`mode: Idle` and `charging: true` -- the vacuum had actually finished and
was sitting on the dock, per the user's direct observation ("the cleaning
finished, it's now at home charging"). User: "Is it cleaning or is it
charging?" and directly connected this to the sweep never advancing
("switch is still 'on' and there are more rooms to clean").

Confirmed root cause: `status` and `charging` come from two *separate*
async polls that can land moments apart, and `status`'s underlying mapping
is unverified third-party data to begin with -- this is the same class of
mismatch flagged earlier in the session ("Returning to charge" while
charging:true), now observed a second time with "Cleaning" instead. Since a
vacuum can't physically charge and clean simultaneously, `charging` (a
direct boolean reading) is trusted over `status` (a translated, unverified
code) whenever they conflict: `handleVacuumStatusResponse` now overrides a
"Cleaning" reading to "Docked" if the most recently known `charging` value
is `true`, *before* that status is used for anything -- the displayed
attribute, `state.lastKnownStatus`, notifications, room crediting, sweep
continuation, and poll-interval switching. This directly unblocks the sweep
continuation added in 1.14.0, which was silently stuck waiting on a
transition that this exact mismatch was preventing from ever being detected.

Known trade-off, accepted rather than engineered around: `charging` is
itself up to ~1 poll cycle stale (same async-lag reason), so there's a
narrow theoretical window right as a resumed clean starts where a
just-turned-stale `charging:true` could wrongly override a correct new
"Cleaning" reading. Self-corrects within one more fast-poll cycle; not
worth more machinery for a rare, self-healing edge case.

Also reported alongside this: a new fault code, 2102, not yet on the
ignored-codes list (2103/2105 are). Not silently suppressed -- no evidence
yet on what it means, and it arrived right as the vacuum returned to
charge, so it may belong in the same benign-charging-status family as
2101/2103/2105, or may not. Logged with full context as usual
(`fault_code=2102 ... mode=... chargeState=... status=... battery=...`) for
the user to build evidence from before adding it to the ignore list.

## ~~12. Mark-cleaned picker never clears + bin-hours correction~~ — DONE (1.16.0)

User asked how the "Mark rooms as cleaned" picker gets emptied out -- answer
was "it doesn't," a real gap: `markCleanRooms_${mac}` was never cleared
after `btnMarkCleaned_` applied it, so it sat looking selected indefinitely.
Fixed with `app.removeSetting(...)` after applying.

Also reported `hoursSinceEmptied` reading 0.0 despite real cleaning having
happened -- directly explained by the 1.15.0 poll-mode-flapping bug fixed
just before this: `accumulateBinHours()` only runs from
`handleCleaningSessionEnd`, which only runs when a Cleaning->non-Cleaning
transition is actually detected, so every session whose transition got
missed also silently skipped its bin-hour contribution. Going forward this
self-resolves with the 1.15.0 fix, but the already-lost hours can't be
recovered, so added a "Set cumulative hours to" field + button (Bin
Reminder section) to correct the running total directly instead of only
being able to reset it to zero.

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
