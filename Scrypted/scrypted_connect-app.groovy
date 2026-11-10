/**
 *  Scrypted Connect
 *  Version: 0.3.0
 *
 *  Brings Scrypted NVR's verified detections (person / vehicle / animal / package)
 *  into Hubitat as virtual motion sensors, and lets Hubitat mute Scrypted's own
 *  app notifications per camera (or for all cameras) without stopping recording.
 *  Home / Away: mutes chosen cameras while Hubitat is in a Home mode, except
 *  during sleep schedules.
 *
 *  Requires the companion Scrypted script (scrypted_connect-bridge.ts) running in
 *  Scrypted -> Plugins -> Scripts. OAuth must be enabled for this app in Apps Code.
 *
 *  Licensed under the Apache License, Version 2.0
 */
import groovy.json.JsonSlurper

definition(
    name: "Scrypted Connect",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Scrypted NVR detections as motion sensors, plus per-camera notification muting",
    category: "Safety & Security",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/scrypted-connect/Scrypted/scrypted_connect-app.groovy",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
    page(name: "camerasPage")
    page(name: "homeModePage")
}

mappings {
    path("/event") {
        action: [POST: "handleEvent"]
    }
}

@groovy.transform.Field static final String HUB_DNI = "scrypted-connect-hub"
@groovy.transform.Field static final String ALL_CAMERAS = "*"
@groovy.transform.Field static final Long REASON_TTL_MS = 60000L   // how long a mute reason waits for the bridge's report
@groovy.transform.Field static final Map CLASS_OPTIONS = [
    "person": "Person", "vehicle": "Vehicle", "animal": "Animal", "package": "Package", "face": "Face"
]
@groovy.transform.Field static final Map MUTE_TARGET_OPTIONS = [
    "auto": "Auto: Scrypted NVR notifiers, plus Advanced Notifier on cameras that use it",
    "nvr": "Scrypted NVR notifiers only",
    "advanced": "Advanced Notifier only",
    "both": "Both (warn if a camera isn't extended with Advanced Notifier)"
]
@groovy.transform.Field static final List DOW = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
@groovy.transform.Field static final Map SLEEP_DAY_OPTIONS = [
    "daily": "Every day", "weekdays": "Weekdays (Mon–Fri)", "weekends": "Weekends (Sat–Sun)", "custom": "Specific days"
]

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: false, uninstall: true, nextPage: "camerasPage") {
        section("<h2>Scrypted Connect</h2>") {
            paragraph "<i>See the <a href='https://github.com/bdwilson/hubitat/tree/master/Scrypted' target='_blank'>installation instructions</a>. " +
                "Install the <b>Scrypted Connect bridge</b> script in Scrypted first; its console prints the Bridge URL and Bridge Token.</i>"
            input "bridgeUrl", "text", title: "Bridge URL", description: "e.g. http://192.168.1.68:11080/endpoint/82/public/", required: true
            input "bridgeToken", "password", title: "Bridge Token", required: true
        }
        section("<h3>Detections</h3>") {
            input "classes", "enum", title: "Detection types that make a camera's motion sensor active", options: CLASS_OPTIONS,
                multiple: true, required: true, defaultValue: ["person", "vehicle", "animal", "package"]
            input "minScore", "decimal", title: "Minimum detection confidence (0.0 – 1.0)", required: true, defaultValue: 0.6, range: "0..1"
            input "gapSeconds", "number", title: "Seconds without a detection before motion goes inactive", required: true, defaultValue: 30, range: "5..3600"
        }
        section("<h3>Notification muting</h3>") {
            input "muteTarget", "enum", title: "When a camera is muted, silence", options: MUTE_TARGET_OPTIONS,
                required: true, defaultValue: "auto"
        }
        section("<h3>Motion Disablers</h3>") {
            if (state.installed) {
                paragraph "Mute Scrypted notifications for selected cameras for a period of time when a contact, lock, motion or presence sensor changes."
                app(name: "scryptedMotionDisabler", appName: "Scrypted Connect Child - Motion Disabler", namespace: "brianwilson-hubitat",
                    title: "Add a Motion Disabler", multiple: true)
            } else {
                paragraph "Finish installing (click Next, pick cameras, Done), then come back here to add Motion Disablers."
            }
        }
        section("<h3>Home / Away</h3>") {
            if (state.installed) {
                href(name: "toHomeModePage", page: "homeModePage", title: "Mute some cameras while home, with sleep-time exceptions",
                    description: homeModeSummary())
            } else {
                paragraph "Finish installing first, then come back here to set up Home / Away muting."
            }
        }
        section("Logging") {
            input "isDebug", "bool", title: "Enable debug logging (turns off after 30 minutes)", defaultValue: false
        }
    }
}

