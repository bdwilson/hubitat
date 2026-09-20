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
| Wait before counting the power threshold | 4 min | How long power has to stay above the start threshold before a cycle is confirmed started. Set higher if your washer has a pre-wash soak that dips back to idle power for a few minutes. |
| Start threshold | 5W | Power level a reading has to reach to be considered "the washer turned on." |
| Minimum minutes before end detection | 10 min | Ignore drops below the stop threshold until the cycle has been running at least this long (covers fill/pause dips early in a cycle). |
| Stop threshold | 3W | Power level a reading has to drop below to be considered "the washer might be done." |
| Stop after N sequential low readings | 2 | Fast path: how many consecutive readings below the stop threshold are needed before ending the cycle immediately. Only fires if the meter actually keeps reporting - see below. |
| Also require N continuous minutes below threshold | 0 (off) | Extra debounce on top of the reading count, if you want it. |
| Quiet-timeout confirmation | 10 min | Backstop: also ends the cycle after this many minutes with no reading back above the stop threshold, even if a second low reading never arrives. See below - this is the important one. |
| Shorter confirmation late in a load | 4 min | Used instead of the 10 minutes above once the wash has already run about as long as a normal load for your machine. See "Why the stop timeout adapts" below. |
| Ignore readings above (spike filter) | 1500W | A single reading this high or higher is treated as sensor noise and never starts a new cycle. |
| Deadman timer | 90 min | Hard cap - force-ends a cycle that's been "on" this long, in case a real stop never gets detected. |

**Why the start wait is 4 minutes, not 2:** idle standby draw isn't perfectly flat - it bounces up to 8-14W in brief, isolated blips fairly often overnight, and the start threshold is only 5W. A real overnight false alarm traced back to exactly this: three such blips landed back-to-back with no dip between them, spanning almost precisely 2 minutes by coincidence, and the wash "started" while nobody was home. Measured across several nights of real data, that was the single worst overnight noise streak found (3.0 minutes); everything else topped out at 1.5. Four minutes clears all of it with better than 2x margin. The cost is a real start notification landing a couple of minutes later - the logged start time itself is unaffected, since it's always taken from the first qualifying reading, not from whenever confirmation happens.

**Why there are two ways to detect a stop:** a lot of power meters only report a new value when it *changes*. Once your washer settles at a genuinely stable idle wattage, it may never send another event at all - which means "stop after 2 sequential low readings" can silently wait forever for a second reading that's never coming, and the cycle only ever ends via the 90-minute deadman timer, 40+ minutes after the wash actually finished. Confirmed on real data: a wash that visibly finished at 10:36am (last high reading, then one 2W reading, then total silence for the next 2h45m) sat "on" until the deadman forced it closed at 11:20am. The quiet-timeout setting fixes this: once the *first* low reading arrives, it schedules its own check independent of whether anything else ever reports, and ends the cycle using that first low reading's timestamp as the true end time (so the logged duration reflects when the wash actually stopped, not when the timeout happened to fire). The two mechanisms race - whichever confirms first wins - so a chatty meter still gets the fast 2-reading path, and a quiet one still gets a correct, reasonably prompt stop instead of a 90-minute wait.

There's a third way a pending stop gets confirmed, faster than either: if the dryer starts for real while the washer has a stop pending, that's strong independent evidence the load actually just finished, so the washer's "done" is confirmed immediately rather than waiting out the rest of the quiet-timeout. Caught on real data: a wash with only one low reading (no second one to satisfy the fast path) sat pending for the full 10 minutes before "Washer is done" fired - by then the dryer had already been running for two minutes, so the notification arrived after the fact, and worse, the 15-minutes-later reminder to move the load fired anyway even though it was already in the dryer. Both are fixed: the dryer starting now resolves the pending washer stop on the spot, and the reminder checks whether the dryer has started before nagging about it, not just whether the washer restarted.

**Why the stop timeout adapts to your machine:** a washer dropping to idle
power mid-load looks *identical* to one that has actually finished - same
2W reading, same length of quiet. Measured on real data, a genuine
mid-cycle pause and a genuine gap between two back-to-back loads were both
exactly 4.5 minutes. No single timeout can separate them.

