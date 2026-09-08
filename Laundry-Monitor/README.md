Laundry Monitor & Logger
---
A small Hubitat app that watches a washer (via a power meter) and a dryer
(via a vibration/acceleration sensor - no power monitoring needed on the
dryer) and figures out when each machine starts and stops. It's built
around one idea: **every raw reading gets saved**, so the start/stop
thresholds can be re-tuned later from real data instead of guesswork.

It's based on the community [Better Laundry
Monitor](https://github.com/HubitatCommunity/Hubitat-BetterLaundryMonitor)
app's power-threshold detection logic (wait-before-counting window,
sequential/continuous-minutes end debounce, deadman timer), but trimmed
down to exactly this one
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
| Minimum continuous active time | 3 min | The sensor has to stay continuously `active` this long before it counts as a real cycle. Anything shorter is treated as a bump/handling and ignored entirely - no notification, no cycle logged. |
| Deadman timer | 120 min | Pure safety net, for a sensor that dies mid-cycle and never reports `inactive`. Not part of normal operation any more. |

**How dryer detection works (and why it's this simple):** this sensor
latches `active` for as long as it keeps feeling vibration, and only
reports `inactive` once the shaking actually stops. So the length of a
continuous active span is a near-perfect signal, and it separates
*cleanly*. Measured across two days of real logged data - 71 active spans
in total:

| | count | span length |
|---|---|---|
| bumps, door slams, unloading, washer cross-talk | 66 | 10-68 **seconds** |
| real dryer cycles | 5 | 28.7-65.8 **minutes** |

There is nothing in between - a 25x gap. So "did the vibration last more
than a few minutes?" answers the question outright, and the cycle end is
exact (the `inactive` report *is* the moment the dryer stopped), with no
debounce, quiet-timeout, or deadman guesswork involved. Replayed against
that dataset, this catches 5 of 5 real cycles - each matching the
household's own written record of when it started and finished, to within
about a minute - and produces zero false positives from the 66 blips.

The only cost is that a start is reported a few minutes late (however
long "minimum continuous active time" is set to), which is a small price
for never being told the dryer started because somebody bumped it.

**A note on what this replaced,** since the earlier approach is a good
cautionary tale. Before this, the app tried to identify cycles by
*counting* vibration reports in a window and then requiring a second,
separate burst to confirm - built on the assumption that report duration
carried no information, because the first few false positives examined all
happened to last exactly 14 seconds (read at the time as a fixed
auto-revert timeout in the driver). That was backwards: 14 seconds is the
*minimum* hold for a brief trigger, and sustained vibration keeps the
sensor latched far longer. Counting reports could never work - handling
the dryer produces just as many toggles as starting it, sometimes more -
and that approach missed all 5 real cycles in the same dataset while
confirming 2 false ones. Duration was the signal the whole time.

Washer/dryer cross-talk
---
Largely a solved problem now, and the dedicated setting defaults to
**off**. Washer vibration bleeding into the dryer sensor only ever
produced short active spans (68 seconds at the very worst) in real data,
so the minimum-run-time check filters it as a side effect of how it
works.

This matters because the old suppression logic was actively harmful: this
household routinely runs the dryer and washer at the same time (a load
goes in the dryer, the next load goes in the washer), and suppressing
dryer vibration whenever the washer was running caused real dryer cycles
to be missed outright. The setting is still there if your sensor somehow
latches `active` from washer vibration alone, but leave it off unless the
Data Log shows you need it.

These defaults came from reviewing about a month of real washer power
readings and dryer vibration reports against the previous Node-RED-based
setup's actual behavior - not generic guesses. If you're starting from
different hardware, they're still a reasonable starting point, but watch
the Data Log for the first few weeks and adjust from there.

Running both machines at once (second load in progress)
---
A common real sequence: washer finishes -> clothes go in the dryer -> the
washer gets started again for a second load while the dryer is still
running the first. This household does it constantly, and it's handled:

- Dryer detection is based purely on how long its own sensor stays
  vibrating, so a washer running at the same time is simply irrelevant to
  it (and cross-talk suppression, which used to break exactly this case,
  now defaults off).
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
- Dryer starts are reported a few minutes late by design - however long
  "minimum continuous active time" is set to - because that wait is what
  proves the vibration is a real cycle and not someone bumping the machine.
  Ends are exact.
- If your dryer sensor behaves differently from this one (i.e. it does
  *not* latch `active` through sustained vibration, but instead
  auto-reverts to `inactive` after a fixed few seconds regardless), this
  duration-based approach won't work as-is and there is no setting that
  rescues it. Check the raw log: if you never see an active span longer
  than a minute even during a known dryer run, that's the tell.
- The deadman timer is a hard cap on cycle length. Real cycles here have
  run up to 66 minutes, so the 120-minute default leaves plenty of room -
  but if you set it near your real cycle length it will truncate them.
  (A previous 66-minute setting would have cut a real 65.8-minute cycle
  short by seconds.)
- There's no dryer power monitoring option in this app (it assumes
  vibration-only on the dryer). It's no longer needed for reliability -
  the vibration approach now matches the household's own record of every
  cycle - but if you add a power meter later, use the washer's
  power-threshold settings as a model.
- A correction worth recording: an earlier version of this README claimed
  this sensor "goes silent for 45-60+ minutes in the middle of real
  cycles." That was a misreading of the same data. It wasn't silent - it
  was *latched active* for those 45-60 minutes, which is exactly the
  signal the current detection relies on. If you're re-tuning from the
  raw log later, read active/inactive as spans, not as isolated events.

Credits
---
Detection approach based on [Better Laundry
Monitor](https://github.com/HubitatCommunity/Hubitat-BetterLaundryMonitor)
by Kevin Tierney, ChrisUthe, C Steele, and Barry Burke.
