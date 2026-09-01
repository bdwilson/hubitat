/**
 *  Hubitat Import URL: https://raw.githubusercontent.com/bdwilson/hubitat/claude/laundry-monitor-calibration-53grlv/Laundry-Monitor/LaundryMonitor-App.groovy
 */

/**
 * Laundry Monitor & Logger
 *
 * Version: 1.0.0 - Brian Wilson
 *
 * Washer (power meter) + Dryer (vibration/acceleration sensor) cycle detection,
 * built for a dryer with no power monitoring available - vibration is the only
 * signal, so this app leans on data logging to make it tunable over time.
 *
 * Design notes:
 *  - Washer start/stop logic (start/stop thresholds, wait-before-counting,
 *    minimum end-detect window, sequential/continuous-minutes end debounce,
 *    ignore-threshold, deadman timer) is modeled on the community "Better
 *    Laundry Monitor" app (HubitatCommunity/Hubitat-BetterLaundryMonitor),
 *    since that's a well-worn, well-understood algorithm. Defaults here are
 *    pre-tuned from a ~30 day calibration pass against real usage logs rather
 *    than the community app's generic defaults.
 *  - Dryer start/stop uses simple active/N-sequential-inactive vibration
 *    debouncing, same as that app's "Sequence Vibration Sensor" mode.
 *  - Optional washer-cross-talk suppression: a vibration sensor mounted near
 *    a washer very often reports "active" purely from the washer running,
 *    not the dryer. When enabled, dryer vibration is ignored while the
 *    washer is actively cycling (plus a short grace period after), which a
 *    calibration pass found accounted for roughly a third of logged "dryer"
 *    cycles being pure washer bleed-through.
 *  - Every raw washer power reading and every raw dryer active/inactive
 *    vibration report is appended to a capped, persistent log (state), along
 *    with a separate log of completed cycle summaries (start/end/duration/
 *    peak power/how it ended). Both are viewable and exportable as CSV from
 *    the app's "Data Log" page so thresholds can be re-tuned later from real
 *    data instead of guesswork - this is the main point of the app.
 *  - All outward-facing actions (push/speech notifications, a reminder after
 *    the washer finishes, follower switches) are OFF by default. Turn them
 *    on individually once you're happy with detection behavior.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at:
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

definition(
    name: "Laundry Monitor & Logger",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Washer (power) + Dryer (vibration) cycle detection with built-in start/stop data logging for calibration.",
    category: "Convenience",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/claude/laundry-monitor-calibration-53grlv/Laundry-Monitor/LaundryMonitor-App.groovy",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page(name: "mainPage")
    page(name: "dataPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Laundry Monitor & Logger", install: true, uninstall: true) {
        section("<b>Devices</b>") {
            input "washerPowerMeter", "capability.powerMeter", title: "Washer power meter", required: true, multiple: false, submitOnChange: true
            input "dryerVibrationSensor", "capability.accelerationSensor", title: "Dryer vibration/acceleration sensor", required: true, multiple: false, submitOnChange: true
        }

        section("<b>Washer - Power Thresholds</b>", hideable: true, hidden: false) {
            paragraph "Defaults below come from a calibration pass against ~30 days of real usage. See the README before changing them."
            input "washerStartWaitMin", "number", title: "Time (minutes) to wait before counting the power threshold (helps with brief startup blips)", required: false, defaultValue: 2
            input "washerStartW", "decimal", title: "Start cycle when power (W) rises above", required: false, defaultValue: 5
            input "washerMinEndMin", "number", title: "Minimum minutes after start before end detection begins", required: false, defaultValue: 10
            input "washerStopW", "decimal", title: "Stop cycle when power (W) drops below", required: false, defaultValue: 3
            input "washerStopReadings", "number", title: "Stop after power is below threshold for this many sequential readings", required: false, defaultValue: 2
            input "washerStopMinutes", "number", title: "Also require this many continuous minutes below threshold before stopping (0 = off)", required: false, defaultValue: 0
            input "washerIgnoreW", "decimal", title: "Ignore extraneous power (W) readings above (spike filter)", required: false, defaultValue: 1500
            input "washerDeadmanMin", "number", title: "Maximum cycle time in minutes (deadman timer, force-ends a stuck cycle)", required: false, defaultValue: 90
        }

        section("<b>Dryer - Vibration Thresholds</b>", hideable: true, hidden: false) {
            input "dryerStopReadings", "number", title: "Stop after no vibration for this many sequential reportings", required: false, defaultValue: 2
            input "dryerDeadmanMin", "number", title: "Maximum cycle time in minutes (deadman timer, force-ends a stuck cycle)", required: false, defaultValue: 90
        }

        section("<b>Washer/Dryer Cross-talk</b>", hideable: true, hidden: false) {
            paragraph "A vibration sensor near the washer can easily pick up the washer running and misreport it as a dryer cycle. This suppresses that."
            input "suppressCrossTalk", "bool", title: "Ignore dryer vibration while the washer is actively running", required: false, defaultValue: true, submitOnChange: true
            if (suppressCrossTalk) {
                input "crossTalkGraceMin", "number", title: "Also suppress for this many minutes after the washer stops", required: false, defaultValue: 2
            }
        }

        section("<b>Notifications (off by default)</b>", hideable: true, hidden: true) {
            input "enableStartNotify", "bool", title: "Notify when a cycle starts", required: false, defaultValue: false, submitOnChange: true
            input "enableDoneNotify", "bool", title: "Notify when a cycle finishes", required: false, defaultValue: false, submitOnChange: true
            if (enableStartNotify || enableDoneNotify) {
                input "notifyDevices", "capability.notification", title: "Send via", multiple: true, required: false
                input "speechDevices", "capability.speechSynthesis", title: "Speak via", multiple: true, required: false
            }
            if (enableStartNotify) {
                input "washerStartMessage", "text", title: "Washer started message", required: false, defaultValue: "Washer started"
                input "dryerStartMessage", "text", title: "Dryer started message", required: false, defaultValue: "Dryer started"
            }
            if (enableDoneNotify) {
                input "washerDoneMessage", "text", title: "Washer done message", required: false, defaultValue: "Washer is done"
                input "dryerDoneMessage", "text", title: "Dryer done message", required: false, defaultValue: "Dryer is done"
            }
            input "enableConcurrentLoadNotify", "bool", title: "Notify when the washer starts again while the dryer is still running (second load heads-up)", required: false, defaultValue: false, submitOnChange: true
            if (enableConcurrentLoadNotify) {
                input "concurrentLoadMessage", "text", title: "Second-load message", required: false, defaultValue: "Washer started again - the dryer is still running the previous load"
            }
            input "enableReminder", "bool", title: "Send a reminder if the washer finishes and nobody moves the load", required: false, defaultValue: false, submitOnChange: true
            if (enableReminder) {
                input "reminderMinutes", "number", title: "Reminder delay (minutes after washer done)", required: false, defaultValue: 15
                input "washerReminderMessage", "text", title: "Reminder message", required: false, defaultValue: "Reminder: the washer is still waiting to be moved to the dryer"
            }
            input "switchList", "capability.switch", title: "Follower switch(es) - on during a cycle, off when it ends", multiple: true, required: false
        }

        section("<b>Data Log</b>") {
            paragraph "Every washer power reading and every dryer active/inactive vibration report is saved so thresholds can be re-tuned from real data later."
            input "maxRawLogEntries", "number", title: "Max raw readings to retain", required: false, defaultValue: 3000
            input "maxCycleLogEntries", "number", title: "Max cycle summaries to retain", required: false, defaultValue: 300
            href "dataPage", title: "View / Export Data Log", description: "Stored: ${(state.rawLog ?: []).size()} raw readings, ${(state.cycleLog ?: []).size()} cycle summaries"
        }

        section("<b>Manual Reset (testing/calibration)</b>", hideable: true, hidden: true) {
            input "resetWasherButton", "button", title: "Force-end washer cycle", backgroundColor: "Crimson", textColor: "white", submitOnChange: true
            input "resetDryerButton", "button", title: "Force-end dryer cycle", backgroundColor: "Crimson", textColor: "white", submitOnChange: true
        }

        section("<b>Logging</b>") {
            input "debugEnable", "bool", title: "Enable verbose debug logging (auto-disables after 30 min)", required: false, defaultValue: false, submitOnChange: true
            input "txtEnable", "bool", title: "Enable descriptive (info) logging", required: false, defaultValue: true
        }

        section("") {
            paragraph "Washer: ${state.washerOn ? 'running' : 'idle'}${state.washerCycleStart ? " (since ${new Date(state.washerCycleStart as Long).format('MM-dd h:mma')})" : ''}"
            paragraph "Dryer: ${state.dryerOn ? 'running' : 'idle'}${state.dryerCycleStart ? " (since ${new Date(state.dryerCycleStart as Long).format('MM-dd h:mma')})" : ''}"
            if (state.washerOn && state.dryerOn) {
                paragraph "<b>Both running at once - second load in progress.</b>"
            }
        }
    }
}

def dataPage() {
    dynamicPage(name: "dataPage", title: "Laundry Monitor - Data Log") {
        section("<b>Summary</b>") {
            paragraph summaryText()
        }
        section("<b>Raw Readings Log</b>") {
            paragraph "Stored: ${(state.rawLog ?: []).size()} of ${(maxRawLogEntries ?: 3000)} max."
            input "exportRowLimit", "number", title: "Rows to show below (most recent)", required: false, defaultValue: 500, submitOnChange: true
            input "showRawExport", "bool", title: "Show CSV for copy/paste", required: false, defaultValue: false, submitOnChange: true
            if (showRawExport) {
                paragraph "<textarea readonly rows='18' style='width:100%;font-family:monospace;font-size:11px'>${rawLogCsv()}</textarea>"
            }
            input "clearRawLogButton", "button", title: "Clear Raw Log", backgroundColor: "Crimson", textColor: "white", submitOnChange: true
        }
        section("<b>Cycle Summary Log</b>") {
            paragraph "Stored: ${(state.cycleLog ?: []).size()} of ${(maxCycleLogEntries ?: 300)} max."
            input "showCycleExport", "bool", title: "Show CSV for copy/paste", required: false, defaultValue: false, submitOnChange: true
            if (showCycleExport) {
                paragraph "<textarea readonly rows='18' style='width:100%;font-family:monospace;font-size:11px'>${cycleLogCsv()}</textarea>"
            }
            input "clearCycleLogButton", "button", title: "Clear Cycle Log", backgroundColor: "Crimson", textColor: "white", submitOnChange: true
        }
    }
}

private String summaryText() {
    List raw = (state.rawLog instanceof List) ? state.rawLog : []
    List cyc = (state.cycleLog instanceof List) ? state.cycleLog : []
    String oldest = raw ? new Date(raw[0].t as Long).format('yyyy-MM-dd h:mma') : "n/a"
    String newest = raw ? new Date(raw[-1].t as Long).format('yyyy-MM-dd h:mma') : "n/a"
    "Raw readings: ${raw.size()} (${oldest} - ${newest})<br>" +
    "Completed cycles logged: ${cyc.size()}<br>" +
    "Cross-talk suppressions so far: ${state.totalSuppressedCount ?: 0}"
}

/* ---------------- lifecycle ---------------- */

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    unschedule()
}

