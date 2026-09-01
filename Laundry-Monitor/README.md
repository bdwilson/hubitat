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
| Stop after N sequential inactive reports | 2 | How many consecutive `inactive` reports are needed before ending the cycle. A cycle starts immediately on the first `active` report. |
| Deadman timer | 90 min | Same idea as the washer's - force-ends a cycle that's run this long without a confirmed stop. |

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
starts or extends a dryer cycle. An optional grace period extends that
suppression for a few minutes after the washer itself stops, since
vibration can linger briefly.

This is a mitigation, not a fix - if your two machines share a wall you'll
still see occasional real dryer loads run *alongside* a washer load
(rather than fully contained inside it), which this can't and shouldn't
suppress. If cross-talk is frequent enough to matter, the more reliable
long-term fix is remounting the vibration sensor further from the washer,
or moving the dryer to power monitoring too.

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
  peak washer power, and how the cycle ended (`normal`, `deadman`, or
  `manual reset`). Capped separately (default 300).

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
- Cross-talk suppression only helps when the dryer cycle is fully inside a
  washer session; genuinely overlapping (but distinct) loads aren't
  filtered, by design.
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