What does separate them is *when in the load* the dip happens. Across every
gap recorded so far:

| | when it happened | what it was |
|---|---|---|
| 27 min into a ~47 min load | early | mid-cycle pause (kept running) |
| 27 min into a ~47 min load | early | mid-cycle pause (kept running) |
| 46 min in | late | end of the load, next one started 4.5 min later |
| 52 min in | late | end of the load, next one started 7.5 min later |

So the app learns the median length of your recent normally-ended washes
and uses the long 10-minute confirmation for dips arriving before ~80% of
that, and the short one after. A dip early in a wash still has to go quiet
for a full 10 minutes to count as the end; one arriving after a normal
load's worth of runtime only needs 4. The in-between gaps that showed up
late in real loads were all 3 minutes or shorter, so 4 clears them.

Without this, two loads run back to back merge into a single cycle that
only ends when the 90-minute deadman fires - which then immediately
"restarts" on the still-running second load, producing a false start, a
false "washer done" and a bogus "washer started again while the dryer is
running" all in a row. It needs at least three completed washes before it
has enough history to adapt; until then it always uses the long timeout.

Dryer vibration thresholds
---
| Setting | Default | What it does |
|---|---|---|
| Minimum continuous active time | 6 min | The sensor has to stay continuously `active` this long before it counts as a real cycle. Anything shorter is treated as a bump/handling and ignored entirely - no notification, no cycle logged. |
| Deadman timer | 120 min | Pure safety net, for a sensor that dies mid-cycle and never reports `inactive`. Not part of normal operation any more. |

