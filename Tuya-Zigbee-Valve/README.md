# Tuya Zigbee Valve (dual-port fork)

Adds a working `openFor(duration)` - "open this valve for N minutes" - on top of
[kkossev/Hubitat "Tuya Zigbee Valve"](https://github.com/kkossev/Hubitat/blob/development/Drivers/Tuya%20Zigbee%20Valve/Tuya%20Zigbee%20Valve.groovy)
(Apache License 2.0, license header preserved in both driver files), for the
**SONOFF SWV-ZF2 (Hydro DUO)** dual-channel Zigbee water valve.

Hubitat Community Link: [https://community.hubitat.com/t/sonoff-zigbee-sprinklers-on-pre-order-sale/162398](https://community.hubitat.com/t/sonoff-zigbee-sprinklers-on-pre-order-sale/162398)

## What this fork adds

Dual-port SWV-ZF2 support (parent/child devices, one per physical valve
output) originated in this fork and has since been merged upstream into
kkossev's own driver - so as of v2.0.0, **the parent driver
(`Tuya Zigbee Valve.groovy`) is upstream's file, unmodified**, except for
which child driver it creates (see below) and its `importUrl`.

The one thing this fork still adds on top: **`openFor(duration)`** - a timed
open, in minutes, on the *component child* device
(`Tuya Zigbee Valve Port.groovy`, namespace `bdwilson` - kept distinct from a
separately-installed real kkossev child driver). It works entirely on the
Hubitat side, with no involvement from the parent driver or the device's own
firmware:

```groovy
void openFor(duration) {
    parent?.componentOpen(device)     // exactly what on() already does
    BigDecimal mins = duration as BigDecimal
    runIn((mins * 60).toLong(), 'autoCloseAfterDuration', [overwrite: true])
}

void close() {
    unschedule('autoCloseAfterDuration')   // cancel a pending timed-close first
    parent?.componentClose(device)
}
```

`close()` unschedules the pending auto-close before closing, so a manual
close doesn't leave a stale timer that could fire later and incorrectly
close an unrelated, still-wanted-open future run. Plain `open()` does the
same, so an explicit indefinite open isn't closed early by a leftover timer.
`on()`/`off()` route through `open()`/`close()` (not directly to the parent)
so this applies consistently no matter which capability (`Valve` or `Switch`)
is used.

### Why `openFor` and not `open(duration)`

Earlier versions declared `command 'open', ['number']` alongside
`capability 'Valve'`. The Valve capability already defines a zero-argument
`open()`, so that registers **two commands sharing one name**.

The Hubitat **device page handles it fine** - it renders an `Open(number)`
field and dispatching from there works. **Maker API does not**: it cannot
resolve which `open` to call, and returns a generic

```json
{"error":true,"type":"java.lang.Exception","message":"An unexpected error occurred."}
```

for `/devices/{id}/open/{minutes}`. That device-page-works /
Maker-API-fails split is what finally identified the cause, after it was
mis-attributed to the parent driver, firmware writes, and timer handling
across several earlier rounds. Two production drivers using the same
`capability "Valve"` + `command "open", ["number"]` shape were previously
cited here as proof the pattern was safe; neither was actually verified
against Maker API, so they didn't rule this out.

Over Maker API, use `/devices/{id}/openFor/{minutes}`.

### Why this is simpler than earlier versions of this fork

Earlier attempts (now reverted) tried to make the **parent** driver handle a
per-run duration - first by writing it to the valve's shared Zigbee firmware
attribute (FC11 `0x501D`, "manual run duration") immediately before opening,
then by having the parent arm a `runIn()` timer itself. Both meant carrying
real, hand-maintained deltas against upstream's parent driver, and neither
one was the cause of the Maker API `500` chased across several rounds of
investigation (that was the duplicate command name, above). This version
drops all of that: the parent is untouched upstream code, and the child's own
`openFor(duration)` is the only place a duration is ever handled - a local
timer on that one device, nothing sent anywhere else.

**Upgrade note:** this fork's child device DNI scheme changed to match
upstream's (`-ZF2-N`, was `-PN`) as part of adopting the upstream parent
driver directly. Upgrading from an older version of this fork orphans any
previously-created child devices - expected, not a bug. Recreate them via
the parent device's `Configure` command after updating.

## Install

Install both files via **Drivers Code → Import** (the child first, since the
parent's `configure()` looks it up by name):

```
https://raw.githubusercontent.com/bdwilson/hubitat/master/Tuya-Zigbee-Valve/Tuya%20Zigbee%20Valve%20Port.groovy
https://raw.githubusercontent.com/bdwilson/hubitat/master/Tuya-Zigbee-Valve/Tuya%20Zigbee%20Valve.groovy
```

Also registered as a [Hubitat Package Manager](https://hubitatpackagemanager.hubitatcommunity.com/)
package (`packageManifest.json` in this folder) for update tracking.

See the changelog block at the top of each driver file for full
version-by-version detail, including this fork's now-reverted earlier
attempts at parent-side duration handling.
