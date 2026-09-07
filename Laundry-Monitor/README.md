Laundry Monitor & Logger
---
A small Hubitat app that watches a washer (via a power meter) and a dryer
(via a vibration/acceleration sensor - no power monitoring needed on the
dryer) and figures out when each machine starts and stops. It's built
around one idea: **every raw reading gets saved**, so the start/stop
thresholds can be re-tuned later from real data instead of guesswork.

It's based on the community [Better Laundry
Monitor](https://github.com/HubitatCommunity/Hubitat-BetterLaundryMonitor)
app's detection logic (power thresholds with a wait-before-counting window,
sequential/continuous-minutes end debounce, deadman timer; vibration with
sequential-inactive-reading debounce), but trimmed down to exactly this one
washer + one dryer setup, with all optional notification "flows" off by
default, and with its own persistent, exportable data log instead of the
transient in-memory calibration stats the community app keeps.

Why not just use Better Laundry Monitor directly? Nothing wrong with it -
but a 30-day log review of an existing Node-RED-based setup using the same
kind of thresholds surfaced a few things worth fixing at the same time:
a washer/dryer vibration cross-talk problem (see below), and a couple of
deadman-timer edge cases that only show up from watching real logs over
time. This app folds those fixes in and defaults its thresholds to values
tuned from that review, and keeps logging every reading going forward so
the next round of tuning doesn't require trawling through hub or Node-RED
debug logs again.

Devices
---
- **Washer**: any device exposing `capability.powerMeter` (a smart plug or
  in-line power monitor works well).
- **Dryer**: any device exposing `capability.accelerationSensor` (reports
  `active`/`inactive`) - typically a vibration sensor stuck to the cabinet.