def camerasPage() {
    dynamicPage(name: "camerasPage", title: "", install: true, uninstall: false) {
        Map status = bridgeGet("status")
        if (status == null) {
            section("Error") {
                paragraph "<b>Could not reach the bridge.</b> ${state.lastBridgeError ?: ''}<br>Check the Bridge URL and Token, and that the Scrypted script is saved and running."
            }
            return
        }
        Map options = [:]
        status.cameras?.each { cam ->
            options[cam.key] = cam.hasDetector ? cam.name : "${cam.name} (no object detection)"
        }
        state.cameraNames = status.cameras?.collectEntries { [(it.key): it.name] } ?: [:]
        section("Cameras") {
            paragraph "Bridge ${status.version}: ${status.cameras?.size() ?: 0} cameras, ${status.notifiers?.size() ?: 0} Scrypted NVR notifiers " +
                "(${status.notifiers?.collect { it.name }?.join(', ')})."
            input "cameras", "enum", title: "Create a Hubitat device for these cameras", options: options, multiple: true, required: false
            paragraph "Unselecting a camera deletes its Hubitat device."
        }
    }
}

def homeModePage() {
    dynamicPage(name: "homeModePage", title: "", install: false, uninstall: false) {
        // Refresh the camera list so cameras without a Hubitat device can be chosen too.
        Map status = bridgeGet("status")
        if (status?.cameras) state.cameraNames = status.cameras.collectEntries { [(it.key): it.name] }
        section("<h3>Home / Away</h3>") {
            paragraph "While Hubitat is in one of the <b>Home modes</b>, Scrypted notifications from the cameras below are muted " +
                "(recording continues). Every other mode counts as <b>Away</b> and restores them. Changes apply when you click Done."
            input "homeModes", "mode", title: "Modes that count as Home", multiple: true, required: false
            input "homeMuteCameras", "enum", title: "When home, mute Scrypted notifications from", options: allCameraOptions(),
                multiple: true, required: false
        }
        section("<h3>But re-enable them while I sleep</h3>") {
            paragraph "Notifications come back during these times even when home. A window that runs past midnight belongs to the " +
                "day it starts (a Weekdays 11pm–6am window covers Monday night through Friday night)."
            ((state.sleepIds ?: []) as List).each { id ->
                input "sleepStart_${id}", "time", title: "From", required: true, width: 3
                input "sleepEnd_${id}", "time", title: "To", required: true, width: 3
                input "sleepDays_${id}", "enum", title: "On", options: SLEEP_DAY_OPTIONS, defaultValue: "daily", required: true,
                    width: 3, submitOnChange: true
                if (settings["sleepDays_${id}"] == "custom") {
                    input "sleepDow_${id}", "enum", title: "Days", options: DOW, multiple: true, required: true, width: 3
                }
                input "removeSleep_${id}", "button", title: "Remove this sleep time"
                paragraph "<hr>"
            }
            input "addSleep", "button", title: "Add sleep time"
        }
        section {
            paragraph "Right now: <b>${homeModeSummary()}</b>"
        }
    }
}