def initialize() {
    subscribe(washerPowerMeter, "power", washerPowerHandler)
    subscribe(dryerVibrationSensor, "acceleration", dryerAccelHandler)

    if (debugEnable) runIn(1800, logsOff)

    // Reschedule deadman timers for any cycle already in progress so a
    // settings save mid-cycle doesn't silently drop the safety net.
    rescheduleDeadman("washer")
    rescheduleDeadman("dryer")
}

private void rescheduleDeadman(String which) {
    boolean on = which == "washer" ? (state.washerOn as boolean) : (state.dryerOn as boolean)
    if (!on) return
    Integer deadmanMin = (which == "washer" ? washerDeadmanMin : dryerDeadmanMin) as Integer
    if (!deadmanMin) return
    Long startTs = (which == "washer" ? state.washerCycleStart : state.dryerCycleStart) as Long
    if (!startTs) return
    Long remainMs = (deadmanMin * 60000L) - (now() - startTs)
    String handler = which == "washer" ? "washerDeadmanFired" : "dryerDeadmanFired"
    Integer delaySec = remainMs > 0 ? Math.max(1, (remainMs / 1000) as Integer) : 1
    runIn(delaySec, handler, [overwrite: true])
}

def appButtonHandler(String btn) {
    switch (btn) {
        case "clearRawLogButton":
            state.rawLog = []
            break
        case "clearCycleLogButton":
            state.cycleLog = []
            break
        case "resetWasherButton":
            if (state.washerOn) endWasherCycle("manual reset")
            break
        case "resetDryerButton":
            if (state.dryerOn) endDryerCycle("manual reset")
            break
    }
}

