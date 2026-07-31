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

### Room rotation attributes

| Attribute | Description |
|---|---|
| `lastCleanedRooms` | Rooms targeted by the most recent room-clean dispatch |
| `roomsPendingThisCycle` | How many of your selected rotation rooms are currently due (not cleaned within the cycle window) |

### Limitations

- Room discovery requires the vacuum to already have a saved map with named rooms in the Wyze app — name your rooms there first.
- The per-room time estimate starts from a flat 15-minute guess and self-corrects from real cleaning-time data reported by the vacuum after each run; expect the first few time-budget runs to be rougher than later ones.
- If Wyze changes the internal map format, room discovery (not the rest of the integration) is the piece most likely to need a fix.

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

## Troubleshooting

**Login fails immediately** — double check Key Id/API Key (from the developer console) and email/password. Watch Hubitat's live logs for the exact error Wyze returned.

**2FA loop / "invalid verification code"** — the code is time-sensitive; request a fresh one and submit quickly. SMS-based 2FA takes an extra round trip and can be more failure-prone than an authenticator app (TOTP) — consider switching your Wyze account to TOTP 2FA if you have repeated trouble.

**Commands silently do nothing** — enable debug logging on the app and check for `signature2`/auth errors in the logs; this usually means the access token expired and the automatic refresh failed, requiring a fresh **Re-login**.

**"No Wyze vacuums found"** — confirm the vacuum is online in the Wyze app and its product model is `JA_RO2` (the 200S's internal model code).

---

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
