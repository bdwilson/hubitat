# Simple Irrigation (personal fork)

A personal fork of [BPTWorld's Simple Irrigation](https://github.com/bptworld/Hubitat) parent/child
app pair for Hubitat — multi-schedule, weather-aware watering control for any `capability.valve` or
`capability.switch` device. BPTWorld has since removed their own copy from their repo, so this is kept
here for my own use (and anyone else who finds it useful).

**This is not published to Hubitat Package Manager and is not supported by BPTWorld** — it's a personal
copy with my own changes on top, kept in this repo purely for my own use. Use at your own risk; there's
no guarantee of support.

## What this fork changes on top of BPTWorld's original

- **Configurable OPEN command.** The original hardcoded `valveDevice.open(onLength)` — passing the run
  length as an argument to the standard `open()` command. That breaks against drivers (like this repo's
  [Tuya Zigbee Valve Port](../Tuya-Zigbee-Valve/) driver) that only support a plain, zero-argument
  `open()` and expose timed opens as a *separate*, distinctly-named command (`openFor(minutes)`) instead
  — a driver can't safely declare two commands both named `open` with different argument counts (the
  Hubitat device page copes, but Maker API can't resolve which one to call). Under **Open Command** in
  the child app you can now set:
  - **Command to OPEN the valve** — the exact command name to send (defaults to `open`).
  - **Send the run time (minutes) as an argument to that command?** — off by default, matching any
    `capability.valve` device's standard zero-argument `open()`/`close()`. Turn it on for a driver with
    its own timed-open command — e.g. set the command name to `openFor` and enable this.
  - Once a valve device is selected, this section also lists the commands that device actually supports,
    to help you get the name right.
- **Always-on internal shutoff timer.** Regardless of which mode above is used, this app arms its own
  `runIn()` timer to call `close()` once the scheduled run time elapses. When the open command doesn't
  take a duration, this is the *only* thing that closes the valve. When it does, this is a safety net in
  case the device's own timer doesn't fire — for a water valve, closing twice is harmless; failing to
  close isn't.
- **Bug fix:** `turnValveOff()` used to re-check "does today match the configured watering days" before
  doing anything — so the scheduled safety shutoff (or the day/weather-check-failed path, which both call
  it to make sure the valve is closed) could silently skip closing entirely if it ran past midnight into
  a non-watering day. Closing is now unconditional; day-of-week only ever gates whether watering *starts*.
- **Bug fix:** a typo (`swtichDevice`) in the weather-check-failed log line threw and aborted the rest of
  `turnValveOn()` — including the `turnValveOff()` safety call at the end of that path — whenever weather
  blocked a scheduled run while using Switch mode.

## Install

Install the parent first, then the child (**Apps Code → New App → Import**):

```
https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/claude/optimistic-heisenberg-yso1ot/SimpleIrrigation/Simple_Irrigation-Parent.groovy
https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/claude/optimistic-heisenberg-yso1ot/SimpleIrrigation/Simple_Irrigation-Child.groovy
```

Then **Apps → Add User App → Simple Irrigation** to install the parent, and use its **Add a new 'Simple
Irrigation' child** button to create a child instance per valve/switch.

## Configuration

- **Valve Devices** — pick either a `capability.valve` device (default) or a `capability.switch` device.
- **Open Command** — valve mode only; see above.
- **Schedule** — up to 3 start times/day and a per-slot run length (minutes), plus which days of the
  week to water on.
- **Safety Features** — retry counts for open/close before giving up and alerting.
- **Check the Weather** — optional rain/wind/other switch devices that cancel a scheduled run when on.
- **Notification Options** — optional push notifications for info/trouble/safety-override events.

## Known limitation (inherited from upstream, not changed here)

The open/close retry loop sends one more attempt than the configured "Attempts to OPEN"/"Attempts to
CLOSE" count before giving up (an off-by-one in the original retry-count comparison) — cosmetic, left
as-is rather than risking the retry logic for a non-functional discrepancy.

## Credits

Original app by [Bryan Turcotte (@bptworld)](https://github.com/bptworld/Hubitat), licensed Apache 2.0
(see header in each `.groovy` file). This is a personal fork, not affiliated with or supported by
BPTWorld.
