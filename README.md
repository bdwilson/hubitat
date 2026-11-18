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

| Package | Type | Key Features | HPM | Docs | Community |
| --- | --- | --- | :---: | --- | --- |
| **Honeywell Vista Envisalink TPI** | 🌐 Local | Native Hubitat support for Honeywell Vista/Envisalink alarm systems — no external server needed | 📦 | [Docs](Envisalink) | [💬](https://community.hubitat.com/t/native-honeywell-vista-envisalink-tpi-alarm-driver/164482) |
| **Honeywell Security** | 🔌 Bridge | Honeywell Vista/Envisalink alarm integration via an external node-proxy server | 📦 | [Docs](Honeywell) | [💬](https://community.hubitat.com/t/release-envisalink-app-driver-for-vista-ademco-honeywell-alarm-via-smartthings-nodeproxy/9726) |
| **Camect Connect** | 🌐 Local | Sync HSM and virtual motion devices with your Camect NVR | 📦 | [Docs](Camect/README.md) | [💬](https://community.hubitat.com/t/release-camect-connect/43837) |
| **Scrypted Connect** | 🔌 Bridge | Virtual motion sensors for Scrypted NVR verified detections • per-camera and Home/Away muting of Scrypted's own notifications (needs a Scrypted bridge script) | 📦 | [Docs](Scrypted) | — |
| **Smart Mailbox** | 🌐 Local | Get notified when you receive mail in your mailbox | 📦 | [Docs](SmartMailbox) | [💬](https://community.hubitat.com/t/release-smart-mailbox/11402) |

## 📍 Presence & Location

| Package | Type | Key Features | HPM | Docs | Community |
| --- | --- | --- | :---: | --- | --- |
| **Geofency Presence** | ☁️ Cloud | Set presence from the Geofency iOS app | 📦 | [Docs](Geofency-Presence) | [💬](https://community.hubitat.com/t/release-geofency-presence/22788) |
| **OwnTracks Presence** | ☁️ Cloud | Set presence from the OwnTracks iOS/Android app | 📦 | [Docs](OwnTracks-Presence) | [💬](https://community.hubitat.com/t/release-owntracks-presence/53419) |
| **BTLE Multi-User Presence** | 🔌 Bridge | Bluetooth LE presence (Tile Mate and similar beacons) for multiple users via a Python script | ➖ | [Docs](BTLE-Presence) | [💬](https://community.hubitat.com/t/release-btle-presence-sensor/48332) |
| **GeoHopper Presence** | 🌐 Local | Multi-user virtual presence updater for the GeoHopper app | ➖ | [Docs](Geohopper-Presence) | — |

## 🚗 Vehicles

| Package | Type | Key Features | HPM | Docs | Community |
| --- | --- | --- | :---: | --- | --- |
| **Volvo Connect** | ☁️ Cloud | Lock/unlock • battery, fuel & range • charging status with start/stop/limit-reached notifications and finish-time estimate • doors, windows, tyres, odometer • GPS location • engine/climate/honk commands (some need Volvo API approval) | 📦 | [Docs](Volvo) | — |

## 🏊 Pool, Water & Irrigation

| Package | Type | Key Features | HPM | Docs | Community |
| --- | --- | --- | :---: | --- | --- |
| **WaterGuru Integration** | ☁️ Cloud | Chlorine, pH, flow rate and temperature from your pool's WaterGuru sensor | 📦 | [Docs](WaterGuru) | [💬](https://community.hubitat.com/t/release-waterguru-pool-integration/77922) |
| **Tuya Zigbee Valve (SONOFF SWV-ZF2)** | 🌐 Local | Dual-port Zigbee water valve fork with a working `openFor(duration)` command | ➖ | [Docs](Tuya-Zigbee-Valve) | [💬](https://community.hubitat.com/t/sonoff-zigbee-sprinklers-on-pre-order-sale/162398) |
| **Raincloud Valve** | 🔌 Bridge | Melnor Raincloud watering valves via a small Python Flask bridge | ➖ | [Docs](Raincloud) | — |
| **Simple Irrigation** | 🌐 Local | Parent/child irrigation app for hose-timer style valves, multiple timers and restrictions (based on work by @bptworld) | ➖ | [Folder](SimpleIrrigation) | — |
| **Arduino Pool Controller** | 🔌 Bridge | ST_Anything-based Pentair SuperFlo VS pump speed and pool light control | ➖ | [Docs](Arduino-Pool) | — |

## 🏠 Control & Appliances

| Package | Type | Key Features | HPM | Docs | Community |
| --- | --- | --- | :---: | --- | --- |
| **Wyze Vacuum Connect** | ☁️ Cloud | Wyze Robot Vacuums (200S/JA_RO2) • room-based rotation cleaning • room timing learning mode • status monitoring • no external server | 📦 | [Docs](Wyze-Vacuum) | [💬](https://community.hubitat.com/t/release-wyze-vacuum-integration-cloud/165866) |
| **Genmon Generator Monitor** | 🔌 Bridge | Monitor and control your generator through Genmon | 📦 | [Docs](Genmon) | [💬](https://community.hubitat.com/t/genmon-generator-monitor/164362) |
| **Neosmart Blinds** | 🔌 Bridge | Control NeoSmart blinds (requires the NeoSmart controller) | 📦 | [Docs](NeoSmart) | [💬](https://community.hubitat.com/t/neo-smart-controller-for-blinds/26391) |
| **Hugo Button Remote** | 🌐 Local | 4-button ESP8266 WiFi remote (7 combinations) integrated with Hubitat — used to control blinds | ➖ | [Docs](Hugo) | — |
| **Fireplace Controller** | 🌐 Local | ~$12 Wemos D1 relay to control a fireplace from Hubitat and Alexa | ➖ | [Docs](Fireplace) | — |
| **Garage Controller** | 🌐 Local | ST_Anything / Wemos D1 garage door controller with local control and no MyQ cloud | ➖ | [Docs](Garage) | [💬](https://community.hubitat.com/t/yet-another-garage-door-controller-w-hubduino/43544) |
| **Honeywell TCC (IFTTT)** | ☁️ Cloud | Control a Honeywell Total Connect Comfort thermostat through IFTTT webhooks | ➖ | [Docs](HoneywellTCC) | [💬](https://community.hubitat.com/t/honeywell-tcc-ifttt-driver/10385) |
| **MyQ Lite** | ☁️ Cloud | MyQ garage door opener app and drivers — **unmaintained**, see the README for the current maintained version | ➖ | [Docs](MyQ) | [💬](https://community.hubitat.com/t/updated-myq-lite-application/31318) |

## ⛽ Propane

| Package | Type | Key Features | HPM | Docs | Community |
| --- | --- | --- | :---: | --- | --- |
| **Otodata Propane Receiver** | 🔌 Bridge | Propane tank level from an Otodata receiver (uses the [otodata-genmon](https://github.com/bdwilson/otodata-genmon) bridge) | ➖ | [Folder](Otodata-Propane) | — |
| **ESP32 Propane Sensor** | 🔌 Bridge | Propane tank level from a DIY ESP32 sensor | ➖ | [Folder](ESP32_Propane) | — |

## 🌤️ Weather

| Package | Type | Key Features | HPM | Docs | Community |
| --- | --- | --- | :---: | --- | --- |
| **Weewx Local Capabilities** | 🌐 Local | Outdoor temp, humidity, wind, pressure and detailed rain totals from Weewx • wet/dry switch for use with Simple Irrigation | 📦 | [Docs](Weewx) | [💬](https://community.hubitat.com/t/release-weewx-local-capabilities/17709) |

## 🧰 Utilities & Notifications

| Package | Type | Key Features | HPM | Docs | Community |
| --- | --- | --- | :---: | --- | --- |
| **SendGrid** | ☁️ Cloud | Notification driver for the SendGrid email service (100 messages/day on the free plan) | 📦 | [Docs](https://community.hubitat.com/t/release-sendgrid-email-notification-plugin/48691) | [💬](https://community.hubitat.com/t/release-sendgrid-email-notification-plugin/48691) |
| **Smarter Bulbs** | 🌐 Local | Keep track of the state of your smart bulbs through a power outage | 📦 | [Docs](SmarterBulbs) | [💬](https://community.hubitat.com/t/power-failure-recovery-via-rm/4298/12) |
| **Toggle API** | 🌐 Local | Toggle devices that don't natively support a toggle command | 📦 | [Docs](Toggle-API) | — |
| **Notify If Motion** | 🔌 Bridge | POST to an endpoint URL you configure when motion is detected | ➖ | [Folder](NotifyIfMotion) | — |

## 📂 Miscellaneous Drivers

[`Drivers/`](Drivers) holds assorted drivers (HEM v1/v2, WAPIRZ-1, contact sensor, Genmon, virtual mobile presence, Z-Wave metering switch). These are **not owned or maintained by me** — I may have modified them to work with Hubitat, but they come with no guarantee.

---

## Notes

- **Tuya Zigbee Valve** has a `packageManifest.json` but is not yet listed in `repository.json`, so it won't appear in HPM's keyword search until it is; install it from its folder in the meantime.
- Packages without a Community thread yet (**Scrypted Connect**, **Volvo Connect**, **Toggle API**) will be linked here once a release thread exists.