def appButtonHandler(String btn) {
    List ids = ((state.sleepIds ?: []) as List).collect { it as Integer }
    if (btn == "addSleep") {
        Integer next = ((state.nextSleepId ?: 0) as Integer) + 1
        state.nextSleepId = next
        state.sleepIds = ids + [next]
    } else if (btn.startsWith("removeSleep_")) {
        Integer id = btn.substring("removeSleep_".length()) as Integer
        state.sleepIds = ids.findAll { it != id }
        ["sleepStart_", "sleepEnd_", "sleepDays_", "sleepDow_"].each { app.removeSetting("${it}${id}") }
    }
}

// Every Scrypted camera (from the last bridge status), labelled with the Hubitat device name when there is one.
Map allCameraOptions() {
    Map opts = [:]
    (state.cameraNames ?: [:]).each { k, v ->
        def child = cameraChild(k as String)
        opts[k] = child ? child.displayName : v
    }
    return opts.sort { it.value }
}

// ---------- lifecycle ----------

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
    if (!state.accessToken) {
        try {
            createAccessToken()
        } catch (e) {
            log.error "Scrypted Connect: enable OAuth for this app in Apps Code, then open the app and click Done again (${e.message})"
            return
        }
    }
    state.installed = true
    if (settings.isDebug) runIn(1800, "logsOff")

    if (!getChildDevice(HUB_DNI)) {
        addChildDevice("brianwilson-hubitat", "Scrypted Connect Hub", HUB_DNI, [name: "Scrypted Connect", label: "Scrypted Connect", isComponent: false])
    }
    syncCameraDevices()
    pushConfig()
    runEvery5Minutes("pushConfig")
    initHomeMode()
}

private void syncCameraDevices() {
    List selected = settings.cameras ?: []
    Map names = state.cameraNames ?: [:]
    selected.each { String key ->
        String dni = cameraDni(key)
        if (!getChildDevice(dni)) {
            String label = names[key] ?: key
            def child = addChildDevice("brianwilson-hubitat", "Scrypted Connect Camera", dni, [name: label, label: label, isComponent: false])
            child.updateDataValue("cameraKey", key)
            log.info "Scrypted Connect: created device for ${label}"
        }
    }
    getChildDevices().each { child ->
        if (child.deviceNetworkId == HUB_DNI) return
        String key = child.getDataValue("cameraKey")
        if (!selected.contains(key)) {
            log.info "Scrypted Connect: removing unselected camera ${child.displayName}"
            deleteChildDevice(child.deviceNetworkId)
        }
    }
}

String cameraDni(String key) {
    return "scrypted:${key}"
}

// ---------- bridge communication ----------

private String bridgeBase() {
    String base = (settings.bridgeUrl ?: "").trim()
    return base.endsWith("/") ? base : base + "/"
}

private Map bridgeHeaders() {
    return ["Authorization": "Bearer ${settings.bridgeToken}"]
}

String callbackUrl() {
    return "${getFullLocalApiServerUrl()}/event?access_token=${state.accessToken}"
}

private Map configBody() {
    return [
        callback  : callbackUrl(),
        classes   : settings.classes ?: ["person", "vehicle", "animal", "package"],
        minScore  : settings.minScore != null ? settings.minScore : 0.6,
        gapSeconds: settings.gapSeconds ?: 30,
        muteTarget: settings.muteTarget ?: "auto"
    ]
}

// Synchronous GET, used only by the settings pages.
private Map bridgeGet(String route) {
    Map result = null
    try {
        httpGet([uri: bridgeBase() + route, headers: bridgeHeaders(), contentType: "application/json", timeout: 10]) { resp ->
            result = resp.data as Map
        }
        state.lastBridgeError = null
    } catch (e) {
        state.lastBridgeError = e.message
        log.warn "Scrypted Connect: bridge ${route} failed: ${e.message}"
    }
    return result
}

