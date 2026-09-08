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
            input "washerStopReadings", "number", title: "Stop after power is below threshold for this many sequential readings (fast path, if the meter keeps reporting)", required: false, defaultValue: 2
            input "washerStopMinutes", "number", title: "Also require this many continuous minutes below threshold before stopping (0 = off)", required: false, defaultValue: 0
            input "washerStopConfirmMin", "number", title: "Also confirm stop after this many minutes with no reading back above the stop threshold, even without a second low reading (handles meters that stop reporting once idle; 0 = off)", required: false, defaultValue: 10
            input "washerIgnoreW", "decimal", title: "Ignore extraneous power (W) readings above (spike filter)", required: false, defaultValue: 1500
            input "washerDeadmanMin", "number", title: "Maximum cycle time in minutes (deadman timer, force-ends a stuck cycle)", required: false, defaultValue: 90
        }

        section("<b>Dryer - Vibration Thresholds</b>", hideable: true, hidden: false) {
            paragraph "The sensor stays <i>active</i> for as long as it keeps feeling vibration, so how long it stays active is what separates a real cycle from a bump: in real data, handling/bumps/cross-talk topped out at 68 seconds while every real dryer cycle held active for 28+ minutes."
            input "dryerMinRunMin", "number", title: "Vibration must stay continuously active this many minutes to count as a real cycle", required: false, defaultValue: 3
            input "dryerDeadmanMin", "number", title: "Maximum cycle time in minutes (deadman timer - safety net for a sensor that dies mid-cycle and never reports inactive)", required: false, defaultValue: 120
        }

        section("<b>Washer/Dryer Cross-talk</b>", hideable: true, hidden: true) {
            paragraph "Mostly obsolete: washer vibration bleeding into the dryer sensor only ever produces <i>short</i> active spans, which the minimum-run-time check above already filters. Leaving this off avoids missing real dryer cycles that legitimately run at the same time as a washer load."
            input "suppressCrossTalk", "bool", title: "Also refuse to start a dryer cycle while the washer is running", required: false, defaultValue: false, submitOnChange: true
        }

        boolean anyNotifyEnabled = (enableStartNotify || enableDoneNotify || enableReminder || enableConcurrentLoadNotify) as boolean
        section("<b>Notifications (off by default)</b>", hideable: true, hidden: !anyNotifyEnabled) {
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
    "Short dryer vibration blips ignored (bumps/handling): ${state.dryerIgnoredBlips ?: 0}<br>" +
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

// Bump this when a new version has to change an existing install's stored
// settings. Pasting new code into Hubitat does NOT do that on its own:
// settings whose input is gone stay stored forever, and a changed
// defaultValue only applies to a setting that has never been set. So
// without this, an existing install keeps running on its old values and
// silently ignores the new defaults.
private static String settingsVersion() { return "2" }

private void migrateSettings() {
    if (state.settingsVersion == settingsVersion()) return
    List changes = []

    // v2: dryer detection was rewritten around how long the vibration
    // sensor stays continuously active. These drove the old
    // report-counting / burst-confirmation logic and now do nothing.
    ["dryerStartReports", "dryerStartWindowMin", "dryerConfirmGapMin",
     "dryerConfirmExpireMin", "dryerStopReadings", "dryerStopConfirmMin",
     "crossTalkGraceMin"].each { String old ->
        if (settings[old] != null) {
            try {
                app.removeSetting(old)
                changes << "retired ${old}"
            } catch (Exception ignored) {
                // older platform without removeSetting - harmless, the
                // value just sits there unused
            }
        }
    }

    if (dryerMinRunMin == null) {
        app.updateSetting("dryerMinRunMin", [value: "3", type: "number"])
        changes << "dryerMinRunMin=3"
    }

    // The deadman is only a safety net now (for a sensor that dies
    // mid-cycle and never reports inactive), not part of normal detection.
    // A real 65.8-minute cycle has been observed, so anything near an hour
    // would truncate real cycles.
    if ((dryerDeadmanMin ?: 0) < 120) {
        app.updateSetting("dryerDeadmanMin", [value: "120", type: "number"])
        changes << "dryerDeadmanMin=120"
    }

    // Cross-talk only ever produces short active spans, which the minimum
    // run time already filters. Leaving this on only risks missing real
    // dryer cycles that legitimately overlap a washer load.
    if (suppressCrossTalk) {
        app.updateSetting("suppressCrossTalk", [value: "false", type: "bool"])
        changes << "suppressCrossTalk=off"
    }

    state.settingsVersion = settingsVersion()
    if (changes) log.info "Laundry Monitor: applied v${settingsVersion()} settings (${changes.join(', ')})"
}

def initialize() {
    migrateSettings()

    subscribe(washerPowerMeter, "power", washerPowerHandler)
    subscribe(dryerVibrationSensor, "acceleration", dryerAccelHandler)

    if (debugEnable) runIn(1800, logsOff)

    // Reschedule deadman and stop-confirm timers for any cycle already in
    // progress so a settings save mid-cycle doesn't silently drop them.
    rescheduleDeadman("washer")
    rescheduleDeadman("dryer")
    rescheduleStopConfirm("washer")
    rescheduleStopConfirm("dryer")

    // Same for a dryer vibration burst still building toward the minimum
    // continuous run time.
    if (state.dryerActiveSince && !state.dryerOn) {
        Integer minRunMin = (dryerMinRunMin ?: 3) as Integer
        Long remainMs = (minRunMin * 60000L) - (now() - (state.dryerActiveSince as Long))
        Integer delaySec = remainMs > 0 ? Math.max(1, (remainMs / 1000) as Integer) : 1
        runIn(delaySec, "dryerMinRunFired", [overwrite: true])
    }
}

private void rescheduleDeadman(String which) {
    boolean on = which == "washer" ? (state.washerOn as boolean) : (state.dryerOn as boolean)
    if (!on) return
    Long startTs = (which == "washer" ? state.washerCycleStart : state.dryerCycleStart) as Long
    if (!startTs) return
    armDeadman(which, startTs)
}

private void rescheduleStopConfirm(String which) {
    if (which != "washer") return
    Long endingSince = state.washerEndingSince as Long
    if (!endingSince) return
    Integer confirmMin = washerStopConfirmMin as Integer
    if (!confirmMin) return
    String handler = "washerStopConfirmFired"
    Long remainMs = (confirmMin * 60000L) - (now() - endingSince)
    Integer delaySec = remainMs > 0 ? Math.max(1, (remainMs / 1000) as Integer) : 1
    runIn(delaySec, handler, [overwrite: true])
}

// Deadman timers are always measured from the cycle's recorded/confirmed
// start (not "now"), so a start that's only confirmed a few minutes after
// the fact (start-wait windows, multi-report confirmation) doesn't quietly
// stretch the deadman past the configured cap, and the logged cycle
// duration lines up with the configured deadman minutes when it fires.
private void armDeadman(String which, Long startTs) {
    Integer deadmanMin = (which == "washer" ? washerDeadmanMin : dryerDeadmanMin) as Integer
    if (!deadmanMin) return
    String handler = which == "washer" ? "washerDeadmanFired" : "dryerDeadmanFired"
    Long remainMs = (deadmanMin * 60000L) - (now() - startTs)
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
            if (state.washerOn) endWasherCycle("manual reset", now())
            break
        case "resetDryerButton":
            if (state.dryerOn) endDryerCycle("manual reset", now())
            state.remove("dryerActiveSince")
            unschedule("dryerMinRunFired")
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
            Long trueStart = (state.washerPendingSince ?: nowTs) as Long
            state.remove("washerPendingSince")
            startWasherCycle(trueStart, p)
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
        if (state.washerEndingSince) {
            unschedule("washerStopConfirmFired")
            state.remove("washerEndingSince")
        }
        return
    }

    if (minEndMin > 0 && (nowTs - (state.washerCycleStart as Long)) < minEndMin * 60000L) {
        if (debugEnable) log.debug "washer below stop threshold but still within min-end window, ignoring"
        return
    }

    state.washerLowCount = (state.washerLowCount ?: 0) + 1

    // A power meter that only reports on change may never send a second low
    // reading once it settles at idle - "N sequential readings" can then
    // never be satisfied. Arm a quiet-timeout confirmation alongside the
    // reading-count fast path, so the cycle still ends even if nothing else
    // ever arrives.
    if (!state.washerEndingSince) {
        state.washerEndingSince = nowTs
        Integer confirmMin = (washerStopConfirmMin ?: 0) as Integer
        if (confirmMin > 0) runIn(confirmMin * 60, "washerStopConfirmFired", [overwrite: true])
    }

    if (state.washerLowCount < stopReadings) return
    if (stopMinutes > 0 && (nowTs - (state.washerEndingSince as Long)) < stopMinutes * 60000L) return

    endWasherCycle("normal", state.washerEndingSince as Long)
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
    armDeadman("washer", ts)
    if (switchList) switchList*.on()
    if (enableStartNotify) notify(washerStartMessage ?: "Washer started")
    if (concurrentDryer && enableConcurrentLoadNotify) {
        notify(concurrentLoadMessage ?: "Washer started again - the dryer is still running the previous load")
    }
}