**How dryer detection works (and why it's this simple):** this sensor
latches `active` for as long as it keeps feeling vibration, and only
reports `inactive` once the shaking actually stops. So the length of a
continuous active span is a near-perfect signal, and it separates
*cleanly*. Measured across two days of real logged data - 71 active spans
in total:

| | count | span length |
|---|---|---|
| bumps, door slams, unloading, washer cross-talk | 80+ | 1 second - 3m50s |
| real dryer cycles | 7 | 28.7-65.8 **minutes** |

There is nothing in between - a 7x gap. (The upper end of that noise range
came later, from someone loading the dryer: a 3m50s continuous burst that
sailed past the original 3-minute threshold and logged a fake 4-minute
"cycle". Every other non-cycle burst on record is under 70 seconds, so 6
minutes clears the outlier comfortably and still sits far below any real
run.) So "did the vibration last more
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

Upgrading an existing install
---
Paste the new code over the old in **Apps Code**, then open the app and
hit **Done** once. Pasting new code alone is not enough to move an
existing install onto new defaults - Hubitat keeps settings whose input
has been removed, and a changed `defaultValue` only ever applies to a
setting that has never been set. So the app migrates its own settings on
first run after an update, and logs exactly what it changed.

Each version bump below is one-time and cumulative - updating from any
older version applies every change up through the current one. Anything
you set afterwards is respected.

| Version | Change | Why |
|---|---|---|
| v2 | retires the six settings that drove the old dryer report-counting logic | Replaced by duration-based detection - see "Dryer vibration thresholds" above. |
| v2 | `dryerDeadmanMin` -> 120 (if lower) | It is only a safety net now. A real 65.8-minute cycle has been observed, so a cap near an hour truncates real cycles. |
| v2 | `suppressCrossTalk` -> off | Cross-talk only ever produces short active spans, which the minimum run time already filters. Leaving it on only risks missing real dryer cycles that overlap a washer load. |
| v3 | `washerStartWaitMin` -> 4 (if lower) | A real overnight false start traced to idle noise landing almost exactly on the old 2-minute boundary - see "Why the start wait is 4 minutes" above. |
| v4 | `dryerMinRunMin` -> 6 (if lower) | Loading the dryer produced a 3m50s continuous burst that the old 3-minute rule scored as a real cycle. |
| v4 | `washerStopConfirmLateMin` -> 4 | Enables the adaptive stop timeout so back-to-back loads stop merging - see "Why the stop timeout adapts" above. |
| v5 | `feedbackLinkStyle` -> `plain` | Superseded by v6 the same day; see below. |
| v6 | `feedbackLinkStyle` -> `auto` | Superseded by v7; the marker it added was not understood by the installed driver. |
| v7 | `feedbackLinkStyle` -> `plain` (from `auto` or unset) | Plain URLs need no cooperation from any driver. The `[HTML]` marker is still selectable, but as a deliberate choice. |

### Only one message per start

The second-load alert ("Washer started again - the dryer is still running
the previous load") replaces the plain "Washer started" rather than
arriving next to it - it already says the washer started, so both together
is the same news twice. Turn the second-load alert off and a second load
just reports as an ordinary start.

Feedback (optional, off by default)
---
Everything this app knows about your machines it learned from its own
output - which means a wrong call quietly teaches it the wrong thing. The
feedback loop fixes that by capturing whether each alert was actually
right, at the one moment you know the answer.

Turn on **Add feedback links to notifications** and each push gains:

```
Washer is done

Was this correct?
Yes: https://cloud.hubitat.com/api/<hub>/apps/<id>/f/142/y?access_token=<token>
No:  https://cloud.hubitat.com/api/<hub>/apps/<id>/f/142/n?access_token=<token>
```

**Yes** is a single tap and needs nothing else - the link records the
answer and returns a page saying so. **No** records it and then offers an
optional note - "nothing was running, I was just emptying the dryer" is
exactly the kind of label that no threshold could have inferred. Ignoring
the question entirely is treated as *nothing said*, not as a yes. The Yes
page also carries an "actually, that one was wrong" link, so a mis-tap
takes one more tap to correct rather than being stuck.

### How the links are formatted

**Plain text URLs** is the default, and is the only option that depends on
nothing but the push client: every one of them turns a bare URL into a
tappable link on its own.

| Setting | What each notifier receives |
| --- | --- |
| **Plain text URLs** | Bare URLs everywhere. Less tidy, universally works. |
| **Pushover `[HTML]` marker** | Pushover devices get `[HTML]` + anchor tags; anything else still gets plain URLs. |
| **Raw HTML** | Anchor tags, no marker - only for a notifier you have confirmed renders HTML unprompted. |

The two tidy options are worth understanding before picking one. Pushover
renders HTML only when the sender sets the API's `html=1` flag, and [Dan
Ogorchock's Pushover driver](https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/pushover-notifications.src/pushover-notifications.groovy)
- the one nearly everyone uses - sets that flag only when the message
carries a literal `[HTML]` marker, a feature added 2020-09-23. Send that
marker to an **older** driver and it has no idea what it means, so it ends
up in the message you receive:

```
[HTML]Washer started<br><br>Was this correct? <a href="https://...">Yes</a>
```

That is the tell: `[HTML]` visible in the notification means the marker
reached a driver too old to strip it. Update the driver from **Drivers
Code**, or go back to plain text. The settings page lists the driver type
name behind each of your notifier devices so you can see what you actually
have installed.

Nothing needs changing inside the Pushover driver either way - the marker
is a documented input, not a missing feature.

Setup, once:

1. **Apps Code** → this app → **OAuth** → Enable OAuth in App. (Hubitat
   only allows this from that screen; the app can't do it for you.)
2. Back in the app, enable the feedback setting and pick **cloud** or
   **local** URLs. Cloud is the default and works when you're away from
   home - local links only resolve on your own network, which is exactly
   when you're least likely to be there to answer.

The settings page prints both URL forms so you can see what's being sent.

**What gets stored.** Every answer is saved against the specific event it
refers to - including the reminder and second-load alerts, which have no
cycle-log entry of their own. Alongside it the app pins a snapshot of the
raw readings from 15 minutes before the event onward, because a label is
worthless once the readings behind it roll out of the capped raw log
(~12 days). Twenty such snapshots are kept, roughly 25KB.

**Getting it back out.** The Data Log page gains a **Feedback Log** CSV
that joins the label, the notification, and the cycle it came from, with
the pinned raw readings underneath it:

```
eventId,notifiedAt,answeredAt,kind,device,correct,cycleAt,durationMin,peakW,reason,note
142,2026-09-17 13:33:31,2026-09-17 13:41:02,done,washer,0,2026-09-17 13:30:34,47,241,normal,"was just emptying it"
```

That is deliberately everything needed to replay a disputed event offline
and work out which threshold was responsible.

**A note on the token.** The link carries an OAuth token, so it passes
through Pushover and sits in your notification history. The endpoint it
unlocks only ever writes feedback - it cannot read your logs, change
settings, or control any device. Notes are capped at 500 characters and
escaped before they're ever shown back.

**What this does not do yet.** Nothing adjusts itself. Labels accumulate
and thresholds stay exactly where you set them. Automatic re-tuning from
the labelled set is a separate piece of work, deliberately kept apart from
collection so that a single mistaken "No" can't move the app's behaviour
on its own.

Learned profile and the sanity gate
---
The three logs together answer one question the thresholds alone cannot:
*does this event look like a real load on this machine?* From the cycle
log the app keeps a running profile - median washer duration, median
washer **peak power**, median dryer duration - and uses it in two places.

**What counts as a sample.** Any cycle that ended normally - a deadman or
manual-reset cycle never counts, and neither does one the app itself
doubted, nor one you marked wrong. Giving feedback is *not* required for a
cycle to count: silence means included, matching the rule that no answer
implies the alert was fine. The practical consequence is that a bad call
you never correct still teaches the profile, so the corrections are worth
giving when something is obviously wrong.

**It excludes anything you marked wrong.** This matters more than it
sounds. Before feedback existed, the app learned from its own output, so a
bad call quietly taught it the wrong thing: one merged 90-minute blob
pulls the median from 47 to 53 minutes, which shifts the adaptive stop
timeout, which makes the next call worse. Marking that event "No" now
removes it from the profile entirely and the median snaps back.

**The sanity gate.** A finished cycle that looks nothing like a real load
gets logged with a `doubt` note but is *not* announced, and does not start
a reminder. It deliberately gates on only the two signals with enormous
measured separation:

| Signal | Real cycles | Known false positive | Gate |
|---|---|---|---|
| Washer peak power | never below 71% of median | overnight phantom hit **2%** | below 25% |
| Dryer duration | never below 56% of median | loading the dryer hit **8%** | below 35% |

Peak power is the strongest washer signal available and nothing was using
it: a real load pulls 200-490W, while the overnight phantom that woke the
house topped out at 11W. Both gates need at least five clean samples
before they do anything, so a new install is never second-guessed by an
empty profile.

Washer *duration* is deliberately **not** gated - a short delicates load is
legitimately short, and there is no safe floor.

Tuning Report
---
**Main page → Tuning Report** builds a single pasteable prompt containing
the current settings, the learned profile, recent detected cycles, every
correction you have given with its note, and the raw sensor readings
around each event you marked wrong. Drop it into any LLM and it has the
whole picture without you explaining any of it.

It asks for three things specifically: which setting caused each bad call,
what to change it to *with the margin shown against every real cycle*, and
an explicit callout of any change that trades one error for another - the
failure mode that has bitten this app more than once.

Nothing in it changes settings. It produces a recommendation you apply
yourself, which is the point: a single mistaken "No" can never move the
app's behaviour on its own.

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

**Can the cycle log be rebuilt from the raw log?** Yes - for as long as
the raw log still reaches back that far. Every cycle in the summary log is
derived from the raw readings, so replaying the raw log reproduces
starts, ends, durations and peak power exactly. (That is how the current
detection logic was validated: the entire two-day dataset was replayed
offline and checked against a written record of what actually ran.) Two
caveats: a replay reflects your *current* settings, not the ones in force
at the time, and it can only cover the window the raw log still holds.

That window is the thing to watch, because the two logs are capped
independently and the raw log fills far faster:

| Log | Default cap | At ~250 readings/day | Approx. state size |
|---|---|---|---|
| Raw readings | 3000 | ~12 days | ~120 KB |
| Cycle summaries | 300 | ~27 days | ~15 KB |

So the cycle log currently outlives the raw log by more than double - past
about 12 days the summaries are the *only* record, and can no longer be
rebuilt. Raising the raw cap buys history at a real cost: app state is
held in the hub's database and kept in memory, and a few hundred KB of it
is enough to slow a hub down, so going much past the current 3000 is not
free. If you want a long archive, the better move is to export the raw CSV
periodically and keep it off-hub, rather than raising the cap.

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