def logsOff() {
    app.updateSetting("debugEnable", [value: "false", type: "bool"])
    if (txtEnable) log.info "debug logging auto-disabled after 30 minutes"
}

/* ---------------- washer (power) ---------------- */

def washerPowerHandler(evt) {
    BigDecimal p = safeDecimal(evt.value)
    if (p == null) return
    logRaw("washer", p)

    Long nowTs = now()
    BigDecimal startW = (washerStartW ?: 5) as BigDecimal
    BigDecimal stopW = (washerStopW ?: 3) as BigDecimal
    BigDecimal ignoreW = (washerIgnoreW ?: 1500) as BigDecimal
    Integer stopReadings = (washerStopReadings ?: 2) as Integer
    Integer stopMinutes = (washerStopMinutes ?: 0) as Integer
    Integer minEndMin = (washerMinEndMin ?: 0) as Integer
    Integer startWaitMin = (washerStartWaitMin ?: 0) as Integer

    if (debugEnable) log.debug "washer power=${p}W on=${state.washerOn}"

    if (!state.washerOn) {
        if (p >= startW && p < ignoreW) {
            if (startWaitMin > 0) {
                if (!state.washerPendingSince) {
                    state.washerPendingSince = nowTs
                    if (debugEnable) log.debug "washer power above start threshold, waiting ${startWaitMin}m to confirm"
                    return
                }
                if (nowTs - (state.washerPendingSince as Long) < startWaitMin * 60000L) return
            }
            state.remove("washerPendingSince")
            startWasherCycle(nowTs, p)
        } else if (state.washerPendingSince && p < stopW) {
            if (debugEnable) log.debug "washer power dropped back to idle before start-wait elapsed, cancelling"
            state.remove("washerPendingSince")
        }
        return
    }

    if (p < ignoreW && (state.washerPeakW == null || p > (state.washerPeakW as BigDecimal))) {
        state.washerPeakW = p
    }

    if (p > stopW) {
        state.washerLowCount = 0
        state.remove("washerEndingSince")
        return
    }

    if (minEndMin > 0 && (nowTs - (state.washerCycleStart as Long)) < minEndMin * 60000L) {
        if (debugEnable) log.debug "washer below stop threshold but still within min-end window, ignoring"
        return
    }

    state.washerLowCount = (state.washerLowCount ?: 0) + 1
    if (state.washerLowCount < stopReadings) return

    if (!state.washerEndingSince) state.washerEndingSince = nowTs
    if (stopMinutes > 0 && (nowTs - (state.washerEndingSince as Long)) < stopMinutes * 60000L) return

    endWasherCycle("normal")
}