private void endWasherCycle(String reason, Long endTs) {
    Long ts = endTs ?: now()
    Long startTs = state.washerCycleStart as Long
    Integer durMin = startTs ? Math.round((ts - startTs) / 60000d) as Integer : 0
    logCycleEvent("washer", "end", ts, [durationMin: durMin, peakW: state.washerPeakW, reason: reason, concurrent: (state.dryerOn as boolean)])
    if (txtEnable) log.info "Washer done after ${durMin} min (peak ${state.washerPeakW}W, ${reason})"
    unschedule("washerDeadmanFired")
    unschedule("washerStopConfirmFired")
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
    endWasherCycle("deadman", now())
}

def washerStopConfirmFired() {
    if (!state.washerOn || !state.washerEndingSince) return
    if (txtEnable) log.info "Washer stop confirmed by quiet timeout (no reading above ${washerStopW}W in ${washerStopConfirmMin} min)"
    endWasherCycle("normal", state.washerEndingSince as Long)
}

def washerReminderFired() {
    if (!state.washerOn) notify(washerReminderMessage ?: "Reminder: the washer is still waiting to be moved to the dryer")
}

/* ---------------- dryer (vibration) ---------------- */

def dryerAccelHandler(evt) {
    boolean active = (evt.value == "active")
    Long nowTs = now()
    logRaw("dryer", evt.value, false)

    Integer minRunMin = (dryerMinRunMin ?: 3) as Integer
    if (debugEnable) log.debug "dryer vibration=${evt.value} on=${state.dryerOn} activeSince=${state.dryerActiveSince}"

    // This sensor latches "active" for as long as it keeps feeling vibration
    // and only reports "inactive" once the shaking actually stops. That makes
    // the length of a continuous active span the signal that matters - and it
    // separates cleanly: across two days of real data, every bump, door slam,
    // unloading and bit of washer cross-talk produced an active span of 68
    // seconds or less, while every real dryer cycle held active for 28-66
    // minutes. So: stay active past the minimum run time and it's a real
    // cycle; go inactive before that and it was just handling.
    if (active) {
        if (state.dryerOn) return
        if (!state.dryerActiveSince) {
            state.dryerActiveSince = nowTs
            runIn(minRunMin * 60, "dryerMinRunFired", [overwrite: true])
            if (debugEnable) log.debug "dryer vibration started, needs ${minRunMin} min continuous to count as a cycle"
        }
        return
    }

    // inactive
    if (state.dryerOn) {
        // The sensor going quiet IS the end of the cycle - no debounce needed
        // or wanted, and the timestamp is exact.
        endDryerCycle("normal", nowTs)
        return
    }

    if (state.dryerActiveSince) {
        Integer secs = ((nowTs - (state.dryerActiveSince as Long)) / 1000L) as Integer
        unschedule("dryerMinRunFired")
        state.remove("dryerActiveSince")
        state.dryerIgnoredBlips = (state.dryerIgnoredBlips ?: 0) + 1
        if (debugEnable) log.debug "dryer vibration stopped after ${secs}s - too short to be a cycle, ignored"
    }
}

