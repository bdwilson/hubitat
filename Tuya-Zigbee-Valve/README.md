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
- (v1.8.1-1.8.3) Fixed a Groovy parse error from a safe-index (`?[`) operator
  unsupported on Hubitat's platform Groovy version; fixed port-2 FC11 traffic
  (irrigation start/end/schedule-status) incorrectly flipping the primary
  `valve`/`switch` (port 1) instead of `valve2`; fixed port-2 `genOnOff`
  attribute reports being silently dropped because `zigbee.getEvent()` only
  decodes reports for the device's primary endpoint (01) - the value is now
  decoded directly from `descMap.value` instead.
- (v1.9.0-1.9.2) Added parent/child support. For `SONOFF_SWV_ZF2_VALVE`, the
  parent's `configure()` creates two child devices - "Port 1" and "Port 2" -
  each a fully separate, independently controllable device, matching how
  Hubitat's built-in "Generic Zigbee Multi-Endpoint Switch" driver already
  exposed both ports as separate devices. The children are only created if
  the connected device is actually a ZF2.

  The children use a companion driver, **`Tuya Zigbee Valve Port.groovy`**
  (in this same folder - install it too), rather than Hubitat's stock
  "Generic Component Switch", so each port gets its own independent
  `irrigationStartTime`/`irrigationEndTime`/`lastIrrigationDuration`/
  `irrigationVolume`/`lastValveOpenDuration`/`waterConsumed` history instead
  of those being single attributes on the parent that get overwritten by
  whichever port last reported. All Zigbee communication and parsing still
  lives in the parent driver; the children only forward `open()`/`close()`/
  `on()`/`off()`/`refresh()` up (`componentOpen`/`componentClose`/
  `componentOn`/`componentOff`/`componentRefresh` on the parent) and receive
  state pushed back down via `child.parse()`, per Hubitat's standard Generic
  Component driver convention. The parent's own top-level `open()`/`close()`/
  `valve`/`switch` (port 1) and `valve2`/`open2()`/`close2()`/`setValve2`
  (port 2) are unchanged and continue to work alongside the children.

  v1.9.2 also fixed a latent concurrency bug: the FC11 `501F` handler's
  dedup state (`znLastScheduleStatus`/`znDeviceEpochOffset`) was shared
  across both ports, so if port 1 and port 2 transitioned through the same
  schedule status around the same time, one port's timestamp event could be
  silently skipped as a false duplicate of the other's. Each port now has
  its own state key.

**Install order matters**: import `Tuya Zigbee Valve Port.groovy` into
Drivers Code first (so the child driver exists), then import/save
`Tuya Zigbee Valve.groovy` and press **Configure** on the ZF2 device to
create its two child devices.

See the changelog at the top of the driver file for the full version history
(both upstream and this fork's additions).