private void startWasherCycle(Long ts, BigDecimal p) {
    boolean concurrentDryer = state.dryerOn as boolean
    state.washerOn = true
    state.washerCycleStart = ts
    state.washerPeakW = p
    state.washerLowCount = 0
    state.remove("washerEndingSince")
    state.remove("washerPendingSince")
    logCycleEvent("washer", "start", ts, [peakW: p, concurrent: concurrentDryer])
    if (txtEnable) log.info "Washer started (${p}W)${concurrentDryer ? ' - dryer is still running (second load)' : ''}"
    if (washerDeadmanMin) runIn(((washerDeadmanMin as Integer) * 60), "washerDeadmanFired", [overwrite: true])
    if (switchList) switchList*.on()
    if (enableStartNotify) notify(washerStartMessage ?: "Washer started")
    if (concurrentDryer && enableConcurrentLoadNotify) {
        notify(concurrentLoadMessage ?: "Washer started again - the dryer is still running the previous load")
    }
}

private void endWasherCycle(String reason) {
    Long ts = now()
    Long startTs = state.washerCycleStart as Long
    Integer durMin = startTs ? Math.round((ts - startTs) / 60000d) as Integer : 0
    logCycleEvent("washer", "end", ts, [durationMin: durMin, peakW: state.washerPeakW, reason: reason, concurrent: (state.dryerOn as boolean)])
    if (txtEnable) log.info "Washer done after ${durMin} min (peak ${state.washerPeakW}W, ${reason})"
    unschedule("washerDeadmanFired")
    state.washerOn = false
    state.washerCycleEndTs = ts
    state.washerLowCount = 0
    state.remove("washerEndingSince")
    state.remove("washerPendingSince")
    if (switchList) switchList*.off()
    if (enableDoneNotify) notify(washerDoneMessage ?: "Washer is done")
    if (enableReminder) runIn(((reminderMinutes ?: 15) as Integer) * 60, "washerReminderFired", [overwrite: true])
}

