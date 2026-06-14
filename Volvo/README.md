# Volvo Connect — Hubitat Integration

Native Hubitat integration for Volvo vehicles via the [Volvo Connected Vehicle API](https://developer.volvocars.com). No external servers or proxies required.

## Features

- **Lock / Unlock** — control your vehicle's central lock from Hubitat
- **Battery level** (EVs/PHEVs) — state of charge as a percentage
- **Electric range** — estimated range remaining
- **Charging status** — current charging state (e.g. `CHARGING_PAUSED`, `CHARGING`)
- **Fuel level** (ICE/PHEV) — fuel percentage
- **Fuel range** — estimated range to empty
- **GPS location** — latitude/longitude *(requires Location API approval — see note below)*
- **Charging notifications** — alerts when charging starts (with estimated finish time), stops, or completes; optional 10% battery step alerts
- **Configurable poll interval** — 5, 10, 15, 30, or 60 minutes

---

## Prerequisites

- Hubitat hub with cloud connectivity (the OAuth callback requires a publicly reachable HTTPS URL)
- A [Volvo developer account](https://developer.volvocars.com)
- Your Volvo account must have at least one vehicle connected

---

## Step 1 — Create a Volvo Developer Application

1. Sign in at [developer.volvocars.com](https://developer.volvocars.com)
2. Go to **My Apps** → **Create Application**
3. Fill in a name and description (e.g. "Hubitat Integration")
4. Under **Scopes**, select the following *(see full scope list below)*
5. In the **Redirect URI** field, enter your Hubitat cloud callback URL — you will get this from the app in Step 3 below. You can come back and add it after the app is installed.
6. Submit the application for approval

### Required Scopes

Select these in the Volvo developer portal when creating your app:

**openid** *(always selected)*

**Connected Vehicle API** *(select all of these)*:
| Scope | Purpose |
|---|---|
| `conve:command_accessibility` | Required to issue any remote command |
| `conve:commands` | Required to issue any remote command |
| `conve:doors_status` | Read lock/door status |
| `conve:fuel_status` | Read fuel level and range |
| `conve:lock` | Lock command |
| `conve:lock_status` | Read lock state |
| `conve:unlock` | Unlock command |
| `conve:vehicle_relation` | List vehicles on your account |

**Energy API** *(for EVs and PHEVs)*:
| Scope | Purpose |
|---|---|
| `energy:capability:read` | Detect supported energy features |
| `energy:state:read` | Battery level, range, charging status |

**Location API** *(for GPS — see approval note)*:
| Scope | Purpose |
|---|---|
| `location:read` | Vehicle GPS coordinates |

### Future-proofing: extra scopes to select now

Volvo re-approval takes 14–21 days, so it's worth enabling these now even if the current integration doesn't use them yet:

`conve:battery_charge_level`, `conve:climatization_start_stop`, `conve:engine_start_stop`, `conve:environment`, `conve:honk_flash`, `conve:odometer_status`, `conve:trip_statistics`, `conve:tyre_status`, `conve:warnings`, `conve:windows_status`

---

## ⚠️ API Approval — 14 to 21 Days

Volvo reviews all developer app submissions before granting access. During this waiting period:

- **Lock, unlock, battery level, charging status, and fuel** all work immediately with a test vehicle (your own registered Volvo) once the app is created — Volvo allows testing before full approval.
- **Location/GPS** (`location:read`) typically does **not** work until the application is fully approved. You will see a `403 Forbidden` error in Hubitat logs until then — this is expected and will resolve on its own once approved.

---

## Step 2 — Install in Hubitat

1. In Hubitat, go to **Drivers Code** → **+ New Driver** → paste the contents of [`Volvo-Driver.groovy`](Volvo-Driver.groovy) → **Save**
2. Go to **Apps Code** → **+ New App** → paste the contents of [`Volvo-Connect-App.groovy`](Volvo-Connect-App.groovy) → **Save**
   - On the Apps Code page, click **OAuth** on the Volvo Connect app and enable it
3. Go to **Apps** → **+ Add User App** → **Volvo Connect**

---

## Step 3 — Configure the App

### Page 1 — Credentials & API Subscriptions

1. **Copy the Redirect URI** shown at the top of the page and add it to your Volvo developer app's allowed redirect URIs (if you haven't already)
2. Enter your **VCC API Key**, **Client ID**, and **Client Secret** from your Volvo developer app
3. Check the boxes for which APIs you subscribed to:
   - ☑ **Energy API** — if you selected `energy:state:read` (required for EV battery data)
   - ☑ **Location API** — if you selected `location:read` (GPS; pending approval initially)
4. Click **Next**

### Page 2 — Authorization & Vehicle Selection

1. Click **Authorize with Volvo →** and complete the login in your browser
2. You will be redirected back to Hubitat automatically. Return to the app.
3. Click **Discover Vehicles** to find your vehicle(s)
4. Select your VIN from the dropdown
5. Set your preferred **poll interval** (15 minutes recommended)
6. Click **Done**

Hubitat will create a child device named **Volvo {VIN}** under the app. You can rename it in the device settings.

---

## Device Attributes

| Attribute | Type | Description |
|---|---|---|
| `lock` | enum | `locked` / `unlocked` / `locking` / `unlocking` |
| `battery` | number | EV/PHEV battery charge level (%) |
| `batteryRange` | number | Estimated electric range (km) |
| `chargingStatus` | string | e.g. `CHARGING_PAUSED`, `CHARGING` |
| `fuelLevel` | number | Fuel level (%) |
| `fuelRange` | number | Estimated fuel range (km) |
| `latitude` | string | GPS latitude *(after API approval)* |
| `longitude` | string | GPS longitude *(after API approval)* |
| `lastLocation` | string | `lat, lon` combined *(after API approval)* |
| `lastRefresh` | string | Timestamp of last successful poll |

## Device Commands

| Command | Description |
|---|---|
| `lock()` | Send lock command to vehicle |
| `unlock()` | Send unlock command to vehicle |
| `refresh()` | Force an immediate poll |

---

## Notifications

Notifications are **change-driven** — a poll never triggers one by itself. The first poll after install silently records a baseline.

| Notification | Trigger |
|---|---|
| Charging started | `chargingStatus` transitions to `CHARGING` |
| Charging stopped | `chargingStatus` leaves `CHARGING` (not fully charged) |
| Fully charged | `chargingStatus` transitions to `FULLY_CHARGED` |
| Battery at X% | Every 10% increment crossed while charging (optional) |

**Charging started** messages include battery level, target charge %, and estimated finish time when the API provides them, e.g.:
> Volvo charging started. Battery at 34%, charging to 80%. Estimated finish: 11:45 PM.

Configure notification devices under **Notifications** on the auth page after selecting your vehicle.

---

## Troubleshooting

**`403 Forbidden` on location** — API not yet approved. Wait for Volvo's approval email (14–21 days). No action needed; it will start working automatically.

**`410 Gone` on energy endpoint** — Outdated endpoint. Make sure you're running the latest version of the app code.

**`invalid_scope`** — The scope list requested scopes your app isn't subscribed to. Check that your Volvo developer app has all required scopes selected, or uncheck the Energy/Location checkboxes in the Hubitat app if you didn't subscribe to those APIs.

**Lock/unlock returns no status** — The `conve:command_accessibility` and `conve:commands` scopes are required in addition to `conve:lock` / `conve:unlock`. Re-authorize if they were missing.

**Re-authorization** — If your refresh token expires or becomes invalid, click **Re-authorize** on the app's auth page. The Redirect URI does not change, so no portal changes are needed.

---

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
