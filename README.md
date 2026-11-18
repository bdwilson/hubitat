# Hubitat Apps & Drivers

Hubitat Elevation apps and drivers by Brian Wilson.

**HPM-ready packages** · 🌐 Local LAN · ☁️ Cloud API · 🔌 Needs an external bridge/device · 📦 [Hubitat Package Manager](https://github.com/dcmeglio/hubitat-packagemanager) · 💬 Community thread

## Like what I do?

[☕ Buy me a coffee](https://www.buymeacoffee.com/brianwilson) or buy some [swag](https://www.etsy.com/shop/CronologicalConcepts)

## Installation

Most packages can be installed and kept up to date with the [Hubitat Package Manager](https://github.com/dcmeglio/hubitat-packagemanager) (HPM): **Install → Search by Keywords** and look for the package name below.

For anything not in HPM (marked ➖ in the tables), open the package's folder, then paste each `.groovy` file into **Drivers Code** / **Apps Code** (or use the file's *Import* button with its raw URL) as described in that folder's README.

---

## 🛡️ Security & Alarm

| Package | Type | Key Features | HPM | Docs | Community | Updated |
| --- | --- | --- | :---: | --- | --- | :---: |
| **Honeywell Vista Envisalink TPI** | 🌐 Local | Native Hubitat support for Honeywell Vista/Envisalink alarm systems — no external server needed | 📦 | [Docs](Envisalink) | [💬](https://community.hubitat.com/t/native-honeywell-vista-envisalink-tpi-alarm-driver/164482) | 2026-10-04 |
| **Honeywell Security** | 🔌 Bridge | Honeywell Vista/Envisalink alarm integration via an external node-proxy server | 📦 | [Docs](Honeywell) | [💬](https://community.hubitat.com/t/release-envisalink-app-driver-for-vista-ademco-honeywell-alarm-via-smartthings-nodeproxy/9726) | 2023-01-16 |
| **Camect Connect** | 🌐 Local | Sync HSM and virtual motion devices with your Camect NVR | 📦 | [Docs](Camect/README.md) | [💬](https://community.hubitat.com/t/release-camect-connect/43837) | 2024-02-12 |
| **Scrypted Connect** | 🔌 Bridge | Virtual motion sensors for Scrypted NVR verified detections (person, vehicle, animal, package) • timed or indefinite muting of Scrypted's own notifications per camera or all cameras, recording continues • Motion Disablers • Home/Away muting with sleep-time exceptions • also mutes Advanced Notifier • mute events say what caused them (needs a Scrypted bridge script) | 📦 | [Docs](Scrypted) | [💬](https://community.hubitat.com/t/release-scrypted-connect-scrypted-nvr-detections-and-notification-muting/167560) | 2026-11-17 |
| **Smart Mailbox** | 🌐 Local | Get notified when you receive mail in your mailbox | 📦 | [Docs](SmartMailbox) | [💬](https://community.hubitat.com/t/release-smart-mailbox/11402) | 2020-05-18 |

## 📍 Presence & Location

| Package | Type | Key Features | HPM | Docs | Community | Updated |
| --- | --- | --- | :---: | --- | --- | :---: |
| **Geofency Presence** | ☁️ Cloud | Set presence from the Geofency iOS app | 📦 | [Docs](Geofency-Presence) | [💬](https://community.hubitat.com/t/release-geofency-presence/22788) | 2026-09-09 |
| **OwnTracks Presence** | ☁️ Cloud | Set presence from the OwnTracks iOS/Android app | 📦 | [Docs](OwnTracks-Presence) | [💬](https://community.hubitat.com/t/release-owntracks-presence/53419) | 2026-09-07 |
| **BTLE Multi-User Presence** | 🔌 Bridge | Bluetooth LE presence (Tile Mate and similar beacons) for multiple users via a Python script | ➖ | [Docs](BTLE-Presence) | [💬](https://community.hubitat.com/t/release-btle-presence-sensor/48332) | 2022-06-05 |
| **GeoHopper Presence** | 🌐 Local | Multi-user virtual presence updater for the GeoHopper app | ➖ | [Docs](Geohopper-Presence) | — | 2019-03-08 |

## 🚗 Vehicles

| Package | Type | Key Features | HPM | Docs | Community | Updated |
| --- | --- | --- | :---: | --- | --- | :---: |
| **Volvo Connect** | ☁️ Cloud | Lock/unlock • battery, fuel & range • charging status with start/stop/limit-reached notifications and finish-time estimate • doors, windows, tyres, odometer • GPS location • engine/climate/honk commands (🧪 extended features are untested — they need Volvo API approval) | 📦 | [Docs](Volvo) | [💬](https://community.hubitat.com/t/release-volvo-connect-native-volvo-integration-oauth2-no-external-server/167561) | 2026-11-18 |

## 🏊 Pool, Water & Irrigation

| Package | Type | Key Features | HPM | Docs | Community | Updated |
| --- | --- | --- | :---: | --- | --- | :---: |
| **WaterGuru Integration** | ☁️ Cloud | Chlorine, pH, flow rate and temperature from your pool's WaterGuru sensor | 📦 | [Docs](WaterGuru) | [💬](https://community.hubitat.com/t/release-waterguru-pool-integration/77922) | 2026-10-24 |
| **Tuya Zigbee Valve (SONOFF SWV-ZF2)** | 🌐 Local | Dual-port Zigbee water valve fork with a working `openFor(duration)` command | ➖ | [Docs](Tuya-Zigbee-Valve) | [💬](https://community.hubitat.com/t/sonoff-zigbee-sprinklers-on-pre-order-sale/162398) | 2026-08-25 |
| **Raincloud Valve** | 🔌 Bridge | Melnor Raincloud watering valves via a small Python Flask bridge | ➖ | [Docs](Raincloud) | — | 2020-06-19 |
| **Simple Irrigation** | 🌐 Local | Parent/child irrigation app for hose-timer style valves, multiple timers and restrictions (based on work by @bptworld) | ➖ | [Folder](SimpleIrrigation) | — | 2026-09-20 |
| **Arduino Pool Controller** | 🔌 Bridge | ST_Anything-based Pentair SuperFlo VS pump speed and pool light control | ➖ | [Docs](Arduino-Pool) | — | 2021-11-06 |

## 🏠 Control & Appliances

| Package | Type | Key Features | HPM | Docs | Community | Updated |
| --- | --- | --- | :---: | --- | --- | :---: |
| **Wyze Vacuum Connect** | ☁️ Cloud | Wyze Robot Vacuums (200S/JA_RO2) • room-based rotation cleaning • room timing learning mode • status monitoring • no external server | 📦 | [Docs](Wyze-Vacuum) | [💬](https://community.hubitat.com/t/release-wyze-vacuum-integration-cloud/165866) | 2026-11-17 |
| **Genmon Generator Monitor** | 🔌 Bridge | Monitor and control your generator through Genmon | 📦 | [Docs](Genmon) | [💬](https://community.hubitat.com/t/genmon-generator-monitor/164362) | 2026-09-22 |
| **Neosmart Blinds** | 🔌 Bridge | Control NeoSmart blinds (requires the NeoSmart controller) | 📦 | [Docs](NeoSmart) | [💬](https://community.hubitat.com/t/neo-smart-controller-for-blinds/26391) | 2024-06-16 |
| **Hugo Button Remote** | 🌐 Local | 4-button ESP8266 WiFi remote (7 combinations) integrated with Hubitat — used to control blinds | ➖ | [Docs](Hugo) | — | 2020-02-02 |
| **Fireplace Controller** | 🌐 Local | ~$12 Wemos D1 relay to control a fireplace from Hubitat and Alexa | ➖ | [Docs](Fireplace) | — | 2020-02-02 |
| **Garage Controller** | 🌐 Local | ST_Anything / Wemos D1 garage door controller with local control and no MyQ cloud | ➖ | [Docs](Garage) | [💬](https://community.hubitat.com/t/yet-another-garage-door-controller-w-hubduino/43544) | 2020-08-17 |
| **Honeywell TCC (IFTTT)** | ☁️ Cloud | Control a Honeywell Total Connect Comfort thermostat through IFTTT webhooks | ➖ | [Docs](HoneywellTCC) | [💬](https://community.hubitat.com/t/honeywell-tcc-ifttt-driver/10385) | 2019-02-14 |
| **MyQ Lite** | ☁️ Cloud | MyQ garage door opener app and drivers — **unmaintained**, see the README for the current maintained version | ➖ | [Docs](MyQ) | [💬](https://community.hubitat.com/t/updated-myq-lite-application/31318) | 2020-05-18 |

## ⛽ Propane

| Package | Type | Key Features | HPM | Docs | Community | Updated |
| --- | --- | --- | :---: | --- | --- | :---: |
| **Otodata Propane Receiver** | 🔌 Bridge | Propane tank level from an Otodata receiver (uses the [otodata-genmon](https://github.com/bdwilson/otodata-genmon) bridge) | ➖ | [Folder](Otodata-Propane) | — | 2022-12-07 |
| **ESP32 Propane Sensor** | 🔌 Bridge | Propane tank level from a DIY ESP32 sensor | ➖ | [Folder](ESP32_Propane) | — | 2022-11-21 |

## 🌤️ Weather

| Package | Type | Key Features | HPM | Docs | Community | Updated |
| --- | --- | --- | :---: | --- | --- | :---: |
| **Weewx Local Capabilities** | 🌐 Local | Outdoor temp, humidity, wind, pressure and detailed rain totals from Weewx • wet/dry switch for use with Simple Irrigation | 📦 | [Docs](Weewx) | [💬](https://community.hubitat.com/t/release-weewx-local-capabilities/17709) | 2026-11-15 |

## 🧰 Utilities & Notifications

| Package | Type | Key Features | HPM | Docs | Community | Updated |
| --- | --- | --- | :---: | --- | --- | :---: |
| **SendGrid** | ☁️ Cloud | Notification driver for the SendGrid email service (100 messages/day on the free plan) | 📦 | [Docs](https://community.hubitat.com/t/release-sendgrid-email-notification-plugin/48691) | [💬](https://community.hubitat.com/t/release-sendgrid-email-notification-plugin/48691) | 2020-09-22 |
| **Smarter Bulbs** | 🌐 Local | Keep track of the state of your smart bulbs through a power outage | 📦 | [Docs](SmarterBulbs) | [💬](https://community.hubitat.com/t/power-failure-recovery-via-rm/4298/12) | 2023-08-13 |
| **Toggle API** | 🌐 Local | Toggle devices that don't natively support a toggle command | 📦 | [Docs](Toggle-API) | — | 2021-02-26 |
| **Notify If Motion** | 🔌 Bridge | POST to an endpoint URL you configure when motion is detected | ➖ | [Folder](NotifyIfMotion) | — | 2020-04-24 |

## 📂 Miscellaneous Drivers

[`Drivers/`](Drivers) holds assorted drivers (HEM v1/v2, WAPIRZ-1, contact sensor, Genmon, virtual mobile presence, Z-Wave metering switch). These are **not owned or maintained by me** — I may have modified them to work with Hubitat, but they come with no guarantee.

---

## Notes

- **Tuya Zigbee Valve** has a `packageManifest.json` but is not yet listed in `repository.json`, so it won't appear in HPM's keyword search until it is; install it from its folder in the meantime.
- **Toggle API** doesn't have a Community thread yet; it will be linked here once a release thread exists.