def washerDeadmanFired() {
    if (!state.washerOn) return
    if (txtEnable) log.info "Washer deadman timer fired - forcing cycle end"
    endWasherCycle("deadman")
}

def washerReminderFired() {
    if (!state.washerOn) notify(washerReminderMessage ?: "Reminder: the washer is still waiting to be moved to the dryer")
}

/* ---------------- dryer (vibration) ---------------- */

def dryerAccelHandler(evt) {
    boolean active = (evt.value == "active")
    Long nowTs = now()

    // Cross-talk suppression only ever blocks a *new* dryer cycle from being
    // mistaken for washer vibration bleed-through. Once the dryer is
    // genuinely running (state.dryerOn), later washer activity - including a
    // second washer load starting mid-dryer-cycle - must never blind the
    // dryer's own stop detection, or a real finish would sit undetected
    // until the deadman timer force-ends it.
    boolean suppress = false
    if (suppressCrossTalk && !state.dryerOn) {
        if (state.washerOn) {
            suppress = true
        } else {
            Long graceMs = ((crossTalkGraceMin ?: 0) as Integer) * 60000L
            Long endTs = state.washerCycleEndTs as Long
            if (endTs && graceMs > 0 && (nowTs - endTs) < graceMs) suppress = true
        }
    }

    logRaw("dryer", evt.value, suppress)

    if (suppress) {
        state.totalSuppressedCount = (state.totalSuppressedCount ?: 0) + 1
        if (debugEnable) log.debug "dryer vibration '${evt.value}' suppressed (washer cross-talk, dryer not yet running)"
        return
    }

    Integer stopReadings = (dryerStopReadings ?: 2) as Integer
    if (debugEnable) log.debug "dryer vibration=${evt.value} on=${state.dryerOn}"

    if (!state.dryerOn) {
        if (active) startDryerCycle(nowTs)
        return
    }

    if (active) {
        state.dryerLowCount = 0
        return
    }

    state.dryerLowCount = (state.dryerLowCount ?: 0) + 1
    if (state.dryerLowCount >= stopReadings) endDryerCycle("normal")
}

