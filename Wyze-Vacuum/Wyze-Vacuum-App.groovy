/**
 * Wyze Vacuum Connect App
 *
 * 1.15.0 - Brian Wilson / bubba@bubba.org
 *
 * Native Hubitat integration for the Wyze Robot Vacuum (e.g. 200S / JA_RO2).
 *
 * Wyze has no official public API for this device, so this app speaks the same
 * private/reverse-engineered app API used by the open-source wyze-sdk (Python),
 * homebridge-wyze-robovac, and matterbridge-wyze-robovac projects. That means:
 *   - It can break without notice if Wyze changes their backend.
 *   - It is not affiliated with or supported by Wyze Labs in any way.
 *   - You need a personal API key/key ID pair from developer-api-console.wyze.com
 *     (free) in addition to your normal Wyze account email/password.
 *
 * Setup:
 *  1. Create a key at https://developer-api-console.wyze.com/#/apikey/view
 *     (this gives you a Key Id and an API Key)
 *  2. Install this app and the "Wyze Robot Vacuum Driver", enter your Wyze email,
 *     password, Key Id, and API Key, and click "Log In"
 *  3. If prompted, enter your 2FA verification code
 *  4. Click "Discover Vacuums", select your vacuum(s), set a poll interval, Done
 *  5. Per vacuum: click "Discover Rooms" (requires an active map in the Wyze app),
 *     pick which rooms to rotate through, and a rotation mode. The driver's
 *     cleanNextRooms() command then cleans whichever selected rooms have gone
 *     longest without a clean — wire it to a "everyone left" automation to work
 *     through the house over the course of a week.
 *  6. Optionally pick notification devices and enable start/finish/stuck/bin
 *     alerts, and set an hours-of-cleaning threshold per vacuum for bin-empty
 *     reminders.
 *
 * A room only counts as "cleaned" toward rotation once its run actually
 * finishes — if a room-scoped clean is interrupted partway through, whichever
 * rooms didn't get their full estimated time stay eligible and are picked
 * again next time, rather than being skipped for a whole cycle.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at:
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field
import java.security.MessageDigest
import java.util.zip.Inflater

@Field static final String AUTH_BASE   = "https://auth-prod.api.wyze.com"
@Field static final String API_BASE    = "https://api.wyzecam.com"
@Field static final String VENUS_BASE  = "https://wyze-venus-service-vn.wyzecam.com"
@Field static final String VENUS_APP_ID = "venp_4c30f812828de875"
@Field static final String VENUS_SALT   = "CVCSNoa0ALsNEpgKls6ybVTVOmGzFoiq"
@Field static final String APP_VERSION  = "2.19.14"
@Field static final String WYZE_SC      = "a626948714654991afd3c0dbd7cdb901"
// Long-standing shared "app" key used by the open-source Wyze client ecosystem
// (wyze-sdk, wyze-node, etc.) to reach the auth-prod login endpoint. It is not a
// secret tied to any individual account. Wyze can rotate/revoke it at any time.
@Field static final String WYZE_X_API_KEY = "RckMFKbsds5p6QY3COEXc2ABwNTYY0q18ziEiSEm"
@Field static final String VACUUM_PRODUCT_MODEL = "JA_RO2"

definition(
    name: "Wyze Vacuum Connect",
    namespace: "brianwilson-hubitat",
    author: "bubba@bubba.org",
    description: "Native integration for Wyze Robot Vacuums (unofficial API)",
    category: "My Apps",
    // TODO: point back at master once this branch is merged
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/claude/hubitat-wyze-vacuum-integration-x2euom/Wyze-Vacuum/Wyze-Vacuum-App.groovy",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

// =================== Page ===================

def mainPage() {
    def loggedIn = state.wyzeAccessToken != null
    def canInstall = loggedIn && settings.selectedVacuums

    return dynamicPage(name: "mainPage", install: canInstall, uninstall: true) {
        section("<b>Step 1 — Wyze Developer API Key</b>") {
            paragraph "Create a free key at <a href='https://developer-api-console.wyze.com/#/apikey/view' target='_blank'>developer-api-console.wyze.com</a> " +
                      "and enter the Key Id / API Key it gives you below."
            input "wyzeKeyId",  "text", title: "Key Id",  required: true, submitOnChange: true
            input "wyzeApiKey", "text", title: "API Key", required: true, submitOnChange: true
        }

        section("<b>Step 2 — Wyze Account</b>") {
            input "wyzeEmail",    "text",     title: "Wyze Email",    required: true, submitOnChange: true
            input "wyzePassword", "password", title: "Wyze Password", required: true, submitOnChange: true
            input "btnLogin", "button", title: loggedIn ? "Re-login" : "Log In", width: 3

            if (state.wyzeLoginError) paragraph "<font color='red'>${state.wyzeLoginError}</font>"

            if (state.wyzeMfa) {
                paragraph "<b>Two-factor authentication required.</b> Enter the code Wyze sent/your authenticator app shows."
                input "mfaCode", "text", title: "Verification Code", required: true, submitOnChange: true
                input "btnSubmitMfa", "button", title: "Submit Code", width: 3
            } else if (loggedIn) {
                paragraph "<font color='green'>&#10003; Logged in to Wyze</font>"
            }
        }

        if (loggedIn) {
            section("<b>Step 3 — Devices</b>") {
                input "btnDiscover", "button", title: "Discover Vacuums", width: 3
                if (state.discoveryError) paragraph "<font color='red'>${state.discoveryError}</font>"
                if (state.discoveredVacuums) {
                    input "selectedVacuums", "enum",
                        title: "Select Vacuum(s)",
                        options: state.discoveredVacuums,
                        multiple: true, required: true, submitOnChange: true
                }
            }

            if (settings.selectedVacuums) {
                section("<b>Polling</b>") {
                    paragraph "Uses a faster interval while any selected vacuum is actively cleaning, and a slower one the rest of the time, so status stays responsive during a run without polling needlessly while idle/charging."
                    input "pollIntervalCleaning", "enum",
                        title: "Poll interval while cleaning",
                        options: ["1": "Every 1 min", "2": "Every 2 min", "5": "Every 5 min"],
                        defaultValue: "1", required: true
                    input "pollIntervalIdle", "enum",
                        title: "Poll interval while idle/charging",
                        options: ["5": "Every 5 min", "10": "Every 10 min", "15": "Every 15 min", "30": "Every 30 min"],
                        defaultValue: "15", required: true
                }

                section("<b>Notifications</b>") {
                    paragraph "Notifications are change-driven — polling by itself never triggers one."
                    input "notifyDevices", "capability.notification", title: "Send notifications to", multiple: true, required: false, submitOnChange: true
                    if (settings.notifyDevices) {
                        input "notifyCleaningStarted",  "bool", title: "Notify when cleaning starts",              defaultValue: true,  required: false
                        input "notifyCleaningFinished", "bool", title: "Notify when cleaning finishes",            defaultValue: true,  required: false
                        input "notifyStuck",            "bool", title: "Notify when the vacuum reports a fault",  defaultValue: true,  required: false
                    }
                    input "ignoredFaultCodes", "text",
                        title: "Fault codes to treat as normal, not real faults (comma-separated) — e.g. some codes may just mean \"charging\"/\"fully charged\", not an actual problem",
                        defaultValue: "2103,2105", required: false
                }

                settings.selectedVacuums.each { mac ->
                    def vacLabel = state.discoveredVacuums?.get(mac) ?: mac
                    def roomErr = state["roomError_${mac}"]
                    section("<b>${vacLabel} — Room Rotation</b>") {
                        input "btnDiscoverRooms_${mac}", "button", title: "Discover Rooms", width: 3
                        if (roomErr) paragraph "<font color='red'>${roomErr}</font>"

                        def rooms = state.discoveredRooms?.getAt(mac)
                        if (rooms) {
                            def roomOptions = rooms.collectEntries { [(it.id.toString()): it.name] }
                            input "rotationRooms_${mac}", "enum",
                                title: "Rooms to include in rotation",
                                options: roomOptions, multiple: true, required: false, submitOnChange: true

                            if (settings["rotationRooms_${mac}"]) {
                                input "rotationMode_${mac}", "enum",
                                    title: "Rotation mode",
                                    options: ["count": "Fixed number of rooms per run", "time": "Time budget per run"],
                                    defaultValue: "count", required: true, submitOnChange: true

                                if ((settings["rotationMode_${mac}"] ?: "count") == "time") {
                                    input "rotationMinutes_${mac}", "number", title: "Target minutes per run", defaultValue: 30, required: true
                                } else {
                                    // Default is 1, not more, on purpose: a single-room dispatch is always
                                    // ground truth for that room's clean time (see finishActiveCleanRun --
                                    // nothing to split, so the real elapsed time overwrites the estimate
                                    // outright). Since each run always picks whichever room is most overdue,
                                    // rotation naturally cycles through every room in turn -- keep this at 1
                                    // until every room has been cleaned at least once and you've built up a
                                    // real timing corpus, then raise it if you want faster multi-room runs.
                                    input "rotationCount_${mac}", "number", title: "Rooms per run", defaultValue: 1, required: true
                                    paragraph "Tip: leave this at 1 until every rotation room has been cleaned at least once -- each single-room " +
                                              "run directly measures that room's real clean time. Raise it later once you have real timings for everything."
                                }
                                input "rotationCycleDays_${mac}", "number",
                                    title: "Cycle length (days) for normal-traffic rooms — a room becomes eligible again after this many days, even if already cleaned this cycle",
                                    defaultValue: 7, required: true

                                def selectedIds = (settings["rotationRooms_${mac}"] ?: []).collect { it as Integer }
                                def selectedRoomOptions = rooms.findAll { (it.id as Integer) in selectedIds }.collectEntries { [(it.id.toString()): it.name] }
                                input "highTrafficRooms_${mac}", "enum",
                                    title: "High-traffic rooms — get their own shorter cycle below and are prioritized over normal-traffic rooms once due",
                                    options: selectedRoomOptions, multiple: true, required: false, submitOnChange: true

                                if (settings["highTrafficRooms_${mac}"]) {
                                    input "rotationCycleDaysHighTraffic_${mac}", "number",
                                        title: "Cycle length (days) for high-traffic rooms — e.g. 3 for roughly twice a week",
                                        defaultValue: 3, required: true
                                }

                                def pending = pendingRoomCount(mac)
                                paragraph "${pending} of ${(settings["rotationRooms_${mac}"] ?: []).size()} rotation room(s) are due for cleaning right now."
                            }
                        } else {
                            paragraph "Click Discover Rooms after the vacuum has completed at least one clean and has an active map with named rooms in the Wyze app."
                        }
                    }

                    section("<b>${vacLabel} — Room Timing</b>") {
                        def learning = state.learningMode?.getAt(mac)
                        if (learning) {
                            paragraph "Learning mode running — ${(learning.queue?.size() ?: 0)} more room(s) queued after the current one."
                            input "btnCancelLearning_${mac}", "button", title: "Cancel Learning", width: 3
                        } else {
                            paragraph "Cleans every selected rotation room by itself, one at a time, to directly measure each room's real clean " +
                                      "time (used to drive the \"time budget\" rotation mode) instead of estimating from mixed multi-room runs. " +
                                      "Takes a while — it works through every room in sequence."
                            input "btnLearnRooms_${mac}", "button", title: "Learn Room Times", width: 3
                        }
                        def avg = state.roomAvgMinutes?.getAt(mac)
                        if (avg) {
                            def known = state.discoveredRooms?.getAt(mac) ?: []
                            def lines = avg.collect { k, v -> "${known.find { it.id.toString() == k }?.name ?: "Room ${k}"}: ${String.format('%.1f', (v as Double))} min" }
                            paragraph "Known room times — ${lines.join(', ')}"
                        }

                        def roomsForTiming = state.discoveredRooms?.getAt(mac)
                        if (roomsForTiming) {
                            paragraph "Manually set or correct a room's clean-time estimate below — e.g. after reinstalling this app (which resets " +
                                      "learned timing data) so you don't have to re-earn it from scratch, or to fix a number you know is wrong. " +
                                      "Leave a field blank to leave that room's estimate untouched; only fields you fill in get applied. Fields show " +
                                      "the value as of when this page last loaded, not live — reopen the page to see the latest learned numbers."
                            roomsForTiming.each { room ->
                                input "roomTimeOverride_${mac}_${room.id}", "decimal",
                                    title: "${room.name} (minutes)",
                                    defaultValue: (avg?.getAt(room.id.toString())), required: false, width: 4
                            }
                            input "btnSetRoomTimes_${mac}", "button", title: "Save Room Times", width: 3
                        }
                    }

                    section("<b>${vacLabel} — Room Buttons</b>") {
                        def rooms = state.discoveredRooms?.getAt(mac)
                        if (rooms) {
                            paragraph "Assign rooms to fixed slots. Each slot is its own no-argument command (cleanRoomSlot1() … cleanRoomSlot8()) " +
                                      "on this vacuum's device — add one Dashboard tile per slot (same device, a different command each) for a " +
                                      "one-tap \"clean this room\" button. No typing, no picker, no extra devices."
                            def slotOptions = ["": "-- not assigned --"] + rooms.collectEntries { [(it.id.toString()): it.name] }
                            (1..8).each { n ->
                                input "roomSlot${n}_${mac}", "enum", title: "Slot ${n} room", options: slotOptions, required: false, submitOnChange: true
                            }
                        } else {
                            paragraph "Discover rooms first to assign room buttons."
                        }
                    }

                    section("<b>${vacLabel} — Mark Rooms as Cleaned</b>") {
                        def rooms3 = state.discoveredRooms?.getAt(mac)
                        if (rooms3) {
                            paragraph "Manually corrects rotation history without actually cleaning anything — for a room you cleaned by hand, " +
                                      "or a run whose completion never got recorded, so it stops getting picked first ahead of rooms that are actually more overdue."
                            def markOptions = rooms3.collectEntries { [(it.id.toString()): it.name] }
                            input "markCleanRooms_${mac}", "enum", title: "Rooms to mark as cleaned right now", options: markOptions, multiple: true, required: false, submitOnChange: true
                            input "btnMarkCleaned_${mac}", "button", title: "Mark as Cleaned", width: 3
                        } else {
                            paragraph "Discover rooms first."
                        }
                    }

                    section("<b>${vacLabel} — Low Battery Protection</b>") {
                        paragraph "Wyze's own firmware already returns to charge and resumes on its own at some internal threshold. This is a supplementary, " +
                                  "more conservative trigger you control — sends it back to dock as soon as battery drops below this while actively cleaning."
                        input "lowBatteryDockPercent_${mac}", "number",
                            title: "Dock if battery drops below this % while cleaning (0 = disabled, rely on the vacuum's own behavior)",
                            defaultValue: 0, required: false
                    }

                    section("<b>${vacLabel} — Bin Reminder</b>") {
                        input "emptyBinHours_${mac}", "number",
                            title: "Notify to empty the bin after this many cumulative cleaning hours (0 = disabled)",
                            defaultValue: 0, required: false
                        def hrs = (state.cleaningHoursSinceEmpty?.getAt(mac) ?: 0.0) as Double
                        paragraph "Cumulative cleaning time since last emptied: ${String.format('%.1f', hrs)} hours"
                        input "btnResetBin_${mac}", "button", title: "I emptied it — reset", width: 3
                    }
                }

                section("<b>Options</b>") {
                    input "isDebug", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true
                }
            }
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "btnLogin") {
        state.wyzeLoginError = null
        state.wyzeMfa = null
        loginWyze()
    } else if (btn == "btnSubmitMfa") {
        submitMfaCode()
    } else if (btn == "btnDiscover") {
        state.discoveryError = null
        discoverVacuums()
    } else if (btn.startsWith("btnDiscoverRooms_")) {
        def mac = btn - "btnDiscoverRooms_"
        state["roomError_${mac}"] = null
        discoverRooms(mac)
    } else if (btn.startsWith("btnResetBin_")) {
        resetBinTimer(btn - "btnResetBin_")
    } else if (btn.startsWith("btnLearnRooms_")) {
        startLearningMode(btn - "btnLearnRooms_")
    } else if (btn.startsWith("btnCancelLearning_")) {
        cancelLearningMode(btn - "btnCancelLearning_")
    } else if (btn.startsWith("btnMarkCleaned_")) {
        def mac = btn - "btnMarkCleaned_"
        def ids = (settings["markCleanRooms_${mac}"] ?: []).collect { it as Integer }
        if (ids) {
            markRoomsCleaned(mac, ids)
            def known = state.discoveredRooms?.getAt(mac) ?: []
            def names = ids.collect { id -> known.find { it.id == id }?.name ?: "Room ${id}" }
            def d = getChildDevice(mac)
            d?.sendEvent(name: "lastCleanedRooms", value: names.join(", "))
            if (d) updateRotationPreviewAttributes(d, mac)
        }
    } else if (btn.startsWith("btnSetRoomTimes_")) {
        setRoomTimesManually(btn - "btnSetRoomTimes_")
    }
}

// Lets a room's clean-time estimate be set/corrected directly, rather than
// only ever earned back through real cleaning runs -- e.g. after
// reinstalling this app (state.roomAvgMinutes is app-local and doesn't
// survive that) so timing data doesn't have to be re-learned from scratch,
// or to fix a number known to be wrong. Only rooms with a filled-in field
// are touched; anything left blank keeps whatever estimate it already had.
private void setRoomTimesManually(String mac) {
    def known = state.discoveredRooms?.getAt(mac) ?: []
    if (!known) return

    state.roomAvgMinutes = state.roomAvgMinutes ?: [:]
    def avgMap = state.roomAvgMinutes[mac] ?: [:]
    def updated = []
    known.each { room ->
        def val = settings["roomTimeOverride_${mac}_${room.id}"]
        if (val != null) {
            avgMap[room.id.toString()] = (val as Double)
            updated << room.name
        }
    }
    state.roomAvgMinutes[mac] = avgMap
    ifDebug("setRoomTimesManually(${mac}): manually set ${updated}")
}

// =================== Lifecycle ===================

def installed() { updated() }

def uninstalled() {
    unschedule()
    getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def updated() {
    unschedule()
    if (settings.isDebug) runIn(3600, logsOff)

    if (state.wyzeAccessToken && settings.selectedVacuums) {
        settings.selectedVacuums.each { mac -> ensureChildDevice(mac) }
        // remove child devices for macs the user deselected
        getAllChildDevices().each { d ->
            if (!(d.deviceNetworkId in settings.selectedVacuums)) deleteChildDevice(d.deviceNetworkId)
        }
        runIn(5, pollAllVacuums)
        state.currentPollMode = null // force rescheduleDynamicPoll to (re)schedule below
        rescheduleDynamicPoll()
    }
}

private String pollCron(String minutes) {
    switch (minutes) {
        case "1":  return "0 * * * * ?"
        case "2":  return "0 0/2 * * * ?"
        case "5":  return "0 0/5 * * * ?"
        case "10": return "0 0/10 * * * ?"
        case "15": return "0 0/15 * * * ?"
        case "30": return "0 0/30 * * * ?"
        default:   return "0 0/5 * * * ?"
    }
}

// Switches the scheduled poll's cadence based on whether any selected
// vacuum is currently cleaning -- faster (pollIntervalCleaning) while a
// run is active, slower (pollIntervalIdle) the rest of the time. Only
// actually reschedules when the mode changes, not on every poll.
private void rescheduleDynamicPoll() {
    // Also treat "we just dispatched a room-clean and are waiting on the
    // first poll to confirm it" as cleaning, not just a confirmed
    // lastKnownStatus=="Cleaning" -- otherwise, with the idle interval at its
    // default 15 min and single-room dispatches often finishing well inside
    // that window, a whole start-to-finish cleaning cycle could land
    // entirely between two idle-interval polls and never get caught at all,
    // silently skipping both the start/finish notifications and credit.
    def anyCleaning = settings.selectedVacuums?.any { mac ->
        state.lastKnownStatus?.getAt(mac) == "Cleaning" || state.activeCleanRun?.containsKey(mac)
    } ?: false
    def desiredMode = anyCleaning ? "cleaning" : "idle"
    if (state.currentPollMode == desiredMode) return

    def minutes = anyCleaning ? (settings.pollIntervalCleaning ?: "1") : (settings.pollIntervalIdle ?: "15")
    unschedule()
    schedule(pollCron(minutes), pollAllVacuums)
    state.currentPollMode = desiredMode
    ifDebug("rescheduleDynamicPoll: switched to ${desiredMode} polling (every ${minutes} min)")
}

private void ensureChildDevice(String mac) {
    def d = getChildDevice(mac)
    if (!d) {
        def label = state.discoveredVacuums?.get(mac) ?: "Wyze Vacuum ${mac}"
        log.info "Wyze Vacuum: creating child device for ${mac}"
        try {
            addChildDevice("brianwilson-hubitat", "Wyze Robot Vacuum Driver", mac, null,
                [name: "Wyze Vacuum", label: label, completedSetup: true])
        } catch (e) {
            log.error "Wyze Vacuum: failed to create child device: ${e.message}. Ensure 'Wyze Robot Vacuum Driver' is installed under Drivers Code."
        }
    }
}

// =================== Login / Auth ===================

private void loginWyze() {
    if (!state.wyzePhoneId) state.wyzePhoneId = java.util.UUID.randomUUID().toString()
    if (!settings.wyzeEmail || !settings.wyzePassword || !settings.wyzeKeyId || !settings.wyzeApiKey) {
        state.wyzeLoginError = "Enter Key Id, API Key, email, and password first."
        return
    }

    def hashedPw = md5Hex(md5Hex(md5Hex(settings.wyzePassword)))
    state.wyzePendingHashedPassword = hashedPw

    def nonce = now()
    def body = JsonOutput.toJson([nonce: "${nonce}", email: settings.wyzeEmail, password: hashedPw])

    Map result = null
    try {
        httpPost([
            uri: AUTH_BASE,
            path: "/api/user/login",
            requestContentType: "application/json",
            headers: [
                "x-api-key": WYZE_X_API_KEY,
                "keyid"    : settings.wyzeKeyId,
                "apikey"   : settings.wyzeApiKey,
                "user-agent": "hubitat-wyze-vacuum/1.0",
                "Accept-Encoding": "gzip"
            ],
            body: body,
            timeout: 30
        ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
    } catch (groovyx.net.http.HttpResponseException e) {
        state.wyzeLoginError = "Login failed (${e.statusCode}): ${e.message}"
        log.error "Wyze login failed: ${e.statusCode} ${e.message}"
        return
    } catch (e) {
        state.wyzeLoginError = "Login failed: ${e.message}"
        log.error "Wyze login error: ${e}"
        return
    }

    handleLoginResult(result)
}

private void handleLoginResult(Map result) {
    if (result?.access_token) {
        state.wyzeAccessToken  = result.access_token
        state.wyzeRefreshToken = result.refresh_token
        state.wyzeUserId       = result.user_id
        state.wyzeMfa = null
        state.wyzeLoginError = null
        ifDebug("Wyze login successful")
        discoverVacuums()
        return
    }

    def mfaOptions = result?.mfa_options
    if (mfaOptions && "TotpVerificationCode" in mfaOptions) {
        state.wyzeMfa = [
            type: "TotpVerificationCode",
            verificationId: result.mfa_details?.totp_apps?.getAt(0)?.app_id
        ]
        ifDebug("Wyze login requires TOTP 2FA")
        return
    }
    if (mfaOptions && "PrimaryPhone" in mfaOptions) {
        def smsResp = authPost("/user/login/sendSmsCode", [
            mfaPhoneType: "Primary",
            sessionId: result.sms_session_id,
            userId: result.user_id
        ])
        state.wyzeMfa = [
            type: "PrimaryPhone",
            verificationId: smsResp?.session_id
        ]
        ifDebug("Wyze login requires SMS 2FA")
        return
    }

    state.wyzeLoginError = "Login failed: ${result?.msg ?: result}"
    log.error "Wyze login failed: ${result}"
}

private void submitMfaCode() {
    if (!state.wyzeMfa || !settings.mfaCode) return
    def payload = [
        email: settings.wyzeEmail,
        password: state.wyzePendingHashedPassword,
        mfa_type: state.wyzeMfa.type,
        verification_id: state.wyzeMfa.verificationId,
        verification_code: settings.mfaCode
    ]
    def result = authPost("/user/login", payload)
    handleLoginResult(result)
}

private Map authPost(String path, Map body) {
    Map result = null
    try {
        httpPost([
            uri: AUTH_BASE,
            path: path,
            requestContentType: "application/json",
            headers: ["x-api-key": WYZE_X_API_KEY, "Accept-Encoding": "gzip"],
            body: JsonOutput.toJson(body),
            timeout: 30
        ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
    } catch (e) {
        log.error "Wyze auth POST ${path} failed: ${e}"
        return null
    }
    return result
}

private boolean refreshWyzeToken() {
    if (!state.wyzeRefreshToken) return false
    ifDebug("Refreshing Wyze access token")
    def resp = apiWyzeRequest("/app/user/refresh_token", [refresh_token: state.wyzeRefreshToken, sv: "d91914dd28b7492ab9dd17f7707d35a3"], false)
    def data = resp?.data ?: resp
    if (data?.access_token) {
        state.wyzeAccessToken = data.access_token
        if (data.refresh_token) state.wyzeRefreshToken = data.refresh_token
        ifDebug("Wyze token refresh succeeded")
        return true
    }
    log.error "Wyze token refresh failed: ${resp}"
    return false
}

// =================== Device Discovery ===================

private void discoverVacuums() {
    def resp = apiWyzeRequest("/app/v2/home_page/get_object_list", [sv: "c417b62d72ee44bf933054bdca183e77"])
    def list = resp?.data?.device_list
    if (list == null) {
        state.discoveryError = "Could not retrieve device list. Check credentials and logs."
        return
    }
    def found = [:]
    list.each { dev ->
        if (dev.product_model == VACUUM_PRODUCT_MODEL) {
            found[dev.mac] = dev.nickname ?: dev.mac
        }
    }
    if (!found) {
        state.discoveryError = "No Wyze vacuums found on this account."
        return
    }
    state.discoveredVacuums = found
    state.discoveryError = null
    ifDebug("Discovered vacuums: ${found}")
}

// =================== Polling ===================
//
// The scheduled poll uses asynchttpGet exclusively -- Hubitat throttles apps
// that make blocking HTTP calls from a scheduled job ("excessive hub load"),
// which synchronous httpGet/httpPost from a cron-triggered handler reliably
// tripped here. The two Venus reads (properties, status) are independent, so
// they're fired as two separate async calls with their own handlers rather
// than chained -- no need to synchronize their arrival. The Cleaning-session
// end handler reads the device's last-known cleanTime attribute (kept fresh
// by the properties poll throughout a session) instead of requiring a
// simultaneous fresh fetch.

def pollAllVacuums() {
    settings.selectedVacuums?.each { mac -> pollVacuum(mac) }
}

def pollVacuum(String mac) {
    if (!getChildDevice(mac)) return
    pollVacuumProps(mac)
    pollVacuumStatus(mac)
}

private void pollVacuumProps(String mac) {
    def keys = ["battary", "mode", "cleanlevel", "chargeState", "cleanSize", "cleanTime", "fault_code", "fault_type"]
    venusGetAsync("/plugin/venus/get_iot_prop", [did: mac, keys: keys.join(",")], "handleVacuumPropsResponse", [mac: mac])
}

private void pollVacuumStatus(String mac) {
    venusGetAsync("/plugin/venus/${mac}/status", [:], "handleVacuumStatusResponse", [mac: mac])
}

def handleVacuumPropsResponse(resp, data) {
    def mac = data?.mac
    def d = getChildDevice(mac)
    if (!d) return
    if (handleVenusAsyncError(resp, data, "handleVacuumPropsResponse")) return

    def props = parseAsyncJson(resp)?.data?.props
    if (props == null) { ifDebug("pollVacuum(${mac}): no props returned"); return }

    if (props.battary != null) {
        def batteryPct = toInt(props.battary)
        d.sendEvent(name: "battery", value: batteryPct, unit: "%")
        checkLowBatteryAutoDock(mac, batteryPct)
    }
    if (props.mode != null)       d.sendEvent(name: "mode", value: vacuumModeDescription(props.mode))
    if (props.cleanlevel != null) d.sendEvent(name: "suctionLevel", value: suctionLevelName(props.cleanlevel))
    if (props.chargeState != null) d.sendEvent(name: "charging", value: (toInt(props.chargeState) == 1) ? "true" : "false")
    if (props.cleanSize != null)  d.sendEvent(name: "cleanSize", value: toInt(props.cleanSize))
    if (props.cleanTime != null)  d.sendEvent(name: "cleanTime", value: toInt(props.cleanTime))
    updateFaultAttribute(d, mac, props)

    updateRotationPreviewAttributes(d, mac)
    d.sendEvent(name: "lastRefresh", value: new Date().format("MM/dd/yyyy HH:mm:ss", location.timeZone))
}

def handleVacuumStatusResponse(resp, data) {
    def mac = data?.mac
    def d = getChildDevice(mac)
    if (!d) return
    if (handleVenusAsyncError(resp, data, "handleVacuumStatusResponse")) return

    def statusData = parseAsyncJson(resp)?.data
    def workStatus = statusData?.heartBeat?.vacuum_work_status ?: statusData?.eventFlag?.vacuum_work_status
    def newStatus = workStatus != null ? vacuumStatusDescription(workStatus) : null
    if (newStatus == null) return

    // Log the raw code every time regardless of the workStatusCode attribute
    // (which has been unreliable showing up in the device UI after driver
    // updates) -- this is the reliable way to get real numbers to check the
    // unverified status label mapping against.
    log.info "Wyze Vacuum ${mac} vacuum_work_status=${workStatus} -> status=\"${newStatus}\""

    // Deliberately NOT d.currentValue("status") -- the driver's own command
    // methods (start/pause/dock/cleanNextRooms/etc.) optimistically write
    // that attribute themselves for immediate UI feedback, before this poll
    // ever runs. Reading it here would mean "previous status" is often
    // already overwritten by the very command that caused this transition,
    // so the transition would never be detected. Track our own copy instead,
    // updated only from confirmed poll data.
    state.lastKnownStatus = state.lastKnownStatus ?: [:]
    def prevStatus = state.lastKnownStatus[mac]

    d.sendEvent(name: "status", value: newStatus)
    // The status label mapping (1:Standby, 2:Cleaning, 3:Returning to
    // charge, ...) comes from a single third-party source (wyze-sdk),
    // unverified against this model's live telemetry. Expose the raw code
    // too so a mismatch (e.g. "Returning to charge" while charging:true)
    // can be reported with real numbers instead of guessed at.
    d.sendEvent(name: "workStatusCode", value: toInt(workStatus))
    // Keep the Switch capability's "switch" attribute honest against real
    // vacuum state, not just the last on()/off() the user tapped -- it flips
    // to "off" on its own once a clean actually finishes, gets docked, etc.
    d.sendEvent(name: "switch", value: newStatus == "Cleaning" ? "on" : "off")

    if (prevStatus != "Cleaning" && newStatus == "Cleaning") {
        state.cleaningSessionStart = state.cleaningSessionStart ?: [:]
        state.cleaningSessionStart[mac] = now()
        if (settings.notifyCleaningStarted) {
            def activeRoomIds = state.activeCleanRun?.getAt(mac)?.roomIds
            def roomDesc = activeRoomIds ? roomNamesFor(mac, activeRoomIds).join(", ") : "whole house"
            sendVacuumNotification("${d.displayName} started cleaning: ${roomDesc}.")
        }
    } else if (prevStatus == "Cleaning" && newStatus != "Cleaning") {
        handleCleaningSessionEnd(mac, d.currentValue("cleanTime"), d, newStatus)
    }
    state.lastKnownStatus[mac] = newStatus
    rescheduleDynamicPoll()

    updateRotationPreviewAttributes(d, mac)
    d.sendEvent(name: "lastRefresh", value: new Date().format("MM/dd/yyyy HH:mm:ss", location.timeZone))
}

private void venusGetAsync(String path, Map query, String callbackHandler, Map data) {
    if (!state.wyzeAccessToken) {
        log.warn "Wyze Vacuum: skipping ${path} for ${data?.mac} -- not logged in. Click Log In in the app."
        return
    }

    def nonce = now()
    def requestId = md5Hex(md5Hex(nonce.toString()))
    def signingKey = md5Hex("${state.wyzeAccessToken}${VENUS_SALT}")
    def qp = new TreeMap()
    (query ?: [:]).each { k, v -> qp[k] = v?.toString() }
    qp["nonce"] = nonce.toString()
    def sigString = qp.collect { k, v -> "${k}=${v}" }.join("&")
    def headers = [
        "access_token"   : state.wyzeAccessToken,
        "requestid"      : requestId,
        "appid"          : VENUS_APP_ID,
        "appinfo"        : "wyze_android_${APP_VERSION}",
        "phoneid"        : state.wyzePhoneId,
        "User-Agent"     : "wyze_android_${APP_VERSION}",
        "Accept-Encoding": "gzip",
        "signature2"     : hmacMd5Hex(signingKey, sigString)
    ]

    def asyncData = new LinkedHashMap(data ?: [:])
    asyncData["_venusPath"] = path
    asyncData["_venusQuery"] = query
    asyncData["_retried"] = false

    try {
        asynchttpGet(callbackHandler, [uri: VENUS_BASE, path: path, query: qp, headers: headers, timeout: 20], asyncData)
    } catch (e) {
        log.error "Wyze Venus async GET ${path} failed to dispatch: ${e}"
    }
}

// Returns true if the caller should stop (either a retry was dispatched, or a
// non-recoverable error was logged); false means the response is good to parse.
private boolean handleVenusAsyncError(resp, Map data, String callbackHandler) {
    Integer status = null
    try { status = resp?.status as Integer } catch (e) { status = null }
    boolean hasError = false
    try { hasError = resp?.hasError() as boolean } catch (e) { hasError = (status != null && status >= 400) }

    def mac = data?.mac

    if (!hasError) {
        // Wyze signals some failures (e.g. an expired access token) with an
        // HTTP 200 and an error code/message in the JSON body instead of a
        // real 401/403 status -- confirmed live: {code:2001, message:"Access
        // token error"}. That response has no "data" at all, so it was
        // silently surfacing as "no props returned" with no retry ever
        // firing. Treat it the same as a real 401/403.
        def body = parseAsyncJson(resp)
        if (isAuthErrorResponse(body)) {
            if (!data?._retried) {
                ifDebug("Wyze Venus ${callbackHandler} got an in-body auth error (${body?.code}: ${body?.message}) for ${mac}, refreshing token (async) and retrying once")
                def retryContext = new LinkedHashMap(data)
                retryContext["_retryCallback"] = callbackHandler
                refreshTokenAsync(retryContext)
            } else {
                log.error "Wyze Venus ${callbackHandler} still getting an auth error for ${mac} after a retry: ${body}"
            }
            return true
        }
        return false
    }

    if (status in [401, 403] && !data?._retried) {
        ifDebug("Wyze Venus ${callbackHandler} got ${status} for ${mac}, refreshing token (async) and retrying once")
        def retryContext = new LinkedHashMap(data)
        retryContext["_retryCallback"] = callbackHandler
        refreshTokenAsync(retryContext)
        return true
    }
    def errMsg = null
    try { errMsg = resp?.getErrorMessage() } catch (e) { errMsg = resp?.error }
    log.error "Wyze Venus ${callbackHandler} failed (${status}) for ${mac}: ${errMsg}"
    return true
}

// Wyze doesn't always use HTTP status codes for auth failures -- some come
// back as HTTP 200 with an error code/message in the body instead. Matches
// both, since we don't have a confirmed full enumeration of error shapes.
private boolean isAuthErrorResponse(Map result) {
    if (result == null) return false
    if (result.code?.toString() == "2001") return true
    def text = "${result.message ?: ''} ${result.msg ?: ''}".toLowerCase()
    return text.contains("access token")
}

private Map parseAsyncJson(resp) {
    try {
        if (resp?.json) return resp.json
        def text = resp?.data
        return text ? new JsonSlurper().parseText(text) : null
    } catch (e) {
        log.error "Wyze Vacuum: failed to parse async response: ${e}"
        return null
    }
}

// Async token refresh so a 401/403 encountered inside an async poll callback
// never has to make a blocking call to recover -- a synchronous call from
// inside an async handler tripped Hubitat's load guardrail just as much as
// the original synchronous poll did, just relocated to a different line.
private void refreshTokenAsync(Map retryContext) {
    if (!state.wyzeRefreshToken) {
        log.error "Wyze Vacuum: no refresh token available -- click Re-login in the app."
        return
    }
    def payload = new LinkedHashMap()
    payload["refresh_token"] = state.wyzeRefreshToken
    payload["sv"] = "d91914dd28b7492ab9dd17f7707d35a3"
    payload["access_token"] = state.wyzeAccessToken
    payload["app_name"] = "com.hualai"
    payload["app_ver"] = "com.hualai___${APP_VERSION}"
    payload["app_version"] = APP_VERSION
    payload["phone_id"] = state.wyzePhoneId
    payload["phone_system_type"] = "2"
    payload["sc"] = WYZE_SC
    payload["ts"] = now()

    try {
        asynchttpPost("handleTokenRefreshResponse", [
            uri: API_BASE, path: "/app/user/refresh_token", requestContentType: "application/json",
            headers: ["Connection": "keep-alive"], body: JsonOutput.toJson(payload), timeout: 20
        ], new LinkedHashMap(retryContext ?: [:]))
    } catch (e) {
        log.error "Wyze Vacuum: async token refresh dispatch failed: ${e}"
    }
}

def handleTokenRefreshResponse(resp, data) {
    boolean hasError = false
    try { hasError = resp?.hasError() as boolean } catch (e) { hasError = false }

    Map result = hasError ? null : parseAsyncJson(resp)
    def tokenData = result?.data ?: result

    if (!tokenData?.access_token) {
        def errMsg = null
        try { errMsg = resp?.getErrorMessage() } catch (e) { errMsg = resp?.error }
        log.error "Wyze Vacuum: async token refresh failed for ${data?.mac}: ${result ?: errMsg}. If this keeps happening, click Re-login in the app."
        return
    }

    state.wyzeAccessToken = tokenData.access_token
    if (tokenData.refresh_token) state.wyzeRefreshToken = tokenData.refresh_token
    ifDebug("Wyze token refresh succeeded (async)")

    def path = data?._venusPath
    def callbackHandler = data?._retryCallback
    if (path && callbackHandler) {
        def retryData = new LinkedHashMap(data)
        retryData["_retried"] = true
        venusGetAsync(path, data?._venusQuery, callbackHandler, retryData)
    }
}

// Wyze's own firmware already has some low-battery return-to-charge-then-
// resume behavior built in (observed live: mode 11 = "docked, cleaning will
// resume after charging", battery climbing while docked). This is a
// supplementary, user-controlled trigger point -- lets you dock earlier
// more conservatively than whatever threshold the vacuum uses internally.
// Calling dock() here is safe even if the vacuum would have self-docked
// shortly after anyway.
private void checkLowBatteryAutoDock(String mac, Integer batteryPct) {
    if (batteryPct == null) return
    def threshold = (settings["lowBatteryDockPercent_${mac}"] ?: 0) as Integer
    if (threshold <= 0) return // disabled

    def isCleaning = state.lastKnownStatus?.getAt(mac) == "Cleaning"
    state.lowBatteryDockTriggered = state.lowBatteryDockTriggered ?: [:]

    if (isCleaning && batteryPct < threshold) {
        if (!state.lowBatteryDockTriggered[mac]) {
            log.warn "Wyze Vacuum ${mac}: battery ${batteryPct}% below ${threshold}% threshold while cleaning — sending back to dock"
            state.lowBatteryDockTriggered[mac] = true
            dockVacuum(mac)
        }
    } else {
        // Reset once no longer cleaning or battery has recovered, so the
        // next time it drops below threshold this can trigger again.
        state.lowBatteryDockTriggered[mac] = false
    }
}

private void updateFaultAttribute(def d, String mac, Map props) {
    def faultCode = toInt(props.fault_code)
    def hasRawFault = faultCode && faultCode != 0
    def ignored = ignoredFaultCodesList()
    def isFault = hasRawFault && !(faultCode in ignored)

    d.sendEvent(name: "fault", value: isFault ? "${props.fault_type ?: faultCode}" : "none")

    // Log full context for *any* nonzero fault_code, even an ignored one --
    // this is the evidence trail for confirming/refuting which codes are
    // real problems vs. benign status codes (e.g. charging/fully charged)
    // that apparently share this same field.
    if (hasRawFault) {
        log.info "Wyze Vacuum ${mac} fault_code=${faultCode}${faultCode in ignored ? ' (ignored)' : ''} fault_type=${props.fault_type} mode=${props.mode} chargeState=${props.chargeState} status=${d.currentValue('status')} battery=${d.currentValue('battery')}"
    }

    state.lastNotifiedFault = state.lastNotifiedFault ?: [:]
    if (isFault) {
        if (settings.notifyStuck && state.lastNotifiedFault[mac] != faultCode) {
            sendVacuumNotification("${d.displayName} reported a fault: ${props.fault_type ?: faultCode}")
            state.lastNotifiedFault[mac] = faultCode
        }
    } else {
        state.lastNotifiedFault[mac] = null // clear so a future recurrence of the same fault code re-notifies
    }
}

private List ignoredFaultCodesList() {
    def raw = settings.ignoredFaultCodes ?: "2103,2105"
    return raw.split(",").collect { toInt(it.trim()) }.findAll { it != null }
}

private List<String> roomNamesFor(String mac, List ids) {
    def known = state.discoveredRooms?.getAt(mac) ?: []
    return (ids ?: []).collect { id -> known.find { it.id == id }?.name ?: "Room ${id}" }
}

// Fires once per Cleaning -> non-Cleaning transition, regardless of whether the
// clean finished naturally, was paused, or was interrupted by a dock/stop.
//
// NOTE: an earlier version of this tried to detect "returned to charge
// because the battery got critically low" (via a battery-percent threshold)
// and treat that as not-a-real-finish. Live data disproved that outright:
// a room legitimately finished (confirmed against the map) with the battery
// down at 21% -- low battery at dock time is apparently unremarkable, not a
// sign of an interrupted room. Reverted back to trusting the vacuum: any
// exit that isn't Paused/Error is a genuine finish, full stop.
private void handleCleaningSessionEnd(String mac, def reportedCleanTimeMinutes, def d, String newStatus) {
    def sessionStart = state.cleaningSessionStart?.getAt(mac)
    def reported = toInt(reportedCleanTimeMinutes)
    Integer elapsedMin = (reported != null && reported > 0)
        ? reported
        : (sessionStart ? (Math.max(0, Math.round((now() - sessionStart) / 60000.0)) as Integer) : 0)

    def run = state.activeCleanRun?.getAt(mac)
    Map finishResult = null
    if (run?.learning) {
        handleLearningRoomEnd(mac, run, elapsedMin, newStatus)
    } else if (run) {
        finishResult = finishActiveCleanRun(mac, elapsedMin, newStatus)
        continueSweepIfNeeded(mac)
    }

    accumulateBinHours(mac, elapsedMin)

    if (settings.notifyCleaningFinished && elapsedMin > 0) {
        def completedNames = finishResult?.completedNames
        def incompleteNames = finishResult?.incompleteNames
        String msg
        if (completedNames || incompleteNames) {
            def parts = []
            if (completedNames) parts << "cleaned: ${completedNames.join(', ')}"
            if (incompleteNames) parts << "not completed (will retry): ${incompleteNames.join(', ')}"
            msg = "${d?.displayName ?: mac} finished cleaning after ${elapsedMin} min -- ${parts.join('; ')}."
        } else {
            msg = "${d?.displayName ?: mac} finished cleaning after ${elapsedMin} min."
        }
        sendVacuumNotification(msg)
    }

    state.cleaningSessionStart?.remove(mac)
}

// Continues a rotation sweep (see cleanNextRooms) once a room-clean run
// genuinely finishes -- if a sweep is active for this vacuum and there's
// still at least one rotation room actually due, dispatches the next batch
// after a short delay; otherwise the sweep is done and clears itself.
private void continueSweepIfNeeded(String mac) {
    if (!(state.rotationSweepActive?.getAt(mac))) return
    if (pendingRoomCount(mac) > 0) {
        ifDebug("continueSweepIfNeeded(${mac}): more due rooms remain, continuing sweep")
        runIn(5, "continueSweepDispatch", [data: [mac: mac]])
    } else {
        ifDebug("continueSweepIfNeeded(${mac}): nothing else due, sweep finished")
        state.rotationSweepActive[mac] = false
    }
}

// Re-checks the sweep flag before dispatching -- if dock()/pause()/off() was
// called in the meantime (which clears rotationSweepActive), this quietly
// no-ops instead of reactivating a sweep the user just stopped.
def continueSweepDispatch(data) {
    def mac = data?.mac
    if (!mac || !(state.rotationSweepActive?.getAt(mac))) return
    cleanNextRooms(mac)
}

private void accumulateBinHours(String mac, Integer elapsedMin) {
    if (!elapsedMin || elapsedMin <= 0) return
    state.cleaningHoursSinceEmpty = state.cleaningHoursSinceEmpty ?: [:]
    double hrs = ((state.cleaningHoursSinceEmpty[mac] ?: 0.0) as Double) + (elapsedMin / 60.0)

    def threshold = (settings["emptyBinHours_${mac}"] ?: 0) as Double
    def d = getChildDevice(mac)
    if (threshold > 0 && hrs >= threshold) {
        sendVacuumNotification("${d?.displayName ?: mac} has cleaned for ${String.format('%.1f', hrs)} hours since the bin was last emptied — time to empty it.")
        hrs = 0.0
    }
    state.cleaningHoursSinceEmpty[mac] = hrs
    d?.sendEvent(name: "hoursSinceEmptied", value: Math.round(hrs * 10) / 10.0)
}

def resetBinTimer(String mac) {
    state.cleaningHoursSinceEmpty = state.cleaningHoursSinceEmpty ?: [:]
    state.cleaningHoursSinceEmpty[mac] = 0.0
    getChildDevice(mac)?.sendEvent(name: "hoursSinceEmptied", value: 0)
    ifDebug("resetBinTimer(${mac})")
}

private void sendVacuumNotification(String msg) {
    ifDebug("Notification: ${msg}")
    settings.notifyDevices?.each { it.deviceNotification(msg) }
}

private String vacuumStatusDescription(def code) {
    def map = [1: "Standby", 2: "Cleaning", 3: "Returning to charge", 4: "Docked", 5: "Mapping", 6: "Paused", 7: "Error"]
    return map[toInt(code)] ?: "Unknown (${code})"
}

private String suctionLevelName(def code) {
    def map = [1: "Quiet", 2: "Standard", 3: "Strong"]
    return map[toInt(code)] ?: "Unknown (${code})"
}

private String vacuumModeDescription(def code) {
    def c = toInt(code)
    if (c in [1, 30, 1101, 1201, 1301, 1401])   return "Cleaning"
    if (c in [4, 31, 1102, 1202, 1302, 1402])   return "Paused"
    if (c in [10, 32, 1103, 1203, 1303, 1403])  return "Cleaning completed, returning to charge"
    if (c == 5)                                 return "Returning to charge"
    if (c in [11, 33, 1104, 1204, 1304, 1404])  return "Docked, cleaning will resume"
    if (c in [0, 14, 29, 35, 40])                return "Idle"
    return "Mode ${c}"
}

// =================== Commands from Driver ===================

def startVacuum(String mac) {
    ifDebug("startVacuum: ${mac}")
    state.rotationSweepActive?.put(mac, false) // whole-house start is a different mode than room rotation
    venusControl(mac, 0, 1) // GLOBAL_SWEEPING / START
    pollVacuum(mac)
}

def pauseVacuum(String mac) {
    ifDebug("pauseVacuum: ${mac}")
    state.rotationSweepActive?.put(mac, false) // explicit stop -- don't auto-continue to the next room
    venusControl(mac, 0, 2) // GLOBAL_SWEEPING / PAUSE
    pollVacuum(mac)
}

def dockVacuum(String mac) {
    ifDebug("dockVacuum: ${mac}")
    state.rotationSweepActive?.put(mac, false) // explicit stop -- don't auto-continue to the next room
    venusControl(mac, 3, 1) // RETURN_TO_CHARGING / START
    pollVacuum(mac)
}

def setVacuumSuctionLevel(String mac, String level) {
    ifDebug("setVacuumSuctionLevel: ${mac} -> ${level}")
    def code = [Quiet: 1, Standard: 2, Strong: 3][level] ?: 2
    def body = [did: mac, model: VACUUM_PRODUCT_MODEL, cmd: "set_preference", params: [[ctrltype: 1, value: code]], is_sub_device: 0]
    venusRequest("POST", "/plugin/venus/set_iot_action", [:], body)
    pollVacuum(mac)
}

def refreshVacuum(String mac) {
    pollVacuum(mac)
}

private void venusControl(String mac, int type, int value, List rooms = null) {
    def body = [type: type, value: value, vacuumMopMode: 0]
    if (rooms) body["rooms_id"] = rooms
    def resp = venusRequest("POST", "/plugin/venus/${mac}/control", [:], body)
    // Wyze returns code as an integer (1), not the string "1" -- comparing
    // against a string here made every successful call log a false warning.
    if (resp != null && resp.code?.toString() != "1") log.warn "Wyze Vacuum control (${mac}) returned: ${resp}"
}

// =================== Room rotation ===================

def cleanRoomSlot(String mac, int slot) {
    def roomIdStr = settings["roomSlot${slot}_${mac}"]
    if (!roomIdStr) { log.warn "Wyze Vacuum: slot ${slot} has no room assigned for ${mac} — set it under Room Buttons"; return }

    def known = state.discoveredRooms?.getAt(mac) ?: []
    def room = known.find { it.id.toString() == roomIdStr }
    if (!room) { log.warn "Wyze Vacuum: slot ${slot} room (id ${roomIdStr}) not found for ${mac} — try Discover Rooms again"; return }

    ifDebug("cleanRoomSlot(${mac}, ${slot}) -> ${room.name}")
    dispatchRoomClean(mac, [room])
}

def cleanSpecificRooms(String mac, String roomNamesCsv) {
    def rooms = state.discoveredRooms?.getAt(mac)
    if (!rooms) { log.warn "Wyze Vacuum: no discovered rooms for ${mac} — click Discover Rooms first"; return }

    def wanted = roomNamesCsv.split(",").collect { it.trim().toLowerCase() }.findAll { it }
    def matched = rooms.findAll { it.name?.toLowerCase() in wanted }
    if (!matched) { log.warn "Wyze Vacuum: no rooms matched '${roomNamesCsv}' for ${mac}. Known rooms: ${rooms.collect { it.name }}"; return }

    dispatchRoomClean(mac, matched)
}

def cleanNextRooms(String mac) {
    def roomIds = settings["rotationRooms_${mac}"]
    if (!roomIds) { log.warn "Wyze Vacuum: no rotation rooms configured for ${mac}"; return }

    // Marks this vacuum as mid-sweep -- once the dispatched batch genuinely
    // finishes, continueSweepIfNeeded() will automatically call this again
    // for the next batch as long as something's still actually due, so a
    // single trigger works through the whole due-list instead of requiring
    // a fresh call per room. dock()/pause()/start() clear this flag again.
    state.rotationSweepActive = state.rotationSweepActive ?: [:]
    state.rotationSweepActive[mac] = true

    def chosen = previewNextRooms(mac)
    if (!chosen) { ifDebug("cleanNextRooms(${mac}): nothing to clean"); return }
    dispatchRoomClean(mac, chosen)
}

// Computes what cleanNextRooms(mac) would pick right now, without dispatching
// anything -- shared by the actual dispatch above and the nextRoomsToClean
// attribute so the two can never drift out of sync with each other.
private void updateRotationPreviewAttributes(def d, String mac) {
    d.sendEvent(name: "roomsPendingThisCycle", value: pendingRoomCount(mac))
    def next = previewNextRooms(mac)
    d.sendEvent(name: "nextRoomsToClean", value: next ? next.collect { it.name }.join(", ") : "none")
}

// Which cycle length applies to a given room -- the shorter high-traffic
// cycle if it's been marked as such, otherwise the normal/low-traffic one.
private Integer roomCycleDays(String mac, Integer roomId) {
    def highSet = (settings["highTrafficRooms_${mac}"] ?: []).collect { it as Integer } as Set
    if (roomId in highSet) {
        return (settings["rotationCycleDaysHighTraffic_${mac}"] ?: 3) as Integer
    }
    return (settings["rotationCycleDays_${mac}"] ?: 7) as Integer
}

private List previewNextRooms(String mac) {
    def roomIds = (settings["rotationRooms_${mac}"] ?: []).collect { it as Integer }
    if (!roomIds) return []

    // Exclude whatever's already actively being cleaned -- its "last
    // cleaned" timestamp won't update until that run actually finishes, so
    // without this a repeat call (or this preview, mid-run) would just
    // re-pick the same rooms already in progress instead of advancing to
    // the next group. Confirmed live: calling cleanNextRooms() again while
    // a batch was still running re-dispatched the identical rooms and
    // visibly did nothing, since the vacuum was already doing exactly that.
    def inProgress = (state.activeCleanRun?.getAt(mac)?.roomIds ?: []) as Set
    roomIds = roomIds.findAll { !(it in inProgress) }
    if (!roomIds) return []

    def known = state.discoveredRooms?.getAt(mac) ?: []
    def history = state.roomHistory?.getAt(mac) ?: [:]
    def nowMs = now()
    // Sort by how overdue each room is *relative to its own cycle length*,
    // not raw last-cleaned time -- a high-traffic room on a 3-day cycle
    // reaches "fully due" (fraction 1.0) three times as fast as a
    // normal-traffic room on a 7-day cycle, so it naturally rises to the
    // top of the pick order more often without a hard-gated separate queue.
    // Equivalent to the old plain oldest-first sort when every room shares
    // the same cycle length.
    def urgency = { Integer id ->
        def last = (history[id.toString()] ?: 0L) as Long
        def cycleMs = roomCycleDays(mac, id) * 24L * 60L * 60L * 1000L
        cycleMs > 0 ? (nowMs - last) / (double) cycleMs : 0.0
    }
    def candidates = roomIds.sort { a, b -> urgency(b) <=> urgency(a) }

    def mode = settings["rotationMode_${mac}"] ?: "count"
    def chosenIds = []
    if (mode == "time") {
        def budgetMin = (settings["rotationMinutes_${mac}"] ?: 30) as Integer
        def avgMap = state.roomAvgMinutes?.getAt(mac) ?: [:]
        def used = 0.0
        candidates.each { id ->
            if (used >= budgetMin && chosenIds) return
            chosenIds << id
            used += (avgMap[id.toString()] ?: 15.0) as Double
        }
    } else {
        def n = (settings["rotationCount_${mac}"] ?: 2) as Integer
        chosenIds = candidates.take(n)
    }

    return chosenIds.collect { id -> known.find { it.id == id } ?: [id: id, name: "Room ${id}"] }
}

private void dispatchRoomClean(String mac, List rooms) {
    def ids = rooms.collect { it.id as Integer }
    ifDebug("dispatchRoomClean(${mac}): ${rooms.collect { it.name }} (ids=${ids})")

    state.activeCleanRun = state.activeCleanRun ?: [:]
    state.activeCleanRun[mac] = [roomIds: ids, startedAt: now()]
    rescheduleDynamicPoll() // switch to fast polling immediately, don't wait on a poll to confirm "Cleaning" first

    venusControl(mac, 0, 1, ids) // GLOBAL_SWEEPING / START, scoped to rooms

    // Rooms are NOT marked cleaned here — only once the run actually ends
    // (see finishActiveCleanRun), so an interrupted run doesn't skip whatever
    // didn't get done. This just reflects what was targeted, for quick feedback.
    def d = getChildDevice(mac)
    d?.sendEvent(name: "lastCleanedRooms", value: rooms.collect { it.name }.join(", "))
    pollVacuum(mac)
}

private void markRoomsCleaned(String mac, List roomIds) {
    if (!roomIds) return
    state.roomHistory = state.roomHistory ?: [:]
    def h = state.roomHistory[mac] ?: [:]
    roomIds.each { id -> h[id.toString()] = now() }
    state.roomHistory[mac] = h
}

// Manually corrects rotation history without actually cleaning anything --
// for when a room was genuinely cleaned (by hand, or by a run whose
// completion never got recorded due to a bug) but the rotation doesn't
// know it, so it keeps getting picked first ahead of rooms that are
// actually more overdue.
def markRoomsCleanedByName(String mac, String roomNamesCsv) {
    def known = state.discoveredRooms?.getAt(mac) ?: []
    if (!known) { log.warn "Wyze Vacuum: no discovered rooms for ${mac} — click Discover Rooms first"; return }

    def wanted = roomNamesCsv.split(",").collect { it.trim().toLowerCase() }.findAll { it }
    def matched = known.findAll { it.name?.toLowerCase() in wanted }
    if (!matched) { log.warn "Wyze Vacuum: no rooms matched '${roomNamesCsv}' for ${mac}. Known rooms: ${known.collect { it.name }}"; return }

    def ids = matched.collect { it.id as Integer }
    markRoomsCleaned(mac, ids)

    def d = getChildDevice(mac)
    d?.sendEvent(name: "lastCleanedRooms", value: matched.collect { it.name }.join(", "))
    if (d) updateRotationPreviewAttributes(d, mac)
    ifDebug("markRoomsCleanedByName(${mac}): manually marked ${matched.collect { it.name }} as cleaned")
}

// Called once a room-scoped clean transitions out of "Cleaning". Wyze doesn't
// tell us which specific rooms finished, so we infer it: walk the dispatched
// rooms in order and consume elapsedMin against each room's known/estimated
// duration. A room only counts as done if its *full* estimate fit inside the
// time that elapsed — so an interrupted run under-credits rather than
// over-credits, and whatever didn't get done stays eligible next time. The
// time-estimate average is only refined when the whole batch completed
// cleanly, so a partial run doesn't skew future time-budget estimates.
private Map finishActiveCleanRun(String mac, Integer elapsedMin, String newStatus) {
    def run = state.activeCleanRun?.getAt(mac)
    if (!run) return [:]
    def rooms = run.roomIds ?: []

    state.roomAvgMinutes = state.roomAvgMinutes ?: [:]
    def avgMap = state.roomAvgMinutes[mac] ?: [:]

    // Rough job-effectiveness read: how much of the *whole* dispatched batch's
    // expected time actually elapsed, regardless of which individual rooms end
    // up credited below. This doesn't need a per-room completion record --
    // just the learned/estimated minutes for each room that was targeted --
    // so it works even for rooms whose exact finish point is ambiguous.
    double expectedTotal = rooms.sum { id -> (avgMap[id.toString()] ?: 15.0) as Double } ?: 0.0
    Integer completenessPct = expectedTotal > 0 ? Math.min(100, Math.round((elapsedMin ?: 0) / expectedTotal * 100)) as Integer : null
    if (completenessPct != null) {
        getChildDevice(mac)?.sendEvent(name: "lastRunCompleteness", value: completenessPct, unit: "%")
    }
    ifDebug("finishActiveCleanRun(${mac}): elapsedMin=${elapsedMin} expectedTotal=${expectedTotal}min across ${rooms.size()} room(s) -> completeness=${completenessPct}%")

    def completed
    def incomplete
    if (rooms.size() == 1) {
        // No batch to split -- whatever happened, happened to this one room,
        // so there's no need to guess against an estimate. Paused/Error is
        // the only genuinely ambiguous exit (could still resume); anything
        // else (Docked/Returning/Standby) means this room's pass is over.
        boolean genuinelyFinished = !(newStatus == "Paused" || newStatus == "Error") && elapsedMin != null && elapsedMin > 0
        completed = genuinelyFinished ? rooms : []
        incomplete = genuinelyFinished ? [] : rooms
        if (genuinelyFinished) {
            def roomName = (state.discoveredRooms?.getAt(mac) ?: []).find { it.id == rooms[0] }?.name ?: "Room ${rooms[0]}"
            log.info "Wyze Vacuum ${mac}: '${roomName}' took ${elapsedMin} min to clean"
        }
    } else {
        double remaining = elapsedMin ?: 0
        completed = []
        rooms.each { id ->
            double est = (avgMap[id.toString()] ?: 15.0) as Double
            if (remaining >= est) {
                completed << id
                remaining -= est
            }
        }
        incomplete = rooms - completed
    }

    markRoomsCleaned(mac, completed)

    if (incomplete.isEmpty() && rooms) {
        if (rooms.size() == 1) {
            // A single-room batch is ground truth -- overwrite outright
            // rather than blending, same treatment Learning Mode gives.
            avgMap[rooms[0].toString()] = (elapsedMin ?: 0) as Double
        } else {
            double perRoom = (elapsedMin ?: 0) / (double) rooms.size()
            rooms.each { id ->
                def key = id.toString()
                def prevAvg = avgMap[key]
                // exponential moving average so estimates keep improving with real runs
                avgMap[key] = prevAvg ? (prevAvg * 0.7 + perRoom * 0.3) : perRoom
            }
        }
        state.roomAvgMinutes[mac] = avgMap
    }

    def known = state.discoveredRooms?.getAt(mac) ?: []
    def completedNames = completed.collect { id -> known.find { it.id == id }?.name ?: "Room ${id}" }
    def incompleteNames = incomplete.collect { id -> known.find { it.id == id }?.name ?: "Room ${id}" }
    if (completedNames) {
        getChildDevice(mac)?.sendEvent(name: "lastCleanedRooms", value: completedNames.join(", "))
    }

    state.activeCleanRun.remove(mac)
    ifDebug("finishActiveCleanRun(${mac}): elapsedMin=${elapsedMin} completed=${completed} incomplete=${incomplete}")
    return [completedNames: completedNames, incompleteNames: incompleteNames]
}

// =================== Room-timing learning mode ===================
//
// Cleans the rotation rooms (or all discovered rooms, if none are selected
// for rotation yet) one at a time and records each one's directly-measured
// clean time -- a ground-truth reading rather than an inferred split of a
// multi-room batch. Runs across many poll cycles: each room dispatch sets a
// single-room activeCleanRun, and handleLearningRoomEnd advances to the next
// room once that one's Cleaning session ends.

def startLearningMode(String mac) {
    def d = getChildDevice(mac)
    if (d?.currentValue("status") == "Cleaning") {
        log.warn "Wyze Vacuum: ${mac} is already cleaning — dock or pause it before starting learning mode."
        return
    }

    def rooms = (settings["rotationRooms_${mac}"] ?: []).collect { it as Integer }
    if (!rooms) {
        rooms = (state.discoveredRooms?.getAt(mac) ?: []).collect { it.id as Integer }
    }
    if (!rooms) { log.warn "Wyze Vacuum: no rooms to learn for ${mac} — discover/select rooms first"; return }

    def known = state.discoveredRooms?.getAt(mac) ?: []
    def firstId = rooms[0]
    def firstRoom = known.find { it.id == firstId } ?: [id: firstId, name: "Room ${firstId}"]

    state.learningMode = state.learningMode ?: [:]
    state.learningMode[mac] = [queue: rooms.drop(1)]
    ifDebug("startLearningMode(${mac}): queue=${rooms}")
    dispatchLearningRoom(mac, firstRoom)
}

def cancelLearningMode(String mac) {
    state.learningMode?.remove(mac)
    getChildDevice(mac)?.sendEvent(name: "learningStatus", value: "Idle")
    ifDebug("cancelLearningMode(${mac})")
}

private void dispatchLearningRoom(String mac, Map room) {
    def id = room.id as Integer
    state.activeCleanRun = state.activeCleanRun ?: [:]
    state.activeCleanRun[mac] = [roomIds: [id], startedAt: now(), learning: true]
    rescheduleDynamicPoll() // switch to fast polling immediately, don't wait on a poll to confirm "Cleaning" first
    venusControl(mac, 0, 1, [id]) // GLOBAL_SWEEPING / START, scoped to this one room

    def remaining = state.learningMode?.getAt(mac)?.queue?.size() ?: 0
    def d = getChildDevice(mac)
    d?.sendEvent(name: "lastCleanedRooms", value: "Learning: ${room.name}")
    d?.sendEvent(name: "learningStatus", value: "Learning ${room.name} (${remaining} more queued)")
    pollVacuum(mac)
}

// Fires once the current learning-mode room's Cleaning session ends. Only a
// clean exit (not "Paused"/"Error") is trusted as a real measurement --
// anything else aborts the whole learning sequence rather than guessing.
private void handleLearningRoomEnd(String mac, Map run, Integer elapsedMin, String newStatus) {
    def d = getChildDevice(mac)
    def roomId = run.roomIds ? (run.roomIds[0] as Integer) : null

    if (newStatus == "Paused" || newStatus == "Error") {
        sendVacuumNotification("${d?.displayName ?: mac} learning mode stopped early — ${newStatus == "Paused" ? "cleaning was paused" : "the vacuum reported an error"} before this room's measurement finished.")
        d?.sendEvent(name: "learningStatus", value: "Stopped early")
        state.activeCleanRun.remove(mac)
        state.learningMode?.remove(mac)
        return
    }

    if (roomId != null && elapsedMin && elapsedMin > 0) {
        // A dedicated single-room pass is ground truth -- overwrite outright
        // rather than blending it in gradually like the multi-room EMA does.
        state.roomAvgMinutes = state.roomAvgMinutes ?: [:]
        def avgMap = state.roomAvgMinutes[mac] ?: [:]
        avgMap[roomId.toString()] = elapsedMin as Double
        state.roomAvgMinutes[mac] = avgMap
        markRoomsCleaned(mac, [roomId])
        ifDebug("learning mode (${mac}): room ${roomId} measured at ${elapsedMin} min")
    }

    state.activeCleanRun.remove(mac)

    def queue = state.learningMode?.getAt(mac)?.queue ?: []
    if (!queue) {
        sendVacuumNotification("${d?.displayName ?: mac} finished learning room times for all rooms.")
        d?.sendEvent(name: "learningStatus", value: "Idle")
        state.learningMode?.remove(mac)
        return
    }

    def nextId = queue[0]
    state.learningMode[mac] = [queue: queue.drop(1)]
    def known = state.discoveredRooms?.getAt(mac) ?: []
    def nextRoom = known.find { it.id == nextId } ?: [id: nextId, name: "Room ${nextId}"]
    ifDebug("learning mode (${mac}): advancing to ${nextRoom.name}")
    dispatchLearningRoom(mac, nextRoom)
}

private Integer pendingRoomCount(String mac) {
    def roomIds = settings["rotationRooms_${mac}"]
    if (!roomIds) return 0
    def history = state.roomHistory?.getAt(mac) ?: [:]
    def nowMs = now()
    return roomIds.count { id ->
        def rid = id as Integer
        def cutoff = nowMs - (roomCycleDays(mac, rid) * 24L * 60L * 60L * 1000L)
        (history[id.toString()] ?: 0L) < cutoff
    }
}

// =================== Map / room discovery ===================

private void discoverRooms(String mac) {
    def resp = venusRequest("GET", "/plugin/venus/memory_map/current_map", [did: mac])
    def blobB64 = resp?.data?.map
    if (!blobB64) {
        state["roomError_${mac}"] = "No active map found. Make sure the vacuum has completed at least one full clean."
        return
    }
    try {
        byte[] compressed = blobB64.decodeBase64()
        byte[] raw = zlibDecompress(compressed)
        def rooms = extractRoomsFromProtobuf(raw)
        if (!rooms) {
            state["roomError_${mac}"] = "Map found but no named rooms yet. Label rooms in the Wyze app first, then try again."
            return
        }
        state.discoveredRooms = state.discoveredRooms ?: [:]
        state.discoveredRooms[mac] = rooms
        state["roomError_${mac}"] = null
        ifDebug("discoverRooms(${mac}): ${rooms}")
    } catch (e) {
        state["roomError_${mac}"] = "Failed to parse map data: ${e.message}"
        log.error "Wyze Vacuum discoverRooms(${mac}) error: ${e}"
    }
}

private byte[] zlibDecompress(byte[] data) {
    def inflater = new Inflater()
    inflater.setInput(data)
    def out = new ByteArrayOutputStream(Math.max(256, data.length * 4))
    byte[] buf = new byte[4096]
    while (!inflater.finished()) {
        int n = inflater.inflate(buf)
        if (n == 0) {
            if (inflater.needsInput() || inflater.needsDictionary()) break
        }
        out.write(buf, 0, n)
    }
    inflater.end()
    return out.toByteArray()
}

// Minimal protobuf wire-format reader — only pulls what's needed (room id + name)
// out of Wyze's zlib-compressed map blob. Field 12 at the top level is a repeated
// RoomDataInfo submessage; within it, field 1 is roomId (varint) and field 2 is
// roomName (length-delimited UTF-8 bytes). Everything else is skipped.
private List readVarint(byte[] buf, int pos) {
    long result = 0
    int shift = 0
    int p = pos
    while (true) {
        int b = buf[p] & 0xFF
        result |= ((long) (b & 0x7F)) << shift
        p++
        if ((b & 0x80) == 0) break
        shift += 7
    }
    return [result, p]
}

private Map parseProtoFields(byte[] buf, int start, int end) {
    def fields = [:]
    int pos = start
    while (pos < end) {
        def tag = readVarint(buf, pos)
        pos = tag[1]
        int fieldNum = (int) ((long) tag[0] >>> 3)
        int wireType = (int) ((long) tag[0] & 0x7)
        switch (wireType) {
            case 0:
                def v = readVarint(buf, pos)
                pos = v[1]
                def list0 = fields[fieldNum] ?: []
                list0 << v[0]
                fields[fieldNum] = list0
                break
            case 1:
                pos += 8
                break
            case 2:
                def len = readVarint(buf, pos)
                pos = len[1]
                int sliceEnd = (int) (pos + (long) len[0])
                byte[] slice = new byte[sliceEnd - pos]
                for (int i = 0; i < slice.length; i++) slice[i] = buf[pos + i]
                pos = sliceEnd
                def list2 = fields[fieldNum] ?: []
                list2 << slice
                fields[fieldNum] = list2
                break
            case 5:
                pos += 4
                break
            default:
                pos = end // unknown wire type — bail out rather than loop forever
        }
    }
    return fields
}

private List extractRoomsFromProtobuf(byte[] buf) {
    def top = parseProtoFields(buf, 0, buf.length)
    def roomBlobs = top[12] ?: []
    def rooms = []
    roomBlobs.each { blob ->
        def rf = parseProtoFields((byte[]) blob, 0, ((byte[]) blob).length)
        def idList = rf[1]
        def nameList = rf[2]
        if (idList && nameList) {
            rooms << [id: ((long) idList[0]) as Integer, name: new String((byte[]) nameList[0], "UTF-8")]
        }
    }
    return rooms
}

// =================== Wyze API — signed Venus (vacuum) calls ===================

private Map venusRequest(String method, String path, Map query = [:], Map bodyMap = null, boolean retry = true) {
    if (!state.wyzeAccessToken) return null

    def nonce = now()
    def requestId = md5Hex(md5Hex(nonce.toString()))
    def signingKey = md5Hex("${state.wyzeAccessToken}${VENUS_SALT}")
    def headers = [
        "access_token": state.wyzeAccessToken,
        "requestid"   : requestId,
        "appid"       : VENUS_APP_ID,
        "appinfo"     : "wyze_android_${APP_VERSION}",
        "phoneid"     : state.wyzePhoneId,
        "User-Agent"  : "wyze_android_${APP_VERSION}",
        "Accept-Encoding": "gzip"
    ]

    Map result = null
    try {
        if (method == "GET") {
            def qp = new TreeMap()
            query.each { k, v -> qp[k] = v?.toString() }
            qp["nonce"] = nonce.toString()
            def sigString = qp.collect { k, v -> "${k}=${v}" }.join("&")
            headers["signature2"] = hmacMd5Hex(signingKey, sigString)

            httpGet([
                uri: VENUS_BASE, path: path, query: qp, headers: headers, timeout: 20
            ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
        } else {
            def payload = new LinkedHashMap(bodyMap ?: [:])
            payload["nonce"] = nonce.toString()
            def bodyJson = JsonOutput.toJson(payload)
            headers["signature2"] = hmacMd5Hex(signingKey, bodyJson)

            httpPost([
                uri: VENUS_BASE, path: path, requestContentType: "application/json",
                headers: headers, body: bodyJson, timeout: 20
            ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (retry && e.statusCode in [401, 403] && refreshWyzeToken()) {
            return venusRequest(method, path, query, bodyMap, false)
        }
        log.error "Wyze Venus ${method} ${path} failed (${e.statusCode}): ${e.message}"
        return null
    } catch (e) {
        log.error "Wyze Venus ${method} ${path} error: ${e}"
        return null
    }

    // Wyze signals some failures (e.g. an expired access token) with an HTTP
    // 200 and an error code/message in the body instead of a real 401/403 --
    // confirmed live: {code:2001, message:"Access token error"}. That never
    // threw HttpResponseException, so it was silently failing with no retry.
    if (retry && isAuthErrorResponse(result)) {
        ifDebug("Wyze Venus ${method} ${path} got an in-body auth error (${result?.code}: ${result?.message}), refreshing token and retrying once")
        if (refreshWyzeToken()) {
            return venusRequest(method, path, query, bodyMap, false)
        }
    }
    return result
}

// =================== Wyze API — unsigned general (api.wyzecam.com) calls ===================

private Map apiWyzeRequest(String path, Map extraBody = [:], boolean retry = true) {
    if (!state.wyzeAccessToken) return null

    def payload = new LinkedHashMap(extraBody)
    payload["access_token"] = state.wyzeAccessToken
    payload["app_name"] = "com.hualai"
    payload["app_ver"] = "com.hualai___${APP_VERSION}"
    payload["app_version"] = APP_VERSION
    payload["phone_id"] = state.wyzePhoneId
    payload["phone_system_type"] = "2"
    payload["sc"] = WYZE_SC
    payload["ts"] = now()

    Map result = null
    try {
        httpPost([
            uri: API_BASE, path: path, requestContentType: "application/json",
            headers: ["Connection": "keep-alive"], body: JsonOutput.toJson(payload), timeout: 20
        ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (retry && e.statusCode in [401, 403] && refreshWyzeToken()) {
            return apiWyzeRequest(path, extraBody, false)
        }
        log.error "Wyze API ${path} failed (${e.statusCode}): ${e.message}"
        return null
    } catch (e) {
        log.error "Wyze API ${path} error: ${e}"
        return null
    }

    if (retry && isAuthErrorResponse(result)) {
        ifDebug("Wyze API ${path} got an in-body auth error (${result?.code}: ${result?.message}), refreshing token and retrying once")
        if (refreshWyzeToken()) {
            return apiWyzeRequest(path, extraBody, false)
        }
    }
    return result
}

// =================== Crypto helpers ===================

private String md5Hex(String s) {
    return MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")).encodeHex().toString()
}

// HMAC-MD5 implemented directly against MessageDigest (no javax.crypto dependency)
private String hmacMd5Hex(String keyHexString, String message) {
    int blockSize = 64
    byte[] key = keyHexString.getBytes("UTF-8")
    if (key.length > blockSize) key = MessageDigest.getInstance("MD5").digest(key)

    byte[] paddedKey = new byte[blockSize]
    for (int i = 0; i < key.length; i++) paddedKey[i] = key[i]  // rest defaults to 0 — zero-padding for free

    byte[] oKeyPad = new byte[blockSize]
    byte[] iKeyPad = new byte[blockSize]
    for (int i = 0; i < blockSize; i++) {
        oKeyPad[i] = (byte) (paddedKey[i] ^ (byte) 0x5c)
        iKeyPad[i] = (byte) (paddedKey[i] ^ (byte) 0x36)
    }

    def md = MessageDigest.getInstance("MD5")
    md.update(iKeyPad)
    md.update(message.getBytes("UTF-8"))
    byte[] innerHash = md.digest()

    md = MessageDigest.getInstance("MD5")
    md.update(oKeyPad)
    md.update(innerHash)
    return md.digest().encodeHex().toString()
}

// =================== Utility ===================

private Integer toInt(def v) {
    if (v == null) return null
    try { return Math.round(v.toString().toDouble()) as Integer }
    catch (e) { return null }
}

def logsOff() {
    log.warn "Wyze Vacuum Connect: debug logging disabled"
    app.updateSetting("isDebug", [value: "false", type: "bool"])
}

private void ifDebug(String msg) {
    if (settings.isDebug) log.debug "Wyze Vacuum Connect: ${msg}"
}