// Asynchronous POST; every bridge reply carries full status, which is applied to devices.
private void bridgePost(String route, Map body, Map extra = [:]) {
    ifDebug("POST ${route} ${body}")
    Map params = [
        uri               : bridgeBase() + route,
        headers           : bridgeHeaders(),
        contentType       : "application/json",
        requestContentType: "application/json",
        body              : body,
        timeout           : 30
    ]
    asynchttpPost("bridgeResponse", params, [route: route] + extra)
}

def bridgeResponse(response, data) {
    def hub = getChildDevice(HUB_DNI)
    if (response.hasError()) {
        log.warn "Scrypted Connect: bridge ${data?.route} failed: HTTP ${response.status} ${response.getErrorMessage()}"
        hub?.setHealth("offline")
        // Undo the Home / Away bookkeeping so the next evaluation retries.
        List ours = (state.homeMuted ?: []) as List
        if (data?.home == "mute") state.homeMuted = ours - (data.keys as List)
        if (data?.home == "unmute") state.homeMuted = (ours + (data.keys as List)).unique()
        return
    }
    hub?.setHealth("online")
    def json = null
    try {
        json = response.getJson()
    } catch (e) {
        log.warn "Scrypted Connect: unreadable bridge reply to ${data?.route}: ${e.message}"
        return
    }
    applyStatus(json)
}

// Re-sent every 5 minutes: keeps the bridge's callback current and doubles as a health check.
def pushConfig() {
    if (!settings.bridgeUrl || !settings.bridgeToken || !state.accessToken) return
    bridgePost("config", configBody())
}

// ---------- called by child devices / child apps ----------

// `reason` says who asked (e.g. "Motion Disabler 'Front Door': Front Door Lock unlocked"). It's shown in
// the camera device's event descriptionText when the bridge reports the resulting mute/unmute.
void muteCameras(List keys, Integer seconds, String reason = null) {
    if (!keys) return
    Map body = [cameras: keys]
    if (seconds) body.seconds = seconds
    noteReason(keys, true, reason)
    bridgePost("mute", body)
}

void unmuteCameras(List keys, String reason = null) {
    if (!keys) return
    noteReason(keys, false, reason)
    bridgePost("unmute", [cameras: keys])
}

void muteAll(Integer seconds, String reason = null) {
    Map body = [cameras: "all"]
    if (seconds) body.seconds = seconds
    noteReason([ALL_CAMERAS], true, reason)
    bridgePost("mute", body)
}

void unmuteAll(String reason = null) {
    noteReason([ALL_CAMERAS], false, reason)
    bridgePost("unmute", [cameras: "all"])
}

// ---------- mute reasons ----------
//
// The bridge reports mutes back asynchronously and doesn't know who asked, so the app remembers the
// reason per camera for a short while and hands it to the camera device when the matching report
// (same direction: mute or unmute) arrives. A report with no remembered reason came from Scrypted
// itself, e.g. a timed mute running out.

private void noteReason(List keys, Boolean muted, String reason) {
    if (!reason) return
    Map reasons = pruneReasons()
    keys.each { reasons[it as String] = [text: reason, muted: muted, at: now()] }
    state.muteReasons = reasons
}

private String reasonFor(String key, Boolean muted) {
    Map reasons = pruneReasons()
    Map r = [reasons[key], reasons[ALL_CAMERAS]].find { it && (it.muted as Boolean) == muted } as Map
    return r?.text as String
}

private Map pruneReasons() {
    Long cutoff = now() - REASON_TTL_MS
    return ((state.muteReasons ?: [:]) as Map).findAll { k, v -> (v.at as Long) >= cutoff }
}

void refreshAll() {
    pushConfig()
}

Map cameraOptions() {
    Map opts = [:]
    getChildDevices().each { child ->
        String key = child.getDataValue("cameraKey")
        if (key) opts[key] = child.displayName
    }
    return opts
}

// ---------- events from the bridge ----------

