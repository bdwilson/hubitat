# WaterGuru Integration for Hubitat (v2.0)

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

## Notes

* WaterGuru devices sample a few times per day at most — polling more often than
  every few hours will not yield additional data.
* Notifications (low chlorine, high pH, cassette nearly empty, etc.) are not
  built in. Pull requests welcome.

## Credits

* Original integration by [Brian Wilson](https://github.com/bdwilson)
* Native SRP authentication approach adapted from
  [hubitat-xsense-integration](https://github.com/mathewbeall/hubitat-xsense-integration)
  by Mathew Beall
* v2.0 rewrite with native Cognito/Lambda auth assisted by
  [Claude](https://claude.ai) (Anthropic)