def dryerMinRunFired() {
    if (state.dryerOn || !state.dryerActiveSince) return
    if (suppressCrossTalk && state.washerOn) {
        state.totalSuppressedCount = (state.totalSuppressedCount ?: 0) + 1
        state.remove("dryerActiveSince")
        if (txtEnable) log.info "Dryer vibration sustained but washer is running - suppressed as cross-talk"
        return
    }
    startDryerCycle(state.dryerActiveSince as Long)
}

private void startDryerCycle(Long ts) {
    boolean concurrentWasher = state.washerOn as boolean
    state.dryerOn = true
    state.dryerCycleStart = ts
    state.remove("dryerActiveSince")
    unschedule("dryerMinRunFired")
    logCycleEvent("dryer", "start", ts, [concurrent: concurrentWasher])
    if (txtEnable) log.info "Dryer started${concurrentWasher ? ' - washer is also running' : ''}"
    armDeadman("dryer", ts)
    if (switchList) switchList*.on()
    if (enableStartNotify) notify(dryerStartMessage ?: "Dryer started")
}

private void endDryerCycle(String reason, Long endTs) {
    Long ts = endTs ?: now()
    Long startTs = state.dryerCycleStart as Long
    Integer durMin = startTs ? Math.round((ts - startTs) / 60000d) as Integer : 0
    logCycleEvent("dryer", "end", ts, [durationMin: durMin, reason: reason, concurrent: (state.washerOn as boolean)])
    if (txtEnable) log.info "Dryer done after ${durMin} min (${reason})"
    unschedule("dryerDeadmanFired")
    unschedule("dryerMinRunFired")
    state.dryerOn = false
    state.remove("dryerActiveSince")
    if (switchList) switchList*.off()
    if (enableDoneNotify) notify(dryerDoneMessage ?: "Dryer is done")
}

def dryerDeadmanFired() {
    if (!state.dryerOn) return
    if (txtEnable) log.info "Dryer deadman timer fired - forcing cycle end"
    endDryerCycle("deadman", now())
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