def handleEvent() {
    Map data = null
    try {
        data = request.JSON as Map
    } catch (ignored) { }
    if (data == null && request.body) {
        try {
            data = new JsonSlurper().parseText(request.body) as Map
        } catch (e) {
            log.warn "Scrypted Connect: unparseable event ${request.body}"
        }
    }
    if (data == null) {
        return render(contentType: "application/json", data: '{"ok":false}', status: 400)
    }
    ifDebug("event ${data}")
    getChildDevice(HUB_DNI)?.setHealth("online")

    switch (data.type) {
        case "detection":
            cameraChild(data.camera?.key)?.applyDetection(data)
            break
        case "mute":
            String key = data.camera?.key as String
            cameraChild(key)?.applyMute(data.muted as Boolean, data.until as Long, reasonFor(key, data.muted as Boolean))
            Map muted = state.mutedCameras ?: [:]
            muted[key] = data.muted as Boolean
            state.mutedCameras = muted
            Map indef = state.indefiniteMutes ?: [:]
            if (data.muted && !data.until) {
                indef[key] = true
            } else {
                indef.remove(key)
            }
            state.indefiniteMutes = indef
            updateHubMuteState()
            break
        case "hello":
            applyStatus(data.status as Map)
            // A restarted bridge may have lost its config; re-send it.
            if (!data.status?.callbackConfigured) pushConfig()
            break
        default:
            log.warn "Scrypted Connect: unknown event type ${data.type}"
    }
    render contentType: "application/json", data: '{"ok":true}', status: 200
}

private def cameraChild(String key) {
    return key ? getChildDevice(cameraDni(key)) : null
}

private void applyStatus(Map status) {
    if (!status) return
    status.cameras?.each { cam ->
        def child = cameraChild(cam.key)
        if (child) {
            child.applyMute(cam.muted as Boolean, cam.until as Long, reasonFor(cam.key as String, cam.muted as Boolean))
            if (!cam.active && child.currentValue("motion") == "active") child.inactive()
        }
    }
    state.mutedCameras = (status.cameras ?: []).collectEntries { [(it.key as String): it.muted as Boolean] }
    state.indefiniteMutes = (status.cameras ?: []).findAll { it.muted && !it.until }.collectEntries { [(it.key as String): true] }
    getChildDevice(HUB_DNI)?.setBridgeVersion(status.version as String)
    updateHubMuteState()
}

// Hub device reflects all Scrypted cameras, not just the ones with Hubitat devices.
private void updateHubMuteState() {
    Map muted = state.mutedCameras ?: [:]
    getChildDevice(HUB_DNI)?.setMuteSummary(muted.count { k, v -> v } as Integer, muted.size() as Integer)
}

// ---------- Home / Away ----------
//
// While location.mode is a Home mode and no sleep window is active, the chosen cameras are muted
// until unmuted. The app remembers which cameras it muted (state.homeMuted) and only acts on the
// difference between that and what it wants now, so a manual unmute while home sticks until the
// next Home/Away/sleep change, and cameras it didn't mute are never unmuted by it.

private void initHomeMode() {
    subscribe(location, "mode", "modeHandler")
    sleepWindows().each { w ->
        [w.start, w.end].each { Integer m ->
            schedule("5 ${m % 60} ${m.intdiv(60)} ? * *", "evaluateHomeMode", [overwrite: false])
        }
    }
    runEvery5Minutes("evaluateHomeMode")   // catches up after a hub reboot or a failed bridge call
    evaluateHomeMode()
}

def modeHandler(evt) {
    evaluateHomeMode()
}

// "off" (not set up), "away", "home" (muting) or "sleeping" (home, inside a sleep window)
private String homeState() {
    if (!settings.homeModes || !settings.homeMuteCameras) return "off"
    if (!((settings.homeModes as List).contains(location.mode))) return "away"
    return isSleeping() ? "sleeping" : "home"
}

