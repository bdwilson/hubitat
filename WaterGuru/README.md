# WaterGuru Integration for Hubitat (v2.2)

A native Hubitat integration for WaterGuru pool monitoring devices. No external
server or Python script required — authentication and API calls are handled
entirely within Hubitat.

## Requirements

* A WaterGuru pool device and account
* Hubitat hub

## Installation

Install both files via HPM or manually via **Apps Code** and **Drivers Code**:

* [WaterGuru-Integration.groovy](WaterGuru-Integration.groovy) — install as an App
* [WaterGuru-Driver.groovy](WaterGuru-Driver.groovy) — install as a Driver

Then add the **WaterGuru Integration** app under **Apps**, enter your credentials,
click **Discover**, select your device(s), and configure your poll interval.

## Features

* Native AWS Cognito SRP authentication — no external Python/Flask server needed
* Device discovery UI — select which WaterGuru device(s) to create child devices for
* Configurable poll interval (1, 2, 3, 4, 6, 8, or 12 hours)
* Optional day-of-week poll filter
* Tracks: free chlorine, pH, temperature, flow rate, cassette status/life, battery, RSSI, alerts
* Smart notifications (v2.2) — see below

## Notifications

Notifications are change-driven, not poll-driven: a poll by itself never sends
a notification. Select one or more notification devices (Pushover, the Hubitat
mobile app, etc.) in the app, then tune what you want to hear about:

* **Chemistry alerts** — when a *new sample* arrives (detected via the
  measurement timestamp, not the poll), pH and free chlorine are checked
  against configurable safe ranges (defaults: pH 7.2–7.8, Cl 1.0–3.0 ppm).
  You're alerted once when a value goes out of range and not again until it
  recovers and re-trips.
* **Status alerts** — when the device status, cassette status, or battery
  status transitions away from GREEN, or when a new device alert appears.
* **Consumable/battery thresholds** — when cassette checks-left or battery %
  drops below your threshold (defaults: 10 checks, 20%). Edge-triggered: you
  get one alert, and it re-arms only after replacement.
* **Optional new-sample digest** — a summary of every new reading, off by default.
* **Recovery notifications** — "back to GREEN" / "back in range" messages,
  off by default.
* **Quiet hours** — alerts during the quiet window are held and delivered when
  it ends.
* **Pause switch and test button** — mute everything temporarily, or send a
  test message to verify routing.

Temperature and flow rate are included in alert messages as context but never
trigger a notification themselves. Multiple alerts from the same poll are
coalesced into a single message. The first poll after install records a
baseline silently.

## Notes

* WaterGuru devices sample a few times per day at most — polling more often than
  every few hours will not yield additional data.

## Credits

* Original integration by [Brian Wilson](https://github.com/bdwilson)
* Native SRP authentication approach adapted from
  [hubitat-xsense-integration](https://github.com/mathewbeall/hubitat-xsense-integration)
  by Mathew Beall
* v2.0 rewrite with native Cognito/Lambda auth and v2.2 notifications assisted by
  [Claude](https://claude.ai) (Anthropic)
