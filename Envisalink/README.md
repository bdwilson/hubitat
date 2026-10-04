# Envisalink Security (Native TPI)

Support / discussion: [Hubitat Community thread](https://community.hubitat.com/t/native-honeywell-vista-envisalink-tpi-alarm-driver/164482)

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
| Partition for arm/disarm, status and HSM | Default `1`. Only change this on a multi-partition panel — see [Multi-Partition Panels](#multi-partition-panels) |
| Number of zone slots | How many zone config rows to show |
| Zone Name / Zone Number / Type | One row per zone you want to monitor |
| Integrate with HSM | Bi-directional sync with Hubitat Safety Monitor |

Zone types: `Contact`, `Motion`, `Smoke`, `Water`, `CO`

Zones not listed in the app config are silently ignored — you don't need to list every zone if you only want to monitor some.

## Multi-Partition Panels

The integration controls and reports **one** partition — partition 1 by default, or the one set in **Partition for arm/disarm, status and HSM** in the app — and works correctly on multi-partition panels.

When a Vista partition is armed, the panel stops reporting faults on that partition's non-alarm zones (e.g. interior motion detectors when armed Stay). A common workaround is to put sensors you want active at all times — like motion detectors that drive lighting — in a second partition that is never armed.

On a multi-partition panel the EnvisaLink sends keypad updates for each partition in turn (roughly every 10 seconds). The driver handles this as follows:

- **Arm status and HSM** — only the selected partition's updates change the **Security Panel** device and HSM, so the state doesn't flap between e.g. partition 1 "armed" and partition 2 "ready".
- **Zones** — each zone belongs to whichever partition reported it open, and a partition's "Ready" update only closes that partition's zones. Nothing to configure.
- **Arm/disarm** — keystrokes always go to the same partition the status comes from. For partition 1 they're sent as plain keystrokes, exactly as in earlier versions. For any other partition each key is sent with the EnvisaLink's "keystroke to partition" command (`^03`), one key at a time, waiting for the EnvisaLink to accept each one. If a key is rejected or gets no reply within 3 seconds, the rest of the command is cancelled and an error is logged.
- Partitions other than the selected one can't be armed, disarmed or monitored for arm state from Hubitat.

## Device Hierarchy

```
Envisalink Security (App)
└── Envisalink Connection (Driver — holds TCP socket)
    ├── Security Panel (Envisalink Partition driver)
    └── Zone 1 ... Zone N  (zone drivers)
```

The **Security Panel** device has arm/disarm/chime/bypass buttons. Zone devices expose standard Hubitat capabilities (Contact Sensor, Motion Sensor, etc.) so they work with all existing rules and dashboards.

### Security Panel Attributes

| Attribute | Values | Use it for |
|---|---|---|
| `panelHSMStatus` | `disarmed`, `armedHome`, `armedAway` | Knowing whether the panel is armed, with no HSM required. Same wording as HSM's own statuses, so a Rule Machine trigger on **Security Panel `panelHSMStatus` changes** (or "is armedAway") is all you need |
| `dscpartition` | `ready`, `notready`, `armedstay`, `armedaway`, `armedinstant`, `armedmax`, `arming`, `alarm`, `alarmcleared` | Finer detail: exit delay (`arming`), alarm, alarm in memory, instant/max modes |
| `panelStatus` | Keypad text, e.g. `DISARMED CHIME Ready to Arm` | Displaying what the keypad shows |

`panelHSMStatus` works whether or not **Integrate with HSM** is turned on. Stay and Instant report `armedHome`; Away and Max report `armedAway`. If the panel converts Arm Away to Arm Stay (see Auto-Stay under Troubleshooting) it reports `armedHome`. During the exit delay it keeps its previous value until the panel finishes arming, and it stays `armedHome`/`armedAway` while an armed panel is in alarm. It only follows the partition selected in the app (see [Multi-Partition Panels](#multi-partition-panels)). After installing or updating it fills in with the panel's next keypad update, usually within about 10 seconds. Update the **Envisalink Connection** and **Envisalink Partition** drivers together; with an older Partition driver the Connection driver logs a one-time warning and the attribute isn't published.

## Arm/Disarm Keystrokes

| Action | TPI sequence |
|---|---|
| Arm Away | `{code}2` |
| Arm Stay | `{code}3` |
| Arm Instant | `{code}7` |
| Disarm | `{code}1` |
| Chime toggle | `{code}9` |
| Bypass zones | `{code}6{zero-padded zones}` |
| Trigger output 17 | `{code}#717` → off after 2s |
| Trigger output 18 | `{code}#718` → off after 2s |

## Troubleshooting

- **App shows a Partition field on each zone slot, or an `addZone` error mentioning 4 arguments** — you have an interim development build of the app. Update the app and Connection driver together (HPM **Repair**, or import both from `master`). Zone partitions are detected automatically; there's nothing to set per zone.
- **`connectionStatus` shows `login failed`** — check your EnvisaLink network password in app/device settings.
- **Zones never open/close** — enable debug logging on the Envisalink Connection device and watch logs during a zone trigger. If `%00` messages appear but zones don't update, check that zone numbers in the app match your actual panel zone numbers.
- **Arm Away becomes Arm Stay** — this is a Vista panel feature ("Auto-Stay"). If no entry/exit door opens during the exit delay, the panel converts Arm Away to Arm Stay. [See this FAQ](https://www.alarmgrid.com/faq/how-do-i-disable-auto-stay-arming-on-a-honeywell-vista-system).
- **Lost connection** — the driver reconnects automatically within ~10 seconds. Check Hubitat logs for reconnect events.

## Known Limitations

- Controls and reports arm state for one partition (see [Multi-Partition Panels](#multi-partition-panels))
- A partition's non-alarm zones don't report while that partition is armed — this is how Vista panels work, not a driver limitation
- No siren/strobe capability (Vista panels don't expose this easily via TPI keystrokes)
- Trigger output commands (#717/#718) — may need adjustment depending on your Vista model and output programming

## Support

Questions and bug reports: [Hubitat Community thread](https://community.hubitat.com/t/native-honeywell-vista-envisalink-tpi-alarm-driver/164482) or a GitHub issue.

## Credits

- Original SmartThings/Node Proxy integration: [redloro](https://github.com/redloro/smartthings)
- Hubitat proxy-based port: bubba@bubba.org / [brianwilson](https://community.hubitat.com/u/brianwilson)
- Native TPI approach informed by [hubitat_envisalink](https://github.com/bdwilson/hubitat_envisalink) (Doug Beard / Brian Wilson)