private void startDryerCycle(Long ts) {
    boolean concurrentWasher = state.washerOn as boolean
    state.dryerOn = true
    state.dryerCycleStart = ts
    state.dryerLowCount = 0
    logCycleEvent("dryer", "start", ts, [concurrent: concurrentWasher])
    if (txtEnable) log.info "Dryer started${concurrentWasher ? ' - washer is also running' : ''}"
    if (dryerDeadmanMin) runIn(((dryerDeadmanMin as Integer) * 60), "dryerDeadmanFired", [overwrite: true])
    if (switchList) switchList*.on()
    if (enableStartNotify) notify(dryerStartMessage ?: "Dryer started")
}

private void endDryerCycle(String reason) {
    Long ts = now()
    Long startTs = state.dryerCycleStart as Long
    Integer durMin = startTs ? Math.round((ts - startTs) / 60000d) as Integer : 0
    logCycleEvent("dryer", "end", ts, [durationMin: durMin, reason: reason, concurrent: (state.washerOn as boolean)])
    if (txtEnable) log.info "Dryer done after ${durMin} min (${reason})"
    unschedule("dryerDeadmanFired")
    state.dryerOn = false
    state.dryerLowCount = 0
    if (switchList) switchList*.off()
    if (enableDoneNotify) notify(dryerDoneMessage ?: "Dryer is done")
}

def dryerDeadmanFired() {
    if (!state.dryerOn) return
    if (txtEnable) log.info "Dryer deadman timer fired - forcing cycle end"
    endDryerCycle("deadman")
}

/* ---------------- shared helpers ---------------- */

private void notify(String msg) {
    if (!msg) return
    if (notifyDevices) notifyDevices*.deviceNotification(msg)
    if (speechDevices) speechDevices*.speak(msg)
    if (txtEnable) log.info "notify: ${msg}"
}

private BigDecimal safeDecimal(v) {
    if (v == null) return null
    try {
        return (v as BigDecimal)
    } catch (Exception ignored) {
        return null
    }
}

private void logRaw(String device, value, boolean suppressed = false) {
    List entries = (state.rawLog instanceof List) ? state.rawLog : []
    Map entry = [t: now(), d: device, v: value]
    if (suppressed) entry.s = true
    entries << entry
    Integer maxN = (maxRawLogEntries ?: 3000) as Integer
    while (entries.size() > maxN) entries.remove(0)
    state.rawLog = entries
}

private void logCycleEvent(String device, String phase, Long ts, Map extra) {
    List entries = (state.cycleLog instanceof List) ? state.cycleLog : []
    Map entry = [t: ts, d: device, p: phase] + extra
    entries << entry
    Integer maxN = (maxCycleLogEntries ?: 300) as Integer
    while (entries.size() > maxN) entries.remove(0)
    state.cycleLog = entries
}

private String rawLogCsv() {
    List rows = (state.rawLog instanceof List) ? state.rawLog : []
    Integer lim = (exportRowLimit ?: 500) as Integer
    List shown = (lim > 0 && rows.size() > lim) ? rows[-lim..-1] : rows
    StringBuilder sb = new StringBuilder()
    sb << "timestamp,device,value,suppressed\n"
    shown.each { e ->
        sb << "${new Date(e.t as Long).format('yyyy-MM-dd HH:mm:ss')},${e.d},${e.v},${e.s ? 1 : 0}\n"
    }
    return sb.toString()
}

private String cycleLogCsv() {
    List rows = (state.cycleLog instanceof List) ? state.cycleLog : []
    StringBuilder sb = new StringBuilder()
    sb << "timestamp,device,phase,durationMin,peakW,reason,concurrent\n"
    rows.each { e ->
        sb << "${new Date(e.t as Long).format('yyyy-MM-dd HH:mm:ss')},${e.d},${e.p},${e.durationMin ?: ''},${e.peakW ?: ''},${e.reason ?: ''},${e.concurrent ? 1 : 0}\n"
    }
    return sb.toString()
}
