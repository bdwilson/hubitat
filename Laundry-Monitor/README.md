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
| Stop after N sequential low readings | 2 | How many consecutive readings below the stop threshold are needed before ending the cycle. |
| Also require N continuous minutes below threshold | 0 (off) | Extra debounce on top of the reading count, if you want it. |
| Ignore readings above (spike filter) | 1500W | A single reading this high or higher is treated as sensor noise and never starts a new cycle. |
| Deadman timer | 90 min | Hard cap - force-ends a cycle that's been "on" this long, in case a real stop never gets detected. |

Dryer vibration thresholds
---
| Setting | Default | What it does |
|---|---|---|
| Require N 'active' reports within the confirmation window | 3 | How many `active` reports have to arrive within the window below before a cycle is confirmed started. Filters out spurious vibration blips (a bump, nearby footsteps/HVAC) that don't repeat enough - see below. Set to 1 to start instantly on the first report, like the original behavior. |
| Start confirmation window | 10 min | The window the above reports have to fall within. |
| Stop after N sequential inactive reports | 2 | How many consecutive `inactive` reports are needed before ending the cycle, once a cycle has been confirmed started. |
| Deadman timer | 90 min | Same idea as the washer's - force-ends a cycle that's run this long without a confirmed stop. |

**Why the start needs confirming:** a vibration sensor doesn't just report your dryer - it reports anything that shakes it a little, even once. A real cycle on this kind of sensor tends to report in a burst of several active/inactive toggles right when it starts, then goes quiet for long stretches (confirmed from a month of real logs: gaps of 20-50 minutes with no reports at all mid-cycle are normal). A single isolated `active` report - or even two, a couple minutes apart - with no further follow-up is indistinguishable from noise and, before this setting existed, was enough on its own to declare a cycle "started": firing a start notification and, since nothing ever confirmed a stop, eventually getting force-closed by the deadman timer and logged as a fake completed cycle. Two real-world false positives on the same hardware (a single 14-second blip, and later a pair of 14-second blips ~2.5 minutes apart) both topped out at 2 reports and then went permanently silent, while the one confirmed real cycle in the original log data showed 3 toggles in its opening burst - hence requiring 3. Raising "Require N reports" fixes this without meaningfully delaying real starts, since real ones burst within the first minute or two anyway. The raw reading is still logged either way (with no `Started`/cycle-log entry created for an unconfirmed blip), so you can always see what the sensor actually reported - and if a triple-blip false positive ever shows up in your own data, raise it again.

Interesting side note if you ever look at the raw log closely: every one of those false-positive `active` reports has lasted exactly 14 seconds before flipping back to `inactive`, on every occurrence, days apart. That's suspiciously exact for a random bump, and points at a fixed auto-revert timeout in the sensor/driver itself (some vibration sensor drivers force `inactive` a fixed N seconds after any trigger, regardless of whether the underlying vibration is still happening) rather than 14 seconds of real shaking each time. If your sensor's driver has a configurable "reset"/"auto-revert" delay, that's worth a look, but it doesn't change anything about how this app should behave - it just explains why report *count*, not report *duration*, is the signal worth trusting here.

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
  (`concurrent`). Capped separately (default 300).

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
  dryer, use the washer's power-threshold settings as a model.

Credits
---
Detection approach based on [Better Laundry
Monitor](https://github.com/HubitatCommunity/Hubitat-BetterLaundryMonitor)
by Kevin Tierney, ChrisUthe, C Steele, and Barry Burke.
