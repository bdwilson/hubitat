# Scrypted Connect for Hubitat

Connects [Scrypted NVR](https://scrypted.app) to Hubitat:

1. **Virtual motion sensors for verified detections.** Each selected camera gets a Hubitat
   device whose `motion` goes **active** when Scrypted NVR verifies a person, vehicle, animal
   or package (you choose which), and **inactive** once nothing has been detected for a
   configurable number of seconds. Raw pixel motion is ignored.
2. **Mute Scrypted's own notifications** for one camera or all cameras, either for a set
   time or until turned back on, while **Scrypted keeps recording**. Hubitat never sends
   alerts itself; Scrypted's native app notifications stay in charge.
3. **Home / Away.** Mute chosen cameras automatically while Hubitat is in a Home mode, but
   turn them back on during sleep schedules (e.g. 11pm–6am daily).

Works with Scrypted on any platform (Docker, macOS, the Home Assistant add-on, ...), as long
as Hubitat can reach Scrypted's HTTP port (11080 by default) on your LAN.

## How it works

```
Scrypted                                         Hubitat
┌───────────────────────────────┐  detection     ┌────────────────────────────────┐
│ Scrypted Connect bridge       │ ─────────────▶ │ Scrypted Connect app           │
│ (a Scrypted Script)           │  POST /event   │  ├─ Scrypted Connect Hub       │
│  - watches camera detections  │                │  │   (mute/unmute all)          │
│  - mutes/unmutes notifications│ ◀───────────── │  ├─ one Camera device each      │
│    via NVR notifier settings  │  mute/unmute   │  └─ Motion Disabler child apps  │
└───────────────────────────────┘  (bearer token)└────────────────────────────────┘
```

* **Detections:** the bridge listens to each camera's Scrypted NVR object detections. Frames
  labelled `motion` (raw motion) or `clip` (internal) are ignored, as are types you didn't
  select and detections below the confidence threshold. Camera-native names are folded in
  (`people` → person, `dog_cat` → animal).
* **Muting:** Scrypted NVR has no per-camera on/off for notifications; instead each
  notifier (each phone/Mac/browser running the Scrypted app) has a **Custom Notifications**
  list. To mute a camera the bridge adds it to that list on **every** notifier with no
  notification types selected. Unmuting puts back exactly what was there before. Timed mutes
  are tracked inside Scrypted, so they end on time even if Hubitat is offline, and they
  survive a Scrypted restart.
* **Advanced Notifier:** the [Advanced Notifier](https://github.com/apocaliss92/scrypted-advanced-notifier)
  plugin ignores NVR Custom Notifications and checks its own per-camera **Notifications
  enabled** switch instead. The app's **When a camera is muted, silence** setting picks what
  a mute touches: **Auto** (default — NVR notifiers, plus Advanced Notifier's switch on any
  camera extended with it), **NVR only**, **Advanced Notifier only**, or **Both**. The
  switch's previous value is restored on unmute.

## Installation

### 1. Scrypted: install the bridge script

1. In Scrypted, open **Plugins → Scripts** and click **+** to create a new script. Name it
   *Scrypted Connect*.
2. Replace its contents with
   [scrypted_connect-bridge.ts](https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/master/Scrypted/scrypted_connect-bridge.ts)
   and click **Save**. Use **Save**, not **Run**: Scrypted only stores a script on Save,
   and Run on an unsaved script fails with `"undefined" is not valid JSON`.
3. The script's console prints two lines you need for Hubitat:
   ```
   Bridge URL:   http://192.168.1.68:11080/endpoint/82/public/
   Bridge Token: 0123456789abcdef0123456789abcdef
   ```
   The token is generated once and kept across restarts.

### 2. Hubitat: install the code

**Option A: Hubitat Package Manager (recommended).** In HPM choose **Install → Search by
Keywords**, search for *Scrypted Connect* and install it. HPM installs the app and both
drivers and enables OAuth for the app. Tick *Scrypted Connect Child - Motion Disabler*
too if you want Motion Disablers.

**Option B: manual install.**

* **Drivers Code → New Driver**, paste and save each of:
  * [Scrypted Connect Hub](https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/master/Scrypted/scrypted_connect-hub-driver.groovy)
  * [Scrypted Connect Camera](https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/master/Scrypted/scrypted_connect-camera-driver.groovy)
* **Apps Code → New App**, paste and save each of:
  * [Scrypted Connect](https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/master/Scrypted/scrypted_connect-app.groovy)
    — then click **OAuth → Enable OAuth in App → Update**. This is required: Scrypted
    delivers detections to the app's OAuth endpoint.
  * [Scrypted Connect Child - Motion Disabler](https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/master/Scrypted/scrypted_connect-motion-disabler-child-app.groovy) (optional)

### 3. Hubitat: configure the app

1. **Apps → Add User App → Scrypted Connect.**
2. Paste the **Bridge URL** and **Bridge Token** from step 1.
3. Pick the detection types that should make a camera's motion sensor active (default:
   person, vehicle, animal, package), the minimum confidence (default 0.6) and how many
   seconds without a detection before it goes inactive (default 30).
4. Click **Next**, select the cameras to create Hubitat devices for, click **Done**.
5. Optionally open the app again and add **Motion Disablers** or set up **Home / Away**.

The app re-sends its settings (including its callback URL) to the bridge every 5 minutes,
which also serves as the health check.

## Devices

### Scrypted Connect Camera (one per selected camera)

| | |
|---|---|
| `motion` | `active` while a selected detection type is being seen; `inactive` after the quiet period |
| `objects` | detected types, e.g. `person, vehicle`; `none` when inactive |
| `score`, `labels`, `lastDetection`, `lastActive`, `lastInactive`, `duration` | details of the current/last detection (`labels` = recognized faces/plates, if any) |
| `switch` | `on` = Scrypted notifications enabled for this camera, `off` = muted |
| `notifications`, `mutedUntil` | mute status and when it ends (`until unmuted` for indefinite mutes) |
| `mute(seconds)` | mute for a period (blank = until `unmute`/`on`) |
| `unmute()` | restore notifications now |

Preference **Safety timeout** (default 900 s) forces `motion` inactive if Scrypted never
reports the end of a detection (for example if Scrypted restarts mid-detection).

**Why was it muted?** Each mute / unmute event's **Description Text** (in the device's
**Events** tab, and in the log while *Log mute / unmute descriptions* is on) names what
caused it, for example:

