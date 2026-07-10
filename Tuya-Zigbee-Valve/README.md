# Tuya Zigbee Valve (dual-port fork)

Vendored copy of [kkossev/Hubitat "Tuya Zigbee Valve"](https://github.com/kkossev/Hubitat/blob/development/Drivers/Tuya%20Zigbee%20Valve/Tuya%20Zigbee%20Valve.groovy)
(Apache License 2.0, license header preserved in the driver file), based on
upstream version 1.7.1, with added support for the **SONOFF SWV-ZF2 (Hydro DUO)**
dual-channel / dual-port Zigbee water valve.

## What's added

The stock driver only ever talks to a single Zigbee endpoint (01). The
SWV-ZF2 exposes two independent endpoints (01 and 02), each with its own
`genOnOff` (cluster `0006`) — confirmed against a real device (`Ep List:
["01","02"]`, endpoint 01 `inClusters: 0000,0006` / `outClusters: 0003,0019`,
manufacturer `SONOFF`, model `SWV-ZF2`), the same layout Hubitat's built-in
"Generic Zigbee Multi-Endpoint Switch" driver already controls successfully
for both ports.

Changes in this fork (v1.8.0):

- New `SONOFF_SWV_ZF2_VALVE` device profile + `isSonoffZF2()` detection.
- `open2()` / `close2()` / `on2()` / `off2()` commands that target endpoint 02
  directly (raw `he cmd` to cluster `0x0006`), independent of the primary
  `open()`/`close()`/`valve`/`switch` commands and attributes (port 1).
- The existing `valve2` attribute and `setValve2()` command (previously
  GiEX/TZE284-only, driven over Tuya DP) are generalized to also drive
  SWV-ZF2 port 2 over standard Zigbee.
- Incoming `genOnOff` reports from endpoint 02 are routed to `valve2` (not
  the primary `valve`/`switch`) by checking `sourceEndpoint`/`endpoint`
  before the generic switch-event parsing runs, since Hubitat's
  `zigbee.getEvent()` does not discriminate by endpoint on its own.
- `configure()`/`refresh()` bind, configure reporting, and read `genOnOff`
  on endpoint 02 as well as endpoint 01.

See the changelog at the top of the driver file for the full version history
(both upstream and this fork's additions).
