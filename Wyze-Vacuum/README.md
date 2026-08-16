# Wyze Vacuum Connect — Hubitat Integration

Native Hubitat integration for the Wyze Robot Vacuum (e.g. 200S / model `JA_RO2`). No external servers, proxies, or bridges (Matterbridge/Homebridge) required — this app talks to Wyze's servers directly from your hub.

## Important: this is unofficial

Wyze has no public, supported API for the robot vacuum. This integration speaks the same private, reverse-engineered app API used by the open-source [`wyze-sdk`](https://github.com/shauntarves/wyze-sdk) (Python), `homebridge-wyze-robovac`, and `matterbridge-wyze-robovac` projects. That means:

- It is **not affiliated with or supported by Wyze Labs**.
- Wyze can change or block this API at any time without notice, which would break this integration until updated.
- Use at your own risk.

---

## Prerequisites

- A Wyze account with a Robot Vacuum (200S or other `JA_RO2` model) already set up in the Wyze app
- A free personal API key from Wyze's developer portal

## Step 1 — Get a Wyze Developer API Key

1. Sign in at [developer-api-console.wyze.com](https://developer-api-console.wyze.com/#/apikey/view) with your Wyze account
2. Generate a key — note the **Key Id** and **API Key** it gives you
3. Avoid keys containing shell-special characters (`*`, `|`, etc.) when copy/pasting

## Step 2 — Install in Hubitat

1. **Drivers Code** → **+ New Driver** → paste [`Wyze-Vacuum-Driver.groovy`](Wyze-Vacuum-Driver.groovy) → **Save**
2. **Apps Code** → **+ New App** → paste [`Wyze-Vacuum-App.groovy`](Wyze-Vacuum-App.groovy) → **Save**
3. **Apps** → **+ Add User App** → **Wyze Vacuum Connect**

## Step 3 — Configure the App

1. Enter your **Key Id** and **API Key** from Step 1
2. Enter your Wyze account **email** and **password**, then click **Log In**
3. If your account has 2FA enabled, enter the verification code (from your authenticator app or SMS) and click **Submit Code**
4. Click **Discover Vacuums**, select the vacuum(s) to add
5. Set a **poll interval** (5 minutes recommended)
6. Click **Done**

Hubitat creates a child device per vacuum, named after its Wyze nickname.

---

## Device Attributes

| Attribute | Type | Description |
|---|---|---|
| `battery` | number | Battery level (%) |
| `switch` | enum | `on` / `off` — see Switch Capability below |
| `status` | string | `Standby` / `Cleaning` / `Returning to charge` / `Docked` / `Mapping` / `Paused` / `Error` |
| `mode` | string | Finer-grained device mode text |
| `suctionLevel` | string | `Quiet` / `Standard` / `Strong` |
| `charging` | string | `true` / `false` |
| `cleanTime` | number | Minutes in the current/last cleaning run |
| `cleanSize` | number | Area cleaned in the current/last run |
| `fault` | string | `none`, or a fault description if the vacuum is stuck/erroring |
| `lastCleanedRooms` | string | Rooms confirmed cleaned by the most recent room-clean run |
| `roomsPendingThisCycle` | number | Rotation rooms not cleaned within the configured cycle window |
| `hoursSinceEmptied` | number | Cumulative cleaning hours since the bin was last reset |
| `learningStatus` | string | `Idle` / `Learning <room> (N more queued)` / `Stopped early` |
| `lastRefresh` | string | Timestamp of the last successful poll |

## Device Commands

| Command | Description |
|---|---|
| `start()` | Start cleaning (or resume if paused) — cleans the whole house |
| `pause()` | Pause the current cleaning run |
| `dock()` | Send the vacuum back to its charging dock |
| `setSuctionLevel(level)` | Set suction to `Quiet`, `Standard`, or `Strong` |
| `cleanRooms(roomNames)` | Clean specific rooms now, e.g. `cleanRooms("Kitchen, Living Room")` |
| `cleanNextRooms()` | Clean whichever rotation rooms have gone longest without a clean (see below) |
| `refresh()` | Force an immediate status poll |
| `resetBinTimer()` | Reset the cumulative cleaning-hours counter used for the bin-empty reminder |
| `learnRoomTimes()` | Start Learning Mode — cleans each rotation room by itself to directly measure its clean time (see below) |
| `cancelLearning()` | Stop Learning Mode after the room currently in progress finishes |
| `cleanRoomSlot1()` … `cleanRoomSlot8()` | Clean whichever room is assigned to that slot (see Room Buttons below) — no arguments, so it's Dashboard-tile-friendly |
| `on()` / `off()` | Standard Switch capability — see below |

---

## Switch Capability

The vacuum implements the standard `Switch` capability (`on()`/`off()`, `switch` attribute) so it works as a plain switch anywhere Hubitat expects one — Alexa/Google Home routines, Rule Machine's switch triggers/conditions, a basic switch tile on Dashboard — with no separate virtual switch device required.

What `on()`/`off()` actually do is configurable per device, under the driver's own preferences (device page → Preferences):

| Setting | Options | Default |
|---|---|---|
| Switch "on" action | Clean Next Rooms (rotation) / Start (whole house) | **Clean Next Rooms** |
| Switch "off" action | Dock / Pause | **Dock** |

The `switch` attribute isn't just a dumb toggle — it's kept in sync with the vacuum's real state on every poll (`on` while `status` is `Cleaning`, `off` otherwise), so it correctly flips to `off` on its own once a clean finishes, gets docked, errors out, etc., not only when you explicitly call `off()`.

---

## Room Buttons — one-tap Dashboard tiles per room, no child devices

Hubitat Dashboard tiles don't support popping up a room picker at tap time — a tile always fires one fixed action. To still get a "tap to clean the kitchen" button without creating a separate device per room, the driver exposes 8 fixed, no-argument commands (`cleanRoomSlot1()` … `cleanRoomSlot8()`) on the vacuum device itself. You assign a room to each slot once; from then on that slot's command always cleans that room.

**Setup:**

1. Under `<vacuum> — Room Buttons` in the app, pick a room for each slot you want to use (leave the rest "not assigned").
2. In Hubitat Dashboard, add the vacuum device as a tile — once per slot you're using. For each tile, pick the specific `cleanRoomSlotN` command as its action (rather than the default tile template). You'll end up with, say, 4 tiles all pointing at the same vacuum device, each labeled/laid out for a different room, each firing a different slot command.
3. Tapping a tile cleans that one room — same ground-truth single-room completion tracking as `cleanRooms()`, so it also counts toward rotation and gets an accurate time measurement.

8 slots is a fixed limit today (Hubitat driver commands have to be declared statically, so this isn't dynamically expandable per-install) — if you have more rooms than that, the extras are still reachable via `cleanRooms("Room Name")`, just not as a one-tap tile.

---

## Room rotation ("clean this while we're out, work through the house over the week")

Wyze's app lets the vacuum clean specific rooms from its saved map. This integration builds on that with a lightweight rotation scheduler, entirely on-hub — no cloud service involved.

### How it works

1. After the vacuum has completed at least one full clean and has named rooms in the Wyze app, click **Discover Rooms** on the app's config page. This decodes the vacuum's current map (Wyze returns it as a zlib-compressed protobuf blob — parsed directly in Groovy) and lists the rooms it found.
2. Pick which of those rooms should participate in the rotation, and a rotation mode:
   - **Fixed number of rooms per run** — e.g. clean 2 rooms every time it's triggered.
   - **Time budget per run** — e.g. clean as many rooms as fit in ~30 minutes. The app learns each room's actual clean time from real runs (starting from a 15-minute guess) and refines the estimate over time, so the time budget gets more accurate the longer you use it.
3. Set a **cycle length** in days (default 7). A room becomes eligible again once it's gone that long without being cleaned — there's no hard weekly reset, it's a rolling "oldest first" queue, so it self-corrects if you trigger it more or less often than expected.
4. Wire the child device's `cleanNextRooms()` command to whatever "everyone left" automation you use (presence, mode change, etc.) in Rule Machine or similar. Each time it fires, it cleans the least-recently-cleaned room(s) from your rotation list and marks them done — so over a week of normal comings and goings, it works its way through the whole rotation list.

You can also call `cleanRooms("Kitchen, Living Room")` directly (e.g. from a button or a one-off automation) to clean specific named rooms regardless of rotation state — it still updates that room's "last cleaned" time, so it counts toward the rotation too.

### What happens if a room-clean run is interrupted

Wyze's API doesn't tell this integration which specific rooms actually finished — only how much cleaning time elapsed. So a room only gets marked "cleaned" (and excluded from the rotation) once its dispatched run ends, and only if its *full* estimated clean time actually elapsed before that happened:

- Dispatch 2 rooms (each estimated ~15 min), and the vacuum is stopped after 5 minutes → **neither** room is marked cleaned. Both stay pending and will be picked again next time `cleanNextRooms()` runs (most likely first, since they're now the most overdue).
- Dispatch 3 rooms (~15 min each), and it runs 20 minutes before being stopped → the **first** room is marked cleaned (its estimate fully elapsed), the other two stay pending.
- If the whole dispatched batch's estimated time elapses without interruption, everything in it is marked cleaned, and that run's actual timing is used to refine the per-room time estimates. A partial/interrupted run does **not** update the estimates, so one bad interruption doesn't skew future time-budget planning.

This is a heuristic, not a ground-truth signal from the vacuum (Wyze doesn't expose one) — it deliberately under-credits rather than over-credits, so an interrupted room is more likely to get re-cleaned sooner than to be silently skipped for a whole cycle.

**Single-room dispatches are the exception** — `cleanRooms("Office")` called with just one room, or any rotation batch that happens to land on one room, has nothing to split, so there's no estimate to check against: any exit other than Paused or Error is treated as that room genuinely finishing, and its real elapsed time becomes the new estimate outright (same ground-truth treatment as Learning Mode below). This also means `cleanRooms("SomeRoom")` with a single room name is itself a quick way to re-time or re-clean just one room, without needing to run the whole Learning Mode queue.

### Room rotation attributes

| Attribute | Description |
|---|---|
| `lastCleanedRooms` | Rooms confirmed cleaned by the most recent room-clean run |
| `roomsPendingThisCycle` | How many of your selected rotation rooms are currently due (not cleaned within the cycle window) |

### Limitations

- Room discovery requires the vacuum to already have a saved map with named rooms in the Wyze app — name your rooms there first.
- The per-room time estimate starts from a flat 15-minute guess and self-corrects from real cleaning-time data reported by the vacuum after each run; expect the first few time-budget runs to be rougher than later ones. **Learning Mode** (below) gets you accurate numbers immediately instead of waiting on that gradual self-correction.
- If Wyze changes the internal map format, room discovery (not the rest of the integration) is the piece most likely to need a fix.

---

## Learning Mode — measuring exact per-room clean times

Normal rotation runs often clean several rooms in one dispatch, so their timing data only tells you the *combined* elapsed time — it's split evenly across the batch as a rough estimate, not measured per room. **Learning Mode** instead cleans every room **by itself, one at a time**, so each room's clean time is a direct measurement, not an inferred split.

### How it works

1. Click **Learn Room Times** under `<vacuum> — Room Timing` (or run the driver's `learnRoomTimes()` command). It queues up every room selected for rotation — or, if none are selected yet, every room Discover Rooms found.
2. It dispatches the first room alone, waits (across normal polling) for that single-room clean to end, records however many minutes it actually took, then automatically dispatches the next room in the queue. This repeats until every queued room has been measured — expect it to take a while, since it's really running the vacuum through each room in sequence.
3. Each room's recorded time **overwrites** whatever estimate existed before (a dedicated single-room pass is treated as ground truth, unlike the gradual blending that happens from normal multi-room rotation runs). You'll see the results under "Known room times" on the app page as they come in.
4. If the run is paused or the vacuum reports an error mid-room, Learning Mode stops itself right there rather than guessing at a number — you'll get a notification (if enabled) saying it stopped early, and whatever rooms were already measured keep their results. Re-run **Learn Room Times** to pick up where you left off (it starts the queue over, but already-measured rooms just get re-measured/overwritten — harmless, just redundant).
5. **Cancel Learning** stops the queue after the room currently in progress finishes (it doesn't interrupt an in-progress room).

Learning Mode also updates each room's "last cleaned" timestamp like any other room clean, so it counts toward your rotation cycle too — it's not wasted cleaning.

### Notes

- This runs over many poll cycles (it depends on your configured poll interval to notice when each room finishes), so don't expect it to fly through all your rooms in a couple of minutes even though each individual room clean might be quick.
- There's currently no automatic timeout if a dispatch silently fails to start (e.g. a dropped API call) — if it looks stuck, check the `learningStatus` attribute and Hubitat's logs, and use **Cancel Learning** to reset it.

---

## Notifications

Optional, change-driven — polling by itself never triggers a notification. Configure under the app's **Notifications** section (applies to all vacuums in this app instance):

| Setting | Description |
|---|---|
| Send notifications to | Any `capability.notification` device(s) — e.g. a virtual notification device wired to your phone/Alexa/etc. |
| Notify when cleaning starts | Fires the first time a poll observes `status` becoming `Cleaning` |
| Notify when cleaning finishes | Fires when `status` leaves `Cleaning`, including the run's elapsed minutes |
| Notify when the vacuum reports a fault | Fires once per new fault (won't repeat every poll while the same fault persists) |

### Bin-empty reminder

Per vacuum, under **`<vacuum> — Bin Reminder`**: set **"Notify to empty the bin after this many cumulative cleaning hours"** (0 disables it). This tracks total active cleaning time — summed across every cleaning session, room-scoped or whole-house — since the counter was last reset. When it crosses the threshold, you get one notification and the counter resets automatically. You can also reset it manually anytime with the **"I emptied it"** button on the app page, or the driver's `resetBinTimer()` command (handy to wire into whatever automation you use when you actually empty it).

---

## Known limitations (v1)

- **Polling only.** Wyze doesn't push status changes, so state updates only happen on the poll interval or right after you issue a command.
- **Single Wyze account.** All vacuums on the account are discoverable from one app instance.
- **Command dispatches (start/pause/dock/cleanRooms/etc.) are still synchronous HTTP calls.** Only the scheduled poll runs fully async (see below) — command calls are one-shot, user/automation-triggered, not on a tight repeating schedule, so they're much less likely to trip Hubitat's load guardrail. If you automate these very frequently and see `LimitExceededException` on a command path, say so — those can be converted too.

## Troubleshooting

**Login fails immediately** — double check Key Id/API Key (from the developer console) and email/password. Watch Hubitat's live logs for the exact error Wyze returned.

**2FA loop / "invalid verification code"** — the code is time-sensitive; request a fresh one and submit quickly. SMS-based 2FA takes an extra round trip and can be more failure-prone than an authenticator app (TOTP) — consider switching your Wyze account to TOTP 2FA if you have repeated trouble.

**Commands silently do nothing** — enable debug logging on the app and check for `signature2`/auth errors in the logs; this usually means the access token expired and the automatic refresh failed, requiring a fresh **Re-login**.

**"No Wyze vacuums found"** — confirm the vacuum is online in the Wyze app and its product model is `JA_RO2` (the 200S's internal model code).

**`LimitExceededException: ... generates excessive hub load` repeating on every poll** — this was a real bug (fixed in 1.5.0): the scheduled poll used blocking `httpGet` calls, which Hubitat's platform throttles when a scheduled job does it repeatedly. The poll now uses `asynchttpGet` exclusively, which doesn't block the hub. If you're on an older version, re-import to pick up the fix.

If the same error instead shows the poll's *async callback method* (`handleVacuumPropsResponse`/`handleVacuumStatusResponse`) rather than `pollAllVacuums`, that's a second variant fixed in 1.5.1: the 401/403 retry path was still making a **synchronous** token-refresh call from inside the async callback, which trips the same guardrail, just relocated. Token refresh is now fully async too. If you were seeing this, also check whether your Wyze session had simply gone stale for an extended period (the `lastRefresh` attribute frozen at an old date is the tell) — if refresh keeps failing even after updating, click **Re-login** in the app to get a fresh session, since the stored refresh token itself may no longer be valid.

---

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