| Name | Value | Description Text |
|---|---|---|
| switch | off | Doorbell notifications muted until 2026-11-09 18:42:10 (Motion Disabler 'Front Door': Front Door Lock unlocked) |
| mutedUntil | 2026-11-09 18:44:30 | Doorbell notifications mute now ends 2026-11-09 18:44:30 (Motion Disabler 'Garage': Garage Door open) |
| switch | on | Doorbell notifications restored (timed mute ended) |
| switch | off | Doorbell notifications muted until unmuted (Home / Away: Home mode Day) |
| switch | on | Doorbell notifications restored (Home / Away: sleep time started) |

The cause shows as the Motion Disabler and its triggering device, Home / Away and the mode
or sleep time, or the camera / hub device's own switch or command (a Rule Machine rule
turning the device off shows up as *switch off*). *Timed mute ended* and *unmuted in
Scrypted* mean Scrypted restored it on its own.

### Scrypted Connect Hub

| | |
|---|---|
| `switch` | `off` = every Scrypted camera muted; `on` otherwise. Turning it off/on mutes/unmutes all cameras |
| `muteAll(seconds)`, `unmuteAll()` | mute every Scrypted camera for a period / restore all |
| `notifications` | `enabled`, `partially muted` or `muted` |
| `mutedCameras`, `totalCameras` | counts across all Scrypted cameras |
| `healthStatus` | `online` / `offline` — whether the bridge answered the last request |
| `homeMode` | `away`, `home` (muting the Home cameras), `sleeping` (home, inside a sleep window) or `off` (not set up) |

### Motion Disabler (child app)

Mutes the selected cameras for N seconds whenever a chosen contact, lock, motion or presence
event happens, e.g. unlocking the front door mutes the Doorbell for 2 minutes. Repeated
triggers extend the mute. Don't use a camera's own Scrypted Connect motion sensor as a
trigger for that same camera.

### Home / Away

Open the app and choose **Home / Away**:

* **Modes that count as Home:** e.g. *Day*, *Evening*. Every other mode is Away.
* **When home, mute Scrypted notifications from:** any Scrypted camera, with or without a
  Hubitat device (e.g. the back yard cameras while you're out there anyway).
* **But re-enable them while I sleep:** add as many sleep times as you like, each with a
  start, an end and *Every day*, *Weekdays*, *Weekends* or specific days. A time that runs past
  midnight belongs to the day it starts: *Weekdays 11pm–6am* covers Monday night through
  Friday night, ending Saturday 6am.

In a Home mode outside sleep time the chosen cameras are muted until unmuted; switching to
an Away mode or reaching a sleep time restores them. The app re-checks every 5 minutes, so it
catches up after a hub reboot.

How it interacts with other muting:

* If you unmute a camera by hand while home, it stays unmuted until the next Home / Away /
  sleep change.
* A camera that was already muted until unmuted (by you or the Hub switch) when Home starts is
  left alone, and Home / Away won't unmute it later.
* A Motion Disabler on a camera Home / Away has muted can't shorten the mute. When Home ends,
  the camera is restored even if a Motion Disabler's time hadn't run out yet.

### Rule Machine examples

* *Turn notifications off while we're in the backyard, back on later:* turn the camera
  device **off**, wait, turn it **on** — or call `mute` with a number of seconds and let
  Scrypted restore it.
* *Lights on when a person is at the front door:* trigger on the Doorbell device's
  `motion` becoming active, optionally conditioned on `objects` containing `person`.

## Known limitations

* Home / Away runs in Hubitat. Its mutes and restores happen when Hubitat is up; a timed
  `mute(seconds)` is tracked by Scrypted instead.
* Muting covers the **Scrypted NVR app notifiers** (the phones/Macs/browsers listed in
  Scrypted's notifier devices). Other notification paths you've built yourself (e.g. Home
  Assistant automations) aren't touched.
* A camera that already has your own **Custom Notifications** on a notifier is handled:
  muting saves those settings and unmuting restores them. Don't edit a camera's Custom
  Notifications in the Scrypted UI while it's muted; unmuting will put back the values from
  before the mute.
* While a camera is muted, a phone that is newly registered with Scrypted only gets the
  mute applied the next time the camera is muted.
* If a camera is deleted and re-added in Scrypted it may get a new identity; select it
  again in the app (the old Hubitat device is removed when you unselect it).
* The bridge's HTTP endpoint is reachable by anything on your LAN that can reach Scrypted;
  every request except a bare version check requires the bridge token.

## Troubleshooting

* **App says it can't reach the bridge:** open the Bridge URL in a browser from the same
  network — it should return `{"scryptedConnect":"0.2.0"}`. If the script was re-created,
  its URL (the number after `/endpoint/`) changes; copy the new one from the console.
* **`createAccessToken` / OAuth error in the Hubitat log:** enable OAuth for the
  Scrypted Connect app in **Apps Code**, then open the app and click **Done**.
* **Motion never goes active:** confirm Scrypted NVR object detection is enabled on the
  camera and that the selected types match what it detects (a camera's own AI may report
  `people`/`dog_cat`; those count as person/animal).
