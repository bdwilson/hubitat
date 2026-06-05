# Envisalink Security (Native TPI)

Native Hubitat integration for Honeywell/Ademco Vista panels via EnvisaLink EVL-3/EVL-4.

**No SmartThings Node Proxy required.** This integration connects directly from your Hubitat hub to your EnvisaLink device over TCP/TPI.

> The previous proxy-based integration lives in the `Honeywell/` folder and continues to work if you prefer it.

## How It Works

The Hubitat hub opens a persistent TCP telnet connection to the EnvisaLink device (port 4025). The EnvisaLink pushes `%00` Virtual Keypad Update messages continuously; the driver parses those to derive zone open/closed/alarm state and partition arm state, then updates child devices accordingly. Arm/disarm commands are sent as keypad keystrokes (`{code}2` = Arm Away, `{code}1` = Disarm, etc.) exactly as the original STNP plugin did.

## Requirements

- EnvisaLink EVL-3 or EVL-4
- Honeywell/Ademco Vista panel (tested patterns: Vista 20P)
- Hubitat hub on the same LAN as the EnvisaLink (or routed LAN)
- Static IP assigned to your EnvisaLink (via DHCP reservation or manual config)

## Installation

### Drivers and App (manual)

1. In Hubitat → **Drivers Code**, add each of the following (copy/paste raw file contents):
   - `Envisalink_Connection.groovy`
   - `Envisalink_Partition.groovy`
   - `Envisalink_Zone_Contact.groovy`
   - `Envisalink_Zone_Motion.groovy`
   - `Envisalink_Zone_Smoke.groovy`
   - `Envisalink_Zone_Water.groovy`
   - `Envisalink_Zone_CO.groovy`

2. In Hubitat → **Apps Code**, add `Envisalink_App.groovy`.

3. Go to **Apps → + Add User App → Envisalink Security** and configure it.

### HPM (Hubitat Package Manager)

Install via HPM using the manifest at:
`https://raw.githubusercontent.com/bdwilson/hubitat/master/Envisalink/packageManifest.json`

## Configuration

In the **Envisalink Security** app:

| Field | Description |
|---|---|
| EnvisaLink IP Address | LAN IP of your EVL-3/EVL-4 |
| EnvisaLink Port | Default `4025` |
| Network Password | EVL network password (default `user`) |
| Security Code | Your panel arm/disarm code |
| Number of zone slots | How many zone config rows to show |
| Zone Name / Zone Number / Type | One row per zone you want to monitor |
| Integrate with HSM | Bi-directional sync with Hubitat Safety Monitor |

Zone types: `Contact`, `Motion`, `Smoke`, `Water`, `CO`

Zones not listed in the app config are silently ignored — you don't need to list every zone if you only want to monitor some.

## Device Hierarchy

```
Envisalink Security (App)
└── Envisalink Connection (Driver — holds TCP socket)
    ├── Security Panel (Envisalink Partition driver)
    └── Zone 1 ... Zone N  (zone drivers)
```

The **Security Panel** device has arm/disarm/chime/bypass buttons. Zone devices expose standard Hubitat capabilities (Contact Sensor, Motion Sensor, etc.) so they work with all existing rules and dashboards.

## Arm/Disarm Keystrokes

| Action | TPI sequence |
|---|---|
| Arm Away | `{code}2` |
| Arm Stay | `{code}3` |
| Arm Instant | `{code}7` |
| Disarm | `{code}1` (sent twice for Vista reliability) |
| Chime toggle | `{code}9` |
| Bypass zones | `{code}6{zero-padded zones}` |
| Trigger output 17 | `{code}#717` → off after 2s |
| Trigger output 18 | `{code}#718` → off after 2s |

## Troubleshooting

- **`connectionStatus` shows `login failed`** — check your EnvisaLink network password in app/device settings.
- **Zones never open/close** — enable debug logging on the Envisalink Connection device and watch logs during a zone trigger. If `%00` messages appear but zones don't update, check that zone numbers in the app match your actual panel zone numbers.
- **Arm Away becomes Arm Stay** — this is a Vista panel feature ("Auto-Stay"). If no entry/exit door opens during the exit delay, the panel converts Arm Away to Arm Stay. [See this FAQ](https://www.alarmgrid.com/faq/how-do-i-disable-auto-stay-arming-on-a-honeywell-vista-system).
- **Lost connection** — the driver reconnects automatically within ~10 seconds. Check Hubitat logs for reconnect events.

## Known Limitations

- Single partition only
- No siren/strobe capability (Vista panels don't expose this easily via TPI keystrokes)
- Trigger output commands (#717/#718) — may need adjustment depending on your Vista model and output programming

## Credits

- Original SmartThings/Node Proxy integration: [redloro](https://github.com/redloro/smartthings)
- Hubitat proxy-based port: bubba@bubba.org / [brianwilson](https://community.hubitat.com/u/brianwilson)
- Native TPI approach informed by [hubitat_envisalink](https://github.com/bdwilson/hubitat_envisalink) (Doug Beard / Brian Wilson)
