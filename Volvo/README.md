# Volvo Connect — Hubitat Integration

Support / discussion: [Hubitat Community thread](https://community.hubitat.com/t/release-volvo-connect-native-volvo-integration-oauth2-no-external-server/167561)

Native Hubitat integration for Volvo vehicles via the [Volvo Connected Vehicle API](https://developer.volvocars.com). No external servers or proxies required.

> ⚠️ **Not all features are verified.** The author's Volvo developer app has **not received full approval** for the extended set of scopes. Lock/unlock, battery/charging and the charging notifications are tested against a real vehicle. Everything marked 🧪 below is **implemented from Volvo's API documentation but untested** — on the author's account those calls return `403 Forbidden`. See [API Approval](#️-api-approval--tested-vs-untested-features).

## Features

**Tested and working**

- **Lock / Unlock** — control your vehicle's central lock from Hubitat
- **Battery level** (EVs/PHEVs) — state of charge as a percentage
- **Electric range** — estimated range remaining
- **Charging status, connection and charge limit** — e.g. `CHARGING`, `CONNECTED_AC`, target %
- **Charging notifications** — started (with target % and estimated finish time), charge limit reached, unplugged, fully charged, optional 10% steps
- **Door / hood / tailgate / tank lid status** (partial — depends on what your vehicle reports)
- **Metric or imperial units** and a **configurable poll interval** (5, 10, 15, 30 or 60 minutes)

**🧪 Implemented but untested (need Volvo's extended approval)**

- **GPS location** — latitude, longitude, heading, timestamp (`location:read`)
- **Window and sunroof status**, **tyre status**, **odometer**, **warning lights**, **engine status**
- **Engine start/stop**, **climatization start/stop**, **honk / flash**
- **Fuel level and range** (ICE/PHEV) — not verified on an ICE or hybrid vehicle

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

The extended features above need these scopes. Volvo re-approval takes 14–21 days, so select them when you first create the app rather than waiting twice:

`conve:battery_charge_level`, `conve:climatization_start_stop`, `conve:engine_start_stop`, `conve:environment`, `conve:honk_flash`, `conve:odometer_status`, `conve:trip_statistics`, `conve:tyre_status`, `conve:warnings`, `conve:windows_status`

---

## ⚠️ API Approval — Tested vs Untested Features

Volvo reviews all developer app submissions before granting access, which takes roughly 14–21 days.

**The author has not received full approval for the extended controls.** Anything in the second table below returns `403 Forbidden` on the author's account, so it has **never been run successfully against a real vehicle**. The code follows Volvo's published API, but treat those features as experimental. If you do have approval, please report whether they work (with a debug log) in the support thread or as a GitHub issue.

### Tested — works without extended approval

| Feature | Notes |
|---|---|
| Lock / Unlock | |
| Battery level, range, charging status, charge limit | Requires Energy API subscription |
| Charging notifications | Driven by the battery/charging data above |
| Door status (hood, tailgate, tank lid) | Partial — individual doors may be missing |

### 🧪 Untested — need extended approval

These return `403 Forbidden` until Volvo approves the corresponding scopes. The app logs them as warnings and skips them, so nothing breaks while you wait. Once approved they *should* start working, but that is not confirmed.

| Feature | Scope |
|---|---|
| GPS location | `location:read` |
| Odometer | `conve:odometer_status` |
| Window / sunroof status | `conve:windows_status` |
| Tyre status | `conve:tyre_status` |
| Warning lights | `conve:warnings` |
| Engine status, start / stop | `conve:engine_start_stop` |
| Climatization start / stop | `conve:climatization_start_stop` |
| Honk / flash | `conve:honk_flash` |
| Fuel level / range | `conve:fuel_status` — works for the author's EV account but returns no data, so unverified on ICE/hybrid |

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

Attributes marked 🧪 depend on scopes that need extended approval and are untested.

| Attribute | Type | Description |
|---|---|---|
| `lock` | enum | `locked` / `unlocked` / `locking` / `unlocking` |
| `battery` | number | EV/PHEV battery charge level (%) |
| `batteryRange` | number | Estimated electric range (km or mi) |
| `chargingStatus` | string | e.g. `CHARGING`, `CHARGING_PAUSED`, `FULLY_CHARGED` |
| `chargingConnection` | string | e.g. `CONNECTED_AC`, `DISCONNECTED` |
| `chargeLimit` | number | Target charge level set on the car (%) |
| `fuelLevel`, `fuelRange` | number | Fuel level (%) and range 🧪 |
| `doorFrontLeft/Right`, `doorRearLeft/Right`, `hood`, `tailgate`, `tankLid` | string | Door states (partial) |
| `windowFrontLeft/Right`, `windowRearLeft/Right`, `sunroof` | string | Window states 🧪 |
| `tyreFrontLeft/Right`, `tyreRearLeft/Right` | string | Tyre status 🧪 |
| `odometer` | number | Total distance (km or mi) 🧪 |
| `engineStatus` | string | `RUNNING` / `STOPPED` 🧪 |
| `warningBrakeLight`, `warningFuelLow`, `warningEngineLight`, `warningOilLow`, `warningWasherFluid`, `warningBrakeFluid` | string | Warning states 🧪 |
| `vehicleModel`, `modelYear`, `fuelType`, `externalColor`, `gearbox` | string | Vehicle info (fetched once) |
| `latitude`, `longitude`, `heading`, `lastLocation`, `locationTimestamp` | string | GPS 🧪 |
| `lastRefresh` | string | Timestamp of last successful poll |

## Device Commands

| Command | Description |
|---|---|
| `lock()` / `unlock()` | Lock or unlock the vehicle |
| `refresh()` | Force an immediate poll |
| `startEngine(minutes)` / `stopEngine()` | 🧪 Remote engine start (1–15 min) / stop |
| `startClimatization()` / `stopClimatization()` | 🧪 Pre-condition the cabin |
| `honk()` / `flash()` / `honkAndFlash()` | 🧪 Horn and/or lights |

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

**`403 Forbidden` on location, odometer, windows, tyres, warnings, engine, climatization, honk/flash** — your Volvo developer app hasn't been approved for those scopes. They're logged as warnings and skipped. These features are untested even by the author (see above).

**`410 Gone` on energy endpoint** — Outdated endpoint. Make sure you're running the latest version of the app code.

**`invalid_scope`** — The scope list requested scopes your app isn't subscribed to. Check that your Volvo developer app has all required scopes selected, or uncheck the Energy/Location checkboxes in the Hubitat app if you didn't subscribe to those APIs.

**Lock/unlock returns no status** — The `conve:command_accessibility` and `conve:commands` scopes are required in addition to `conve:lock` / `conve:unlock`. Re-authorize if they were missing.

**Re-authorization** — If your refresh token expires or becomes invalid, click **Re-authorize** on the app's auth page. The Redirect URI does not change, so no portal changes are needed.

---

## Support

Questions, bug reports and feedback (especially if you have extended Volvo approval and can test the 🧪 features): [Hubitat Community thread](https://community.hubitat.com/t/release-volvo-connect-native-volvo-integration-oauth2-no-external-server/167561).

## License

Licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