def evaluateHomeMode() {
    String hs = homeState()
    String prev = state.lastHomeState as String
    String reason = "Home / Away: " + (
        hs == "home" ? (prev == "sleeping" ? "sleep time ended (mode ${location.mode})" : "Home mode ${location.mode}") :
        hs == "sleeping" ? "sleep time started" :
        hs == "away" ? "Away mode ${location.mode}" : "turned off")
    if (hs != prev) {
        log.info "Scrypted Connect: Home / Away is now ${hs} (Hubitat mode ${location.mode})"
        state.lastHomeState = hs
    }
    getChildDevice(HUB_DNI)?.setHomeMode(hs)
    if (!settings.bridgeUrl || !settings.bridgeToken) return

    List desired = hs == "home" ? ((settings.homeMuteCameras ?: []) as List) : []
    List ours = (state.homeMuted ?: []) as List
    Map indef = state.indefiniteMutes ?: [:]
    // Leave alone cameras someone else already muted until unmuted; they're muted anyway.
    List toMute = (desired - ours).findAll { !indef[it] }
    List toUnmute = ours - desired
    if (toMute) {
        state.homeMuted = ours + toMute
        log.info "Scrypted Connect: home, muting ${cameraNamesFor(toMute)}"
        noteReason(toMute, true, reason)
        bridgePost("mute", [cameras: toMute], [home: "mute", keys: toMute])
    }
    if (toUnmute) {
        state.homeMuted = ((state.homeMuted ?: []) as List) - toUnmute
        log.info "Scrypted Connect: ${hs == 'sleeping' ? 'sleep time' : hs}, restoring ${cameraNamesFor(toUnmute)}"
        noteReason(toUnmute, false, reason)
        bridgePost("unmute", [cameras: toUnmute], [home: "unmute", keys: toUnmute])
    }
}

private String cameraNamesFor(List keys) {
    Map names = allCameraOptions()
    return keys.collect { names[it] ?: it }.join(", ")
}

private String homeModeSummary() {
    switch (homeState()) {
        case "off": return "Not set up (choose Home modes and cameras)"
        case "away": return "Away (${location.mode}): notifications on"
        case "sleeping": return "Home (${location.mode}), sleep time: notifications on"
        default: return "Home (${location.mode}): muting ${cameraNamesFor((settings.homeMuteCameras ?: []) as List)}"
    }
}

private List sleepWindows() {
    List out = []
    ((state.sleepIds ?: []) as List).each { id ->
        String s = settings["sleepStart_${id}"]
        String e = settings["sleepEnd_${id}"]
        if (!s || !e) return
        String days = settings["sleepDays_${id}"] ?: "daily"
        List dayList = days == "weekdays" ? ["Mon", "Tue", "Wed", "Thu", "Fri"] :
            days == "weekends" ? ["Sat", "Sun"] :
            days == "custom" ? ((settings["sleepDow_${id}"] ?: []) as List) : DOW
        out << [start: minuteOfDay(toDateTime(s)), end: minuteOfDay(toDateTime(e)), days: dayList]
    }
    return out
}

private Integer minuteOfDay(Date d) {
    Calendar cal = Calendar.getInstance(location.timeZone)
    cal.setTime(d)
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

private String dayName(Date d) {
    Calendar cal = Calendar.getInstance(location.timeZone)
    cal.setTime(d)
    return DOW[cal.get(Calendar.DAY_OF_WEEK) - 1]
}

// A window that crosses midnight belongs to the day it starts.
private boolean isSleeping() {
    Date nowDate = new Date()
    Integer m = minuteOfDay(nowDate)
    String today = dayName(nowDate)
    String yesterday = dayName(new Date(nowDate.time - 86400000L))
    return sleepWindows().any { w ->
        if (w.start == w.end) return false
        if (w.start < w.end) return w.days.contains(today) && m >= w.start && m < w.end
        return (w.days.contains(today) && m >= w.start) || (w.days.contains(yesterday) && m < w.end)
    }
}

// ---------- logging ----------

def logsOff() {
    log.warn "Scrypted Connect: debug logging disabled"
    app.updateSetting("isDebug", [value: "false", type: "bool"])
}

void ifDebug(String msg) {
    if (settings.isDebug) log.debug "Scrypted Connect: ${msg}"
}