Install
---
1. In Hubitat, go to **Apps Code** → **New App**, paste in
   [`LaundryMonitor-App.groovy`](https://raw.githubusercontent.com/bdwilson/hubitat/claude/laundry-monitor-calibration-53grlv/Laundry-Monitor/LaundryMonitor-App.groovy),
   and Save.
2. Go to **Apps** → **Add User App** → **Laundry Monitor & Logger**.
3. Pick your washer's power meter and your dryer's vibration sensor.
4. Leave the thresholds at their defaults to start (see below for what they
   mean and where they came from), or adjust to fit your own hardware.
5. Everything under **Notifications** is off by default - turn on what you
   want once you're happy the start/stop detection looks right in the Data
   Log.

Washer power thresholds
---
| Setting | Default | What it does |
|---|---|---|
| Wait before counting the power threshold | 2 min | How long power has to stay above the start threshold before a cycle is confirmed started. Set higher if your washer has a pre-wash soak that dips back to idle power for a few minutes. |
| Start threshold | 5W | Power level a reading has to reach to be considered "the washer turned on." |
| Minimum minutes before end detection | 10 min | Ignore drops below the stop threshold until the cycle has been running at least this long (covers fill/pause dips early in a cycle). |
| Stop threshold | 3W | Power level a reading has to drop below to be considered "the washer might be done." |
| Stop after N sequential low readings | 2 | Fast path: how many consecutive readings below the stop threshold are needed before ending the cycle immediately. Only fires if the meter actually keeps reporting - see below. |
| Also require N continuous minutes below threshold | 0 (off) | Extra debounce on top of the reading count, if you want it. |
| Quiet-timeout confirmation | 10 min | Backstop: also ends the cycle after this many minutes with no reading back above the stop threshold, even if a second low reading never arrives. See below - this is the important one. |
| Ignore readings above (spike filter) | 1500W | A single reading this high or higher is treated as sensor noise and never starts a new cycle. |
| Deadman timer | 90 min | Hard cap - force-ends a cycle that's been "on" this long, in case a real stop never gets detected. |

**Why there are two ways to detect a stop:** a lot of power meters only report a new value when it *changes*. Once your washer settles at a genuinely stable idle wattage, it may never send another event at all - which means "stop after 2 sequential low readings" can silently wait forever for a second reading that's never coming, and the cycle only ever ends via the 90-minute deadman timer, 40+ minutes after the wash actually finished. Confirmed on real data: a wash that visibly finished at 10:36am (last high reading, then one 2W reading, then total silence for the next 2h45m) sat "on" until the deadman forced it closed at 11:20am. The quiet-timeout setting fixes this: once the *first* low reading arrives, it schedules its own check independent of whether anything else ever reports, and ends the cycle using that first low reading's timestamp as the true end time (so the logged duration reflects when the wash actually stopped, not when the timeout happened to fire). The two mechanisms race - whichever confirms first wins - so a chatty meter still gets the fast 2-reading path, and a quiet one still gets a correct, reasonably prompt stop instead of a 90-minute wait.

Dryer vibration thresholds
---
| Setting | Default | What it does |
|---|---|---|
| Require N 'active' reports within the candidate-burst window | 3 | How many `active` reports have to arrive within the window below before a vibration burst is even treated as a *candidate* (not yet confirmed) cycle. Filters spurious single/double blips (a bump, nearby footsteps/HVAC). Set to 1 to skip this stage entirely. |
| Candidate burst window | 10 min | The window the above reports have to fall within. |
| Silence needed to count a "separate" later burst | 3 min | How much quiet has to pass before the next report counts as a new, later burst rather than the candidate burst just continuing. |
| Confirm only if a second burst arrives within | 60 min | A candidate burst only becomes a real, confirmed cycle (logged, deadman armed, notification sent) once a *second, separate* burst shows up within this window. One burst that never repeats quietly expires as "unconfirmed" instead - see below, this is the important one for avoiding false "Dryer started" alerts. |
| Stop after N sequential inactive reports | 2 | Fast path, same idea as the washer's - but see the warning below: vibration sensors are typically edge-triggered (report only on active↔inactive transitions), so a second `inactive` report in a row essentially never arrives in practice. Don't rely on this alone. |
| Quiet-timeout confirmation | 30 min | Backstop: also ends the cycle after this many minutes with no further vibration at all. **Read the warning below before changing this** - it's a real trade-off on this hardware, not a simple "shorter is better" dial. |
| Deadman timer | 90 min | Same idea as the washer's - force-ends a cycle that's run this long without a confirmed stop. |

**Important trade-off - read before tuning the dryer's quiet-timeout:** unlike the washer, this vibration sensor has been observed going completely silent for **45-60+ minutes in the middle of a real, still-running cycle** - confirmed independently on two different days (a 48.7-minute gap in the original calibration log, a 47-minute gap and a 61-minute gap on a later day, all inside cycles later confirmed real). That means a short quiet-timeout will sometimes end a cycle that hasn't actually finished yet - it'll just look like two separate shorter cycles instead of one long one. The 30-minute default is a deliberate middle ground: short enough to meaningfully improve on waiting out the full deadman for cycles that really did just finish, but you should expect it to occasionally split a long real cycle in two. Watch the Data Log: if you see a dryer `end`/`start` pair a few minutes apart where you know it was actually one continuous run, raise this setting (or set it to 0 to disable it and rely purely on the deadman, which is what happened before this setting existed). If you mostly see fast, correctly-timed normal stops, leave it or lower it further. There's no vibration-only setting that eliminates this trade-off - the underlying limitation is the sensor, not the app (see "Known limitations" below).

Before this fix, every single dryer cycle observed ended via the deadman timer, none normally - not occasionally, but 100% of the time - because the fast path (2 sequential inactive reports) can basically never be satisfied by an edge-triggered sensor. Worse, because the washer's equivalent bug (above) kept `washerOn` stuck `true` for up to an hour past the real end of a wash, the dryer's washer-cross-talk suppression was blocking genuinely real dryer starts that happened to occur while the washer was (incorrectly) still considered running - a real ~30-minute dryer cycle went completely unlogged this way, with no start or end entry at all. Fixing the washer's stop detection removes that cascade too.

**Why a start needs two separate bursts to confirm, not just enough reports in one burst:** a vibration sensor can't tell "the dryer is running" apart from "someone opened the door, pulled clothes out, and slammed it shut" - physically handling the dryer produces the exact same kind of active/inactive toggle burst as the drum starting up, sometimes even more of them. Report *count* alone can't fix this: one real false alarm on this hardware was a single 14-second blip; another was two 14-second blips ~2.5 minutes apart; but a later one was a full *5* active reports across 3.5 minutes - clearly someone unloading the dryer, immediately followed by total silence for over an hour. Raising the report count to filter that out would also risk delaying or missing real starts, since it's genuinely the same signature.

What actually tells them apart: a real drying cycle keeps producing vibration bursts spread across its *entire* runtime (confirmed from a month of real logs - a burst at the start, then silence, then another burst 10-50 minutes later, and so on until the cycle ends), while handling the dryer is one burst that then goes *permanently* silent. So a candidate burst (enough reports close together to rule out a single spurious blip) is only promoted to a confirmed, real cycle once a **second, separate burst** shows up later - a genuine gap of quiet, then more vibration. If nothing shows up again within the confirmation window, it quietly logs as `unconfirmed` in the cycle log (with no `Started` entry, no deadman armed, and critically **no notification sent**) instead of alerting you that the dryer started when someone was really just unloading it.

The trade-off: a real cycle's *notification* won't fire until that second burst arrives, which (per the observed burst pattern) is usually within the first 10 minutes but can occasionally take up to the confirmation window if the cycle's first quiet gap happens to run long. Given the alternative is a wrong "Dryer started" push every time someone empties the dryer, that trade is worth it - accuracy over speed. The raw reading is still logged immediately either way, so nothing is ever lost, just held back from being called a "start" until it's actually confirmed.

Interesting side note if you ever look at the raw log closely: the very first false-positive blip found on this hardware lasted exactly 14 seconds before flipping back to `inactive`, and so did the next one, days apart. That's suspiciously exact for a random bump, and points at a fixed auto-revert timeout in the sensor/driver itself (some vibration sensor drivers force `inactive` a fixed N seconds after any trigger, regardless of whether the underlying vibration is still happening) rather than 14 seconds of real shaking each time. If your sensor's driver has a configurable "reset"/"auto-revert" delay, that's worth a look, but it doesn't change anything about how this app should behave.

These defaults came from reviewing about a month of real washer power
readings and dryer vibration reports against the previous Node-RED-based
setup's actual behavior - not generic guesses. If you're starting from
different hardware, they're still a reasonable starting point, but watch
the Data Log for the first few weeks and adjust from there.

Washer/dryer cross-talk suppression
---
A vibration sensor mounted on or near a dryer will often also pick up a
washer running nearby (shared wall, floor joist, or just proximity) and
misreport it as a dryer cycle. In one month of logs from a real setup,
roughly a third of the vibration-detected "dryer cycles" were entirely
contained inside a concurrent washer power session - i.e., not the dryer
running at all.

With **Ignore dryer vibration while the washer is actively running**
enabled (on by default), any vibration report that arrives while the
washer is mid-cycle is logged (so you can still see it happened) but never
**starts** a new dryer cycle. An optional grace period extends that
suppression for a few minutes after the washer itself stops, since
vibration can linger briefly.

Crucially, this suppression only ever blocks a dryer cycle from *starting*.
Once the dryer is genuinely running, it is never blocked by washer
activity - see "Running both machines at once" below for why that matters.

This is a mitigation, not a fix - if your two machines share a wall you'll
still see occasional real dryer loads *start* alongside an already-running
washer load, which this can't and shouldn't try to filter (a single
vibration sensor can't tell "washer bleed-through" from "the dryer really
did just start" in that instant). If that's frequent enough to matter, the
more reliable long-term fix is remounting the vibration sensor further from
the washer, or moving the dryer to power monitoring too.

Running both machines at once (second load in progress)
---
A common real sequence: washer finishes → clothes go in the dryer → the
washer gets started again for a second load while the dryer is still
running the first. The app handles this explicitly:

- Cross-talk suppression (above) never applies once the dryer has already
  started, so a second washer load running at the same time cannot blind
  the dryer's own stop detection or delay it until the deadman timer.
- Every cycle-start/end event in the Cycle Summary Log carries a
  `concurrent` column - true if the *other* machine was running at that
  moment. A washer-start row with `concurrent=1` means the dryer still had
  the previous load in it.
- The main page shows **"Both running at once - second load in progress"**
  whenever washer and dryer are on simultaneously.
- Optionally, turn on **Notify when the washer starts again while the dryer
  is still running** (off by default, under Notifications) for a
  heads-up push/speech notification the moment that happens.

Data Log
---
This is the main point of the app. Two logs are kept, both viewable and
exportable as CSV from **View / Export Data Log** on the main page:

- **Raw readings log** - every washer power reading and every dryer
  active/inactive report, with a timestamp and whether it was suppressed as
  cross-talk. Capped at "Max raw readings to retain" (default 3000;
  raise it if you want a longer history, at the cost of a bit more hub
  storage).
- **Cycle summary log** - one row per detected start/end, with duration,
  peak washer power, how the cycle ended (`normal`, `deadman`, or `manual
  reset`), and whether the *other* machine was running at that moment
  (`concurrent`). Capped separately (default 300). The dryer can also log an
  `unconfirmed` row - a vibration burst that looked like it might be a real
  start but never got a confirming second burst (most often someone
  emptying the dryer, not a real cycle) - these never fire a notification
  and don't count as a real cycle, but stay visible here for calibration.

Both logs persist across hub reboots and app setting changes. Use **Clear
Raw Log** / **Clear Cycle Log** to reset them (e.g. after you've exported
and are starting a fresh tuning window).

Manual reset
---
The **Manual Reset** section has buttons to force-end a stuck washer or
dryer cycle without waiting for the deadman timer - handy while testing
threshold changes.

Known limitations
---
- Cross-talk suppression can only prevent a *false start* of the dryer
  while the washer is running; it can't tell a real dryer start apart from
  washer bleed-through in that same instant, so an occasional genuine dryer
  start right as the washer is running will still be suppressed. Once a
  dryer cycle is actually running, though, nothing about the washer can
  block or delay its detection - see "Running both machines at once" above.
- The deadman timer is a hard cap on cycle length. If you regularly run
  loads longer than 90 minutes, raise it - otherwise a legitimately long
  cycle will get force-ended and logged as `deadman` instead of `normal`.
- There's no dryer power monitoring option in this app (this build assumes
  vibration-only on the dryer); if you later add a power meter to the
  dryer, use the washer's power-threshold settings as a model - it doesn't
  have this problem, since power meters report their idle wattage far more
  frequently than a vibration sensor reports "still not moving."
- The dryer's vibration sensor genuinely goes silent for 45-60+ minutes in
  the middle of confirmed real cycles (not a bug - just how sparse this
  sensor's reporting is). That means there's a hard limit on how fast and
  how reliably a stop can be detected from vibration alone; see the
  quiet-timeout trade-off above. Dryer power monitoring is the only way to
  fully remove this limitation.
- A real dryer's *notification* is deliberately held back until a second
  confirming burst arrives (see "Why a start needs two separate bursts"
  above), so if you want a heads-up the instant the sensor first reports
  anything, that's not what this app is optimized for - it's optimized to
  never tell you the dryer started when someone was just unloading it.
- This class of false positive (bumps, door handling) is specific to the
  vibration-based dryer. The washer's power-based detection isn't
  susceptible to it - physically bumping or knocking into a washer doesn't
  move its power draw, so there's nothing analogous to filter there.

Credits
---
Detection approach based on [Better Laundry
Monitor](https://github.com/HubitatCommunity/Hubitat-BetterLaundryMonitor)
by Kevin Tierney, ChrisUthe, C Steele, and Barry Burke.
