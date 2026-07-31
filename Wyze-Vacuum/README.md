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
| `lastRefresh` | string | Timestamp of the last successful poll |

## Device Commands

| Command | Description |
|---|---|
| `start()` | Start cleaning (or resume if paused) |
| `pause()` | Pause the current cleaning run |
| `dock()` | Send the vacuum back to its charging dock |
| `setSuctionLevel(level)` | Set suction to `Quiet`, `Standard`, or `Strong` |
| `refresh()` | Force an immediate status poll |

---

## Known limitations (v1)

- **No room/zone selection.** Wyze exposes per-room cleaning via saved maps, which needs additional map-parsing work not yet implemented here. `start()` runs a full clean.
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
