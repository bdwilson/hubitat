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
 *  - Dryer start/stop is duration-based: this sensor latches "active" for as
 *    long as it keeps feeling vibration, so a cycle is real only once that
 *    active span outlasts a minimum run time. Handling the machine tops out
 *    at a few minutes; real cycles hold active for 28-66. That also makes
 *    cross-talk suppression unnecessary (it only ever produces short spans),
 *    so it defaults off.
 *  - The washer's end-of-cycle confirmation adapts to how long a load
 *    normally takes on this machine: a low-power dip early in a wash needs a
 *    long quiet period to count as the end (it is usually a fill/pause),
 *    while one arriving after a full typical load's worth of runtime is
 *    confirmed quickly, so back-to-back loads don't merge into one.
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
    iconX3Url: "",
    oauth: true
)

mappings {
    // Yes/No land straight from the notification - one tap. "No" then
    // offers an optional note. Distinct path shapes so ":answer" can't
    // swallow the note submission.
    path("/f/:id/:answer") { action: [GET: "feedbackAnswer"] }
    path("/fn/:id")        { action: [GET: "feedbackNote"] }
}

preferences {
    page(name: "mainPage")
    page(name: "dataPage")
    page(name: "tuningPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Laundry Monitor & Logger", install: true, uninstall: true) {
        section("<b>Devices</b>") {
            input "washerPowerMeter", "capability.powerMeter", title: "Washer power meter", required: true, multiple: false, submitOnChange: true
            input "dryerVibrationSensor", "capability.accelerationSensor", title: "Dryer vibration/acceleration sensor", required: true, multiple: false, submitOnChange: true
        }

        section("<b>Washer - Power Thresholds</b>", hideable: true, hidden: false) {
            paragraph "Defaults below come from a calibration pass against ~30 days of real usage. See the README before changing them."
            input "washerStartWaitMin", "number", title: "Time (minutes) to wait before counting the power threshold (helps with brief startup blips)", required: false, defaultValue: 4
            input "washerStartW", "decimal", title: "Start cycle when power (W) rises above", required: false, defaultValue: 5
            input "washerMinEndMin", "number", title: "Minimum minutes after start before end detection begins", required: false, defaultValue: 10
            input "washerStopW", "decimal", title: "Stop cycle when power (W) drops below", required: false, defaultValue: 3
            input "washerStopReadings", "number", title: "Stop after power is below threshold for this many sequential readings (fast path, if the meter keeps reporting)", required: false, defaultValue: 2
            input "washerStopMinutes", "number", title: "Also require this many continuous minutes below threshold before stopping (0 = off)", required: false, defaultValue: 0
            input "washerStopConfirmMin", "number", title: "Also confirm stop after this many minutes with no reading back above the stop threshold, even without a second low reading (handles meters that stop reporting once idle; 0 = off)", required: false, defaultValue: 10
            input "washerStopConfirmLateMin", "number", title: "Shorter confirmation once the wash has already run about as long as a normal load (catches back-to-back loads; 0 = always use the value above)", required: false, defaultValue: 4
            input "washerIgnoreW", "decimal", title: "Ignore extraneous power (W) readings above (spike filter)", required: false, defaultValue: 1500
            input "washerDeadmanMin", "number", title: "Maximum cycle time in minutes (deadman timer, force-ends a stuck cycle)", required: false, defaultValue: 90
        }

        section("<b>Dryer - Vibration Thresholds</b>", hideable: true, hidden: false) {
            paragraph "The sensor stays <i>active</i> for as long as it keeps feeling vibration, so how long it stays active is what separates a real cycle from handling it: in real data, bumps and cross-talk run about a second to a minute, loading the machine has reached 3m50s, and every real dryer cycle held active for 28+ minutes."
            input "dryerMinRunMin", "number", title: "Vibration must stay continuously active this many minutes to count as a real cycle", required: false, defaultValue: 6
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

        section("<b>Feedback (off by default)</b>", hideable: true, hidden: !(enableFeedback as boolean)) {
            paragraph "Adds <i>Was this correct? Yes / No</i> links to each notification. Yes is one tap; No offers an optional note. No answer is treated as nothing said - not as a yes. Answers are stored with the raw readings behind them so detection can be re-tuned from real labelled events instead of guesswork."
            input "enableFeedback", "bool", title: "Add feedback links to notifications", required: false, defaultValue: false, submitOnChange: true
            if (enableFeedback) {
                input "feedbackUrlMode", "enum", title: "Which URL to put in notifications", required: false, defaultValue: "cloud", options: ["cloud": "Cloud (works away from home)", "local": "Local (LAN only)"], submitOnChange: true
                input "feedbackLinkStyle", "enum", title: "How to put the links in the message", required: false, defaultValue: "auto", options: ["auto": "Automatic - tidy links on Pushover, plain text elsewhere", "plain": "Always plain text", "html": "Always HTML"], submitOnChange: true
                paragraph "<small><b>Automatic</b> sends Pushover devices the <code>[HTML]</code> marker its driver needs to render real links, and sends every other notifier plain URLs that the push client links itself. Pick <b>Always HTML</b> only if you have confirmed your notifier renders HTML without a marker; pick <b>Always plain text</b> if tags show up as literal markup.</small>"
                if (!state.accessToken) {
                    paragraph "<span style='color:#b00'><b>OAuth is not enabled yet.</b> Go to <b>Apps Code</b> &rarr; this app &rarr; <b>OAuth</b> &rarr; Enable, then come back and hit Done.</span>"
                } else {
                    paragraph "Cloud: <code>${getFullApiServerUrl()}/f/&lt;id&gt;/y?access_token=${state.accessToken}</code>"
                    paragraph "Local: <code>${getFullLocalApiServerUrl()}/f/&lt;id&gt;/y?access_token=${state.accessToken}</code>"
                    paragraph "<small>Anyone with that token can submit feedback, so treat the link as mildly sensitive. It cannot read your logs, change settings, or control devices.</small>"
                }
                Integer answered = ((state.feedback instanceof List) ? state.feedback : []).size()
                Integer wrong = ((state.feedback instanceof List) ? state.feedback : []).count { !(it.ok) } as Integer
                paragraph "Answers so far: ${answered} (${wrong} marked not correct)"
            }
        }

        section("<b>Data Log</b>") {
            paragraph "Every washer power reading and every dryer active/inactive vibration report is saved so thresholds can be re-tuned from real data later."
            input "maxRawLogEntries", "number", title: "Max raw readings to retain", required: false, defaultValue: 3000
            input "maxCycleLogEntries", "number", title: "Max cycle summaries to retain", required: false, defaultValue: 300
            href "tuningPage", title: "Tuning Report", description: "Generate a ready-to-paste prompt for re-tuning from your own labelled data"
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

def tuningPage() {
    dynamicPage(name: "tuningPage", title: "Tuning Report") {
        section {
            paragraph "Everything needed to re-derive this app's thresholds from your own data: current settings, what the app has learned about your machines, the cycles it detected, and every event you marked wrong. Copy it into any LLM and it will have the full picture without you having to explain any of it."
            paragraph "Nothing here changes settings. It produces a recommendation you apply yourself."
            paragraph "<textarea readonly rows='24' style='width:100%;font-family:monospace;font-size:11px'>${tuningPrompt()}</textarea>"
        }
    }
}

private String tuningPrompt() {
    Map prof = cycleProfile()
    List cyc = (state.cycleLog instanceof List) ? state.cycleLog : []
    List fb = (state.feedback instanceof List) ? state.feedback : []
    List idx = (state.feedbackIndex instanceof List) ? state.feedbackIndex : []
    List arch = (state.feedbackRaw instanceof List) ? state.feedbackRaw : []
    StringBuilder sb = new StringBuilder()

    sb << "I run a Hubitat app that detects washer and dryer cycles and notifies me.\n"
    sb << "It got some calls wrong. Below is its configuration, what it has learned,\n"
    sb << "what it detected, and my corrections. Tell me which specific settings to\n"
    sb << "change and why, using only this data. Flag anything you cannot resolve\n"
    sb << "from the evidence rather than guessing.\n\n"

    sb << "HOW DETECTION WORKS\n"
    sb << "Washer: a power meter. A cycle starts when power stays at/above the start\n"
    sb << "threshold for the start-wait window, and ends when power drops below the\n"
    sb << "stop threshold and stays there - confirmed either by N sequential low\n"
    sb << "readings or by a quiet timeout. The quiet timeout is shorter once the wash\n"
    sb << "has already run about as long as a typical load, because a dip that late is\n"
    sb << "more likely the end of one load than a mid-cycle pause.\n"
    sb << "Dryer: a vibration sensor with NO power monitoring. It latches 'active'\n"
    sb << "while it feels vibration, so a cycle is real only if that active span\n"
    sb << "outlasts the minimum run time; the 'inactive' report is the exact end.\n"
    sb << "Handling the machine (loading, emptying, bumps) also produces vibration,\n"
    sb << "which is the main source of false dryer cycles.\n\n"

    sb << "CURRENT SETTINGS\n"
    sb << "washer start threshold: ${washerStartW ?: 5} W\n"
    sb << "washer start wait: ${washerStartWaitMin ?: 4} min\n"
    sb << "washer stop threshold: ${washerStopW ?: 3} W\n"
    sb << "washer min minutes before end detection: ${washerMinEndMin ?: 10}\n"
    sb << "washer stop - sequential low readings: ${washerStopReadings ?: 2}\n"
    sb << "washer stop - quiet timeout: ${washerStopConfirmMin ?: 10} min\n"
    sb << "washer stop - quiet timeout late in load: ${washerStopConfirmLateMin ?: 4} min\n"
    sb << "washer spike filter: ${washerIgnoreW ?: 1500} W\n"
    sb << "washer deadman: ${washerDeadmanMin ?: 90} min\n"
    sb << "dryer minimum continuous active: ${dryerMinRunMin ?: 6} min\n"
    sb << "dryer deadman: ${dryerDeadmanMin ?: 120} min\n"
    sb << "cross-talk suppression: ${suppressCrossTalk ? 'on' : 'off'}\n\n"

    sb << "LEARNED PROFILE (medians, excluding anything I marked wrong)\n"
    sb << "typical washer cycle: ${prof.washerMin ?: 'n/a'} min over ${prof.washerN} sample(s)\n"
    sb << "typical washer peak power: ${prof.washerPeak ?: 'n/a'} W\n"
    sb << "typical dryer cycle: ${prof.dryerMin ?: 'n/a'} min over ${prof.dryerN} sample(s)\n\n"

    sb << "DETECTED CYCLES (most recent last)\n"
    sb << "when,device,phase,durationMin,peakW,reason,concurrent,appDoubted\n"
    List recent = cyc.size() > 40 ? cyc[-40..-1] : cyc
    recent.each { e ->
        sb << "${new Date(e.t as Long).format('yyyy-MM-dd HH:mm:ss')},${e.d},${e.p},"
        sb << "${e.durationMin ?: ''},${e.peakW ?: ''},${e.reason ?: ''},"
        sb << "${e.concurrent ? 1 : 0},${e.doubt ? 'yes' : ''}\n"
    }

    sb << "\nMY CORRECTIONS\n"
    if (!fb) {
        sb << "(none yet - answer the Yes/No links on notifications to build this up)\n"
    } else {
        fb.each { f ->
            Map n = idx.find { (it.i as Integer) == (f.i as Integer) }
            sb << "- ${n?.t ? new Date(n.t as Long).format('yyyy-MM-dd HH:mm') : '?'} "
            sb << "\"${n?.m ?: '?'}\" (${n?.k} ${n?.d}) -> ${f.ok ? 'CORRECT' : 'WRONG'}"
            if (f.note) sb << " - I said: ${f.note}"
            sb << "\n"
        }
    }

    List wrongIds = fb.findAll { !it.ok }.collect { it.i as Integer }
    if (wrongIds) {
        sb << "\nRAW SENSOR READINGS AROUND THE EVENTS I MARKED WRONG\n"
        sb << "(washer values are watts; dryer values are active/inactive)\n"
        sb << "eventId,timestamp,device,value\n"
        arch.each { a ->
            if (!wrongIds.contains(a.i as Integer)) return
            a.rows.each { r ->
                sb << "${a.i},${new Date(r[0] as Long).format('yyyy-MM-dd HH:mm:ss')},${r[1]},${r[2]}\n"
            }
        }
    }

    sb << "\nWHAT I WANT\n"
    sb << "1. For each event I marked wrong, say which setting caused it.\n"
    sb << "2. Recommend new values, with the reasoning and the margin - i.e. show\n"
    sb << "   that the new value separates the bad events from the real ones, and\n"
    sb << "   check it against every real cycle above so it does not break them.\n"
    sb << "3. Call out any change that trades one error for another.\n"
    return sb.toString()
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
        section("<b>Feedback Log</b>") {
            List fb = (state.feedback instanceof List) ? state.feedback : []
            paragraph "Stored: ${fb.size()} answered event(s), ${((state.feedbackRaw instanceof List) ? state.feedbackRaw : []).size()} with pinned raw readings."
            input "showFeedbackExport", "bool", title: "Show CSV for copy/paste", required: false, defaultValue: false, submitOnChange: true
            if (showFeedbackExport) {
                paragraph "<textarea readonly rows='18' style='width:100%;font-family:monospace;font-size:11px'>${feedbackCsv()}</textarea>"
            }
            input "clearFeedbackButton", "button", title: "Clear Feedback Log", backgroundColor: "Crimson", textColor: "white", submitOnChange: true
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
private static String settingsVersion() { return "6" }

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

    // v3: a real overnight false start was traced to two-and-a-bit minutes
    // of coincidental idle-noise blips (three consecutive readings above
    // the start threshold, ~90 seconds apart, with no intervening dip) -
    // the 2-minute wait happened to land exactly on the boundary. Measured
    // against every noise run across several nights of real data, the
    // worst case tops out at 3.0 minutes and everything else at 1.5 - so
    // 4 minutes clears all of them with better than 2x margin, at the cost
    // of a slightly later start notification for real cycles (the logged
    // start time is unaffected either way; it's always the first
    // qualifying reading, not the confirmation time).
    if ((washerStartWaitMin ?: 0) < 4) {
        app.updateSetting("washerStartWaitMin", [value: "4", type: "number"])
        changes << "washerStartWaitMin=4"
    }

    // v4: loading the dryer produced a 3m50s continuous vibration burst -
    // a real cycle by the old 3-minute rule, but nobody was drying
    // anything. Next-longest non-cycle burst across all recorded data is
    // 1.1 minutes and the shortest real cycle is 28.7, so 6 clears the
    // outlier with margin and stays far below any real run.
    if ((dryerMinRunMin ?: 0) < 6) {
        app.updateSetting("dryerMinRunMin", [value: "6", type: "number"])
        changes << "dryerMinRunMin=6"
    }
    if (washerStopConfirmLateMin == null) {
        app.updateSetting("washerStopConfirmLateMin", [value: "4", type: "number"])
        changes << "washerStopConfirmLateMin=4"
    }

    // v5/v6: feedback links went out as HTML anchors and showed up as
    // literal markup in Pushover. v5 fell back to plain URLs everywhere;
    // v6 is better than that - the Pushover driver does render HTML, it
    // just wants a "[HTML]" marker on the message first, so "auto" sends
    // that marker to Pushover devices and plain URLs to everything else.
    // Only v5's own fallback is overwritten here, and it shipped the same
    // day, so nobody chose "plain" deliberately yet.
    if (feedbackLinkStyle == null || feedbackLinkStyle == "plain") {
        app.updateSetting("feedbackLinkStyle", [value: "auto", type: "enum"])
        changes << "feedbackLinkStyle=auto"
    }

    state.settingsVersion = settingsVersion()
    if (changes) log.info "Laundry Monitor: applied v${settingsVersion()} settings (${changes.join(', ')})"
}

def initialize() {
    migrateSettings()

    if (enableFeedback && !state.accessToken) {
        try {
            createAccessToken()
        } catch (Exception e) {
            log.warn "Laundry Monitor: enable OAuth in Apps Code before feedback links can be sent (${e.message})"
        }
    }

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
        case "clearFeedbackButton":
            state.feedback = []
            state.feedbackRaw = []
            state.feedbackIndex = []
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
        Integer confirmMin = stopConfirmMinutesFor(nowTs)
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
    if (enableStartNotify) notify(washerStartMessage ?: "Washer started", "start", "washer", ts)
    if (concurrentDryer && enableConcurrentLoadNotify) {
        notify(concurrentLoadMessage ?: "Washer started again - the dryer is still running the previous load", "concurrent", "washer", ts)
    }
}

private void endWasherCycle(String reason, Long endTs) {
    Long ts = endTs ?: now()
    Long startTs = state.washerCycleStart as Long
    Integer durMin = startTs ? Math.round((ts - startTs) / 60000d) as Integer : 0
    String doubt = implausibleReason("washer", durMin, state.washerPeakW)
    Map extra = [durationMin: durMin, peakW: state.washerPeakW, reason: reason, concurrent: (state.dryerOn as boolean)]
    if (doubt) extra.doubt = doubt
    logCycleEvent("washer", "end", ts, extra)
    if (txtEnable) log.info "Washer done after ${durMin} min (peak ${state.washerPeakW}W, ${reason})"
    unschedule("washerDeadmanFired")
    unschedule("washerStopConfirmFired")
    state.washerOn = false
    state.washerCycleEndTs = ts
    state.washerLowCount = 0
    state.remove("washerEndingSince")
    state.remove("washerPendingSince")
    if (switchList) switchList*.off()
    if (enableDoneNotify) {
        if (doubt) {
            log.warn "Laundry Monitor: not announcing washer done - ${doubt}. Logged for review."
        } else {
            notify(washerDoneMessage ?: "Washer is done", "done", "washer", ts)
        }
    }
    if (enableReminder && !doubt) runIn(((reminderMinutes ?: 15) as Integer) * 60, "washerReminderFired", [overwrite: true])
}

def washerDeadmanFired() {
    if (!state.washerOn) return
    if (txtEnable) log.info "Washer deadman timer fired - forcing cycle end"
    endWasherCycle("deadman", now())
}

// Cycles the user has explicitly told us were wrong. Without this the app
// learns from its own mistakes: a bad call goes into the history, shifts
// the median, and makes the next call worse.
private List rejectedCycleKeys() {
    List fb = (state.feedback instanceof List) ? state.feedback : []
    List idx = (state.feedbackIndex instanceof List) ? state.feedbackIndex : []
    List keys = []
    fb.each { f ->
        if (f.ok) return
        Map n = idx.find { (it.i as Integer) == (f.i as Integer) }
        if (n?.c && n?.d) keys << "${n.d}|${n.c}".toString()
    }
    return keys
}

private Integer medianOf(List values) {
    if (!values) return null
    List s = values.sort()
    return s[(int) (s.size() / 2)] as Integer
}

// What a normal load looks like on THIS machine, learned from its own
// history minus anything the user rejected. Everything downstream - the
// adaptive stop timeout and the sanity gate - reads from here.
private Map cycleProfile() {
    List entries = (state.cycleLog instanceof List) ? state.cycleLog : []
    List rejected = rejectedCycleKeys()
    List wDur = [], wPeak = [], dDur = []
    entries.each { e ->
        if (e.p != "end" || e.reason != "normal" || !e.durationMin) return
        // Never learn from a cycle we ourselves refused to announce - the
        // app would otherwise be taught by the very events it distrusted.
        if (e.doubt) return
        if (rejected.contains("${e.d}|${e.t}".toString())) return
        if (e.d == "washer") {
            wDur << (e.durationMin as Integer)
            if (e.peakW) wPeak << (e.peakW as BigDecimal).intValue()
        } else if (e.d == "dryer") {
            dDur << (e.durationMin as Integer)
        }
    }
    if (wDur.size() > 10) wDur = wDur[-10..-1]
    if (wPeak.size() > 10) wPeak = wPeak[-10..-1]
    if (dDur.size() > 10) dDur = dDur[-10..-1]
    return [washerMin: medianOf(wDur), washerPeak: medianOf(wPeak), dryerMin: medianOf(dDur),
            washerN: wDur.size(), dryerN: dDur.size()]
}

private Integer typicalWasherMin() {
    Map prof = cycleProfile()
    return (prof.washerN >= 3) ? (prof.washerMin as Integer) : null
}

// A finished cycle that looks nothing like a real load on this machine.
// Deliberately only gates on the two signals with enormous measured
// separation: washer peak power (real loads never dropped below 71% of
// median, the overnight phantom hit 2%) and dryer duration (real never
// below 56%, loading-the-machine hit 8%). Washer duration is NOT gated -
// a short delicates load is legitimately short.
private String implausibleReason(String device, Integer durMin, def peakW) {
    Map prof = cycleProfile()
    if (device == "washer") {
        if (prof.washerN < 5 || !prof.washerPeak || peakW == null) return null
        BigDecimal floor = (prof.washerPeak as BigDecimal) * 0.25
        if ((peakW as BigDecimal) < floor) {
            return "peak ${peakW}W is far below the ${prof.washerPeak}W typical for a real load"
        }
    } else {
        if (prof.dryerN < 5 || !prof.dryerMin || durMin == null) return null
        BigDecimal floor = (prof.dryerMin as BigDecimal) * 0.35
        if ((durMin as BigDecimal) < floor) {
            return "ran ${durMin} min against a ${prof.dryerMin} min typical cycle"
        }
    }
    return null
}

// How long a low-power stretch has to hold before it counts as the end of
// the cycle. A dip arriving early in a wash is almost always a fill/pause
// and needs the long timeout; one arriving after the machine has already
// run about as long as a normal load is far more likely to be the real end
// (or the boundary before the next load), and waiting out the long timeout
// there just merges two loads into one.
private Integer stopConfirmMinutesFor(Long nowTs) {
    Integer longMin = (washerStopConfirmMin ?: 0) as Integer
    Integer lateMin = (washerStopConfirmLateMin ?: 0) as Integer
    if (lateMin <= 0 || lateMin >= longMin) return longMin

    Integer typical = typicalWasherMin()
    Long cycleStart = state.washerCycleStart as Long
    if (typical == null || !cycleStart) return longMin

    Long lateAfterMs = (Math.max(typical * 0.8d, 20d) * 60000d) as Long
    if ((nowTs - cycleStart) >= lateAfterMs) {
        if (debugEnable) log.debug "washer has run ${Math.round((nowTs - cycleStart) / 60000d)}m (typical ${typical}m) - using ${lateMin}m stop confirmation"
        return lateMin
    }
    return longMin
}

def washerStopConfirmFired() {
    if (!state.washerOn || !state.washerEndingSince) return
    if (txtEnable) log.info "Washer stop confirmed by quiet timeout (no reading above ${washerStopW}W in ${washerStopConfirmMin} min)"
    endWasherCycle("normal", state.washerEndingSince as Long)
}

def washerReminderFired() {
    // Don't nag about moving a load that's already confirmed in the dryer -
    // this fired even when the dryer had been running for several minutes,
    // because it only checked whether the washer had restarted, never
    // whether the dryer already had.
    if (!state.washerOn && !state.dryerOn) {
        notify(washerReminderMessage ?: "Reminder: the washer is still waiting to be moved to the dryer", "reminder", "washer", state.washerCycleEndTs as Long)
    }
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
    if (enableStartNotify) notify(dryerStartMessage ?: "Dryer started", "start", "dryer", ts)

    // The washer's quiet-timeout can take up to washerStopConfirmMin to
    // confirm a real stop when only one low reading ever arrives (no
    // second reading to satisfy the fast path). A dryer cycle actually
    // starting for real is strong independent evidence that whatever it's
    // drying just finished washing, so resolve a pending washer end now
    // instead of leaving "Washer is done" to arrive stale - sometimes
    // minutes after the load is already confirmed moved.
    if (concurrentWasher && state.washerEndingSince) {
        endWasherCycle("normal", state.washerEndingSince as Long)
    }
}

private void endDryerCycle(String reason, Long endTs) {
    Long ts = endTs ?: now()
    Long startTs = state.dryerCycleStart as Long
    Integer durMin = startTs ? Math.round((ts - startTs) / 60000d) as Integer : 0
    String doubt = implausibleReason("dryer", durMin, null)
    Map extra = [durationMin: durMin, reason: reason, concurrent: (state.washerOn as boolean)]
    if (doubt) extra.doubt = doubt
    logCycleEvent("dryer", "end", ts, extra)
    if (txtEnable) log.info "Dryer done after ${durMin} min (${reason})"
    unschedule("dryerDeadmanFired")
    unschedule("dryerMinRunFired")
    state.dryerOn = false
    state.remove("dryerActiveSince")
    if (switchList) switchList*.off()
    if (enableDoneNotify) {
        if (doubt) {
            log.warn "Laundry Monitor: not announcing dryer done - ${doubt}. Logged for review."
        } else {
            notify(dryerDoneMessage ?: "Dryer is done", "done", "dryer", ts)
        }
    }
}

def dryerDeadmanFired() {
    if (!state.dryerOn) return
    if (txtEnable) log.info "Dryer deadman timer fired - forcing cycle end"
    endDryerCycle("deadman", now())
}


/* ---------------- feedback loop ---------------- */

private String feedbackBaseUrl() {
    try {
        return (feedbackUrlMode == "local") ? getFullLocalApiServerUrl() : getFullApiServerUrl()
    } catch (Exception e) {
        log.warn "Laundry Monitor: could not build feedback URL - ${e.message}"
        return null
    }
}

// Every notification gets an id so feedback can be tied back to the exact
// event it is about - including the reminder and second-load alerts, which
// have no cycle-log entry of their own.
private Integer recordNotification(String msg, String kind, String device, Long cycleTs) {
    Integer id = (state.nextFeedbackId ?: 1) as Integer
    state.nextFeedbackId = id + 1
    List idx = (state.feedbackIndex instanceof List) ? state.feedbackIndex : []
    Map entry = [i: id, t: now(), k: kind, d: device, m: msg]
    if (cycleTs) entry.c = cycleTs
    idx << entry
    while (idx.size() > 200) idx.remove(0)
    state.feedbackIndex = idx
    return id
}

private Map findNotification(Integer id) {
    List idx = (state.feedbackIndex instanceof List) ? state.feedbackIndex : []
    return idx.find { (it.i as Integer) == id }
}

// A label is worthless once the raw readings behind it have rolled out of
// the capped log, so snapshot the window around the event the moment
// feedback arrives.
private void pinRawWindow(Integer id, Map note) {
    List archive = (state.feedbackRaw instanceof List) ? state.feedbackRaw : []
    if (archive.find { (it.i as Integer) == id }) return
    Long from = ((note.c ?: note.t) as Long) - (15 * 60000L)
    List raw = (state.rawLog instanceof List) ? state.rawLog : []
    List rows = []
    raw.each { r ->
        if ((r.t as Long) >= from) rows << [r.t, r.d, r.v]
    }
    if (rows.size() > 400) rows = rows[-400..-1]
    archive << [i: id, rows: rows]
    while (archive.size() > 20) archive.remove(0)
    state.feedbackRaw = archive
}

private void storeFeedback(Integer id, boolean ok, String noteText) {
    List fb = (state.feedback instanceof List) ? state.feedback : []
    fb = fb.findAll { (it.i as Integer) != id }
    Map rec = [i: id, t: now(), ok: ok]
    if (noteText) rec.note = noteText
    fb << rec
    while (fb.size() > 200) fb.remove(0)
    state.feedback = fb
    Map n = findNotification(id)
    if (n) pinRawWindow(id, n)
    if (txtEnable) log.info "Laundry Monitor feedback on #${id} (${n?.k} ${n?.d}): ${ok ? 'correct' : 'NOT correct'}${noteText ? " - ${noteText}" : ''}"
}

private String esc(String s) {
    if (s == null) return ""
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;").replace("'", "&#39;")
}

private String fbPage(String bodyHtml) {
    return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Laundry Monitor</title><style>
body{font-family:-apple-system,system-ui,sans-serif;background:#111;color:#eee;margin:0;padding:28px 20px;font-size:17px;line-height:1.5}
.card{max-width:520px;margin:0 auto}
.ok{color:#5cd65c;font-size:22px;font-weight:600}
.bad{color:#ff8a65;font-size:22px;font-weight:600}
.ev{color:#aaa;margin:14px 0 22px}
.link{color:#7aa7ff}
textarea{width:100%;box-sizing:border-box;min-height:110px;font-size:17px;padding:10px;border-radius:8px;border:1px solid #444;background:#1c1c1c;color:#eee}
button{margin-top:14px;width:100%;padding:14px;font-size:18px;font-weight:600;border:0;border-radius:8px;background:#2f6fed;color:#fff}
</style></head><body><div class="card">${bodyHtml}</div></body></html>"""
}

// render() hands the response back as this method's RETURN VALUE - calling
// it and then falling out of the method sends an empty body, which is
// exactly the blank page this used to produce. Every exit below returns
// render(...) directly, and the whole thing is wrapped so that even an
// unexpected failure says something instead of nothing.
private def fbRender(String bodyHtml) {
    return render(contentType: "text/html", data: fbPage(bodyHtml), status: 200)
}

private String fbEventHtml(Map n) {
    String when = ""
    try {
        when = " &middot; " + new Date(n.t as Long).format("h:mm a", location.timeZone)
    } catch (Exception ignored) {
    }
    return "<p class=\"ev\">${esc(n.m as String)}<br><small>${esc(n.k as String)} &middot; ${esc(n.d as String)}${when}</small></p>"
}

def feedbackAnswer() {
    try {
        Integer id = safeInt(params?.id)
        String answer = params?.answer
        Map n = id == null ? null : findNotification(id)
        if (n == null) {
            return fbRender("<p class=\"bad\">Event not found</p><p class=\"ev\">It may have aged out of the log.</p>")
        }
        boolean ok = (answer == "y")
        storeFeedback(id, ok, null)
        String ev = fbEventHtml(n)
        String base = feedbackBaseUrl()
        if (ok) {
            String flip = "${base}/f/${id}/n?access_token=${state.accessToken}"
            return fbRender("<p class=\"ok\">Thanks &mdash; logged as correct.</p>${ev}<p><a class=\"link\" href=\"${flip}\">Actually, that one was wrong</a></p>")
        }
        return fbRender("""<p class="bad">Logged as not correct.</p>${ev}
<form action="${base}/fn/${id}" method="GET">
<input type="hidden" name="access_token" value="${state.accessToken}">
<label for="note">What actually happened? (optional)</label>
<textarea id="note" name="note" placeholder="e.g. nothing was running, I was just emptying the dryer"></textarea>
<button type="submit">Save note</button></form>
<p><small>The answer is already saved. The note is optional extra detail.</small></p>""")
    } catch (Exception e) {
        log.error "Laundry Monitor: feedback link failed - ${e}"
        return fbRender("<p class=\"bad\">Something went wrong</p><p class=\"ev\">${esc(e.message as String)}</p>")
    }
}

def feedbackNote() {
    try {
        Integer id = safeInt(params?.id)
        Map n = id == null ? null : findNotification(id)
        if (n == null) {
            return fbRender("<p class=\"bad\">Event not found</p>")
        }
        String noteText = (params?.note ?: "") as String
        if (noteText.length() > 500) noteText = noteText.substring(0, 500)
        storeFeedback(id, false, noteText)
        String body = "<p class=\"ok\">Thanks &mdash; noted.</p>" + fbEventHtml(n)
        if (noteText) body += "<p class=\"ev\">&ldquo;${esc(noteText)}&rdquo;</p>"
        return fbRender(body)
    } catch (Exception e) {
        log.error "Laundry Monitor: feedback note failed - ${e}"
        return fbRender("<p class=\"bad\">Something went wrong</p><p class=\"ev\">${esc(e.message as String)}</p>")
    }
}

private Integer safeInt(v) {
    if (v == null) return null
    try {
        return (v as Integer)
    } catch (Exception ignored) {
        return null
    }
}

/* ---------------- shared helpers ---------------- */

private void notify(String msg, String kind, String device, Long cycleTs) {
    if (!msg) return
    String plain = msg
    String html = null
    if (enableFeedback && state.accessToken) {
        Integer id = recordNotification(msg, kind, device, cycleTs)
        if (id != null) {
            String base = feedbackBaseUrl()
            if (base) {
                String yes = "${base}/f/${id}/y?access_token=${state.accessToken}"
                String no = "${base}/f/${id}/n?access_token=${state.accessToken}"
                plain = "${msg}\n\nWas this correct?\nYes: ${yes}\nNo: ${no}"
                html = "${msg}<br><br>Was this correct? <a href=\"${yes}\">Yes</a> | <a href=\"${no}\">No</a>"
            }
        }
    }
    notifyDevices?.each { dev ->
        try {
            dev.deviceNotification(messageFor(dev, plain, html))
        } catch (Exception e) {
            log.warn "Laundry Monitor: ${dev} rejected the notification - ${e.message}"
        }
    }
    // Speech gets the plain subject line - nobody wants a URL read aloud.
    if (speechDevices) speechDevices*.speak(msg)
    if (txtEnable) log.info "notify: ${msg}"
}

// Pushover does render HTML, but its Hubitat driver only asks the API for
// that when the message carries a literal "[HTML]" marker; without it the
// tags arrive as text. No other notifier understands that marker - it would
// just show up verbatim - so the choice is made per device rather than baked
// into the message.
private String messageFor(dev, String plain, String html) {
    if (html == null) return plain
    switch (feedbackLinkStyle) {
        case "html":
            return html
        case "plain":
            return plain
        default:
            return isPushoverDevice(dev) ? "[HTML]${html}" : plain
    }
}

private boolean isPushoverDevice(dev) {
    try {
        String t = dev?.getTypeName()
        return t != null && t.toLowerCase().contains("pushover")
    } catch (Exception ignored) {
        return false
    }
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

// Replay-ready join: the label, the event it was about, and the cycle it
// came from. Pinned raw readings are exported separately below it.
private String feedbackCsv() {
    List fb = (state.feedback instanceof List) ? state.feedback : []
    List idx = (state.feedbackIndex instanceof List) ? state.feedbackIndex : []
    List cyc = (state.cycleLog instanceof List) ? state.cycleLog : []
    StringBuilder sb = new StringBuilder()
    sb << "eventId,notifiedAt,answeredAt,kind,device,correct,cycleAt,durationMin,peakW,reason,note\n"
    fb.each { f ->
        Map n = idx.find { (it.i as Integer) == (f.i as Integer) }
        Map c = null
        if (n?.c) c = cyc.find { (it.t as Long) == (n.c as Long) && it.d == n.d }
        sb << "${f.i},"
        sb << "${n?.t ? new Date(n.t as Long).format('yyyy-MM-dd HH:mm:ss') : ''},"
        sb << "${new Date(f.t as Long).format('yyyy-MM-dd HH:mm:ss')},"
        sb << "${n?.k ?: ''},${n?.d ?: ''},${f.ok ? 1 : 0},"
        sb << "${n?.c ? new Date(n.c as Long).format('yyyy-MM-dd HH:mm:ss') : ''},"
        sb << "${c?.durationMin ?: ''},${c?.peakW ?: ''},${c?.reason ?: ''},"
        sb << "\"${((f.note ?: '') as String).replace('"', "'")}\"\n"
    }
    List arch = (state.feedbackRaw instanceof List) ? state.feedbackRaw : []
    if (arch) {
        sb << "\n# pinned raw readings for the events above\n"
        sb << "eventId,timestamp,device,value\n"
        arch.each { a ->
            a.rows.each { r ->
                sb << "${a.i},${new Date(r[0] as Long).format('yyyy-MM-dd HH:mm:ss')},${r[1]},${r[2]}\n"
            }
        }
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
