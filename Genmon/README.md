# Genmon Generator Monitor — Hubitat Integration

A native Hubitat driver for monitoring and controlling Generac generators via [genmon](https://github.com/jgyates/genmon) - based on [genmon-ha](https://www.github.com/jgyates/genmon-ha)

This integration communicates with the **genhalink** addon running on your genmon instance, providing real-time generator data through a REST + WebSocket API. It works alongside the existing MQTT-based integration — users can choose whichever fits their setup.

## Features

- **60+ sensors**: Battery voltage, RPM, output power, fuel level, run hours, weather, and more
- **8 binary sensors**: Outage detection, generator running, alarm active, utility present, etc.
- **5 control buttons**: Start, Stop, Start Transfer, Start Exercise, Set Time
- **Quiet mode switch**: Toggle quiet mode for Evolution/Nexus controllers
- **Exercise scheduling**: Select day/frequency and set hour/minute via HA UI
- **Automatic discovery**: Zeroconf/mDNS finds your generator on the network
- **WebSocket push updates**: Near-instant state changes without polling delay
- **Diagnostics**: Built-in diagnostics download for troubleshooting
- **Multi-instance**: Supports multiple generators

## Requirements

- **genmon v2+** running on a Raspberry Pi (or similar) connected to your Generac generator
- **genhalink addon for Home Assistant** enabled in genmon (see setup below)
- **Home Assistant** 2024.1.0 or later

## Installation


### Step 1: Enable the genhalink addon in genmon

1. Open the genmon web UI (typically `http://<pi-ip>:8000`)
2. Go to **Settings** (gear icon)
3. Find **Home Assistant Integration (Native)** and enable it
4. An **API Key** is automatically generated — copy it for the next step
5. Click **Save** and the addon will start automatically

![z_20260602_184630_2026-06-02_18-45-21](https://bdwilson.github.io/images/z_20260602_184630_2026-06-02_18-45-21.png)

### Step 2: Add the integration in Hubitat

## Option 1 - Manual

1. In another tab, Go into Hubitat -> Drivers Code -> Import URL: https://raw.githubusercontent.com/bdwilson/hubitat/master/Genmon/genmon-driver.groovy -> Save 
2. Add Device -> Virtual -> Genmon Generator Monitor -> Give it a Name.
3. Go into your new device -> Preferences -> Enter IP address, leave default port, API Key from other tab, leave HTTPS enabled, I'd suggest websocket minimum threshold to 2m to minimize # of events in Hubitat. 
4. Initalize.

## Option 2 - HPM
1. Open Hubitat Package Manager and Search for Genmon -> Install
2. Add Device -> Virtual -> Genmon Generator Monitor -> Give it a Name.
3. Go into your new device -> Preferences -> Enter IP address, leave default port, API Key from other tab, leave HTTPS enabled, I'd suggest websocket minimum threshold to 2m to minimize # of events in Hubitat. 
4. Initalize.

## Options

After setup, you can configure options via the integration's **Configure** button:

| Option | Default | Description |
|--------|---------|-------------|
| Polling interval | 5 seconds | Fallback polling rate. WebSocket push updates are always active. |
| Include monitor stats | Yes | Include CPU usage, comm stats, and other platform metrics |
| Include weather | Yes | Include weather sensors (if configured in genmon) |
| Excluded sensor paths | (empty) | Comma-separated genmon data paths to skip |

## Entities

### Sensors
| Entity | Description | Device Class |
|--------|-------------|-------------|
| Battery Voltage | Generator battery voltage | voltage |
| RPM | Engine RPM | — |
| Output Frequency | Generator output frequency | frequency |
| Output Voltage | Generator output voltage | voltage |
| Output Current | Generator output current | current |
| Output Power | Single-phase output power | power |
| Power Leg 1/2 | Per-leg power (split-phase) | power |
| Current Leg 1/2 | Per-leg current (split-phase) | current |
| Engine State | Current engine state text | — |
| Switch State | Transfer switch position | — |
| Utility Voltage | Incoming utility voltage | voltage |
| Fuel Level | Tank fuel level (%) | — |
| Estimated Fuel | Gallons remaining | — |
| Total Run Hours | Lifetime run hours | duration |
| Energy (30 Days) | kWh generated in last 30 days | energy |
| Weather Temperature | Local weather temp | temperature |
| *...and many more* | Dynamic sensors auto-discovered from your controller | — |

### Binary Sensors
| Entity | Description |
|--------|-------------|
| System In Outage | Utility power lost |
| Generator Running | Engine is running |
| Exercising | Currently in exercise mode |
| Alarm Active | Active alarm present |
| Utility Power Present | Utility power available |
| Switch in Auto | Transfer switch in auto mode |
| Update Available | Genmon update available |

### Controls
| Entity | Type | Description |
|--------|------|-------------|
| Start / Stop | Button | Remote start/stop generator |
| Start Transfer | Button | Start with transfer |
| Start Exercise | Button | Trigger exercise cycle |
| Set Generator Time | Button | Sync generator clock |
| Quiet Mode | Switch | Toggle quiet mode (Evo/Nexus) |
| Exercise Frequency | Select | Weekly/BiWeekly/Monthly |
| Exercise Day of Week | Select | Day selection for exercise |
| Exercise Hour/Minute | Number | Time of day for exercise |

## Troubleshooting

### Cannot connect
- Verify genmon is running: `http://<pi-ip>:8000`
- Verify genhalink addon is enabled in genmon Settings
- Check the port (default `9083`) is accessible from your HA instance
- Check firewall rules on the Pi: `sudo ufw allow 9083/tcp`

### Invalid API key
- Open genmon Settings → Home Assistant Integration (Native)
- Copy the current API key and re-enter it in HA

### Missing entities
- Some entities are controller-specific (Evolution, Nexus, H-100, PowerZone)
- Check Options → "Include monitor stats" / "Include weather" toggles
- Dynamic sensors appear after the first data poll

### Check genhalink logs
On the genmon Pi:
```bash
tail -f /var/log/genhalink.log
```

### Check on the Hubitat community
- You can try the [Hubitat Community](https://community.hubitat.com/t/genmon-generator-monitor/164362) if you have issues. This is not supported by the Genmon authort. 

## License

This integration is not part of the [genmon](https://github.com/jgyates/genmon) project, but is distributed under the same license.

