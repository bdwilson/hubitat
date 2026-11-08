/**
 *  Scrypted Connect
 *  Version: 0.1.0
 *
 *  Brings Scrypted NVR's verified detections (person / vehicle / animal / package)
 *  into Hubitat as virtual motion sensors, and lets Hubitat mute Scrypted's own
 *  app notifications per camera (or for all cameras) without stopping recording.
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
}

mappings {
    path("/event") {
        action: [POST: "handleEvent"]
    }
}

@groovy.transform.Field static final String HUB_DNI = "scrypted-connect-hub"
@groovy.transform.Field static final Map CLASS_OPTIONS = [
    "person": "Person", "vehicle": "Vehicle", "animal": "Animal", "package": "Package", "face": "Face"
]

def mainPage() {
    dynamicPage(name: "mainPage", title: "", install: false, uninstall: true, nextPage: "camerasPage") {
        section("<h2>Scrypted Connect</h2>") {
            paragraph "<i>See the <a href='https://github.com/bdwilson/hubitat/tree/scrypted-connect/Scrypted' target='_blank'>installation instructions</a>. " +
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
        section("<h3>Motion Disablers</h3>") {
            if (state.installed) {
                paragraph "Mute Scrypted notifications for selected cameras for a period of time when a contact, lock, motion or presence sensor changes."
                app(name: "scryptedMotionDisabler", appName: "Scrypted Connect Child - Motion Disabler", namespace: "brianwilson-hubitat",
                    title: "Add a Motion Disabler", multiple: true)
            } else {
                paragraph "Finish installing (click Next, pick cameras, Done), then come back here to add Motion Disablers."
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
        gapSeconds: settings.gapSeconds ?: 30
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
private void bridgePost(String route, Map body) {
    ifDebug("POST ${route} ${body}")
    Map params = [
        uri               : bridgeBase() + route,
        headers           : bridgeHeaders(),
        contentType       : "application/json",
        requestContentType: "application/json",
        body              : body,
        timeout           : 30
    ]
    asynchttpPost("bridgeResponse", params, [route: route])
}

def bridgeResponse(response, data) {
    def hub = getChildDevice(HUB_DNI)
    if (response.hasError()) {
        log.warn "Scrypted Connect: bridge ${data?.route} failed: HTTP ${response.status} ${response.getErrorMessage()}"
        hub?.setHealth("offline")
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

void muteCameras(List keys, Integer seconds) {
    if (!keys) return
    Map body = [cameras: keys]
    if (seconds) body.seconds = seconds
    bridgePost("mute", body)
}

void unmuteCameras(List keys) {
    if (!keys) return
    bridgePost("unmute", [cameras: keys])
}

void muteAll(Integer seconds) {
    Map body = [cameras: "all"]
    if (seconds) body.seconds = seconds
    bridgePost("mute", body)
}

void unmuteAll() {
    bridgePost("unmute", [cameras: "all"])
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
            cameraChild(data.camera?.key)?.applyMute(data.muted as Boolean, data.until as Long)
            Map muted = state.mutedCameras ?: [:]
            muted[data.camera?.key as String] = data.muted as Boolean
            state.mutedCameras = muted
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
            child.applyMute(cam.muted as Boolean, cam.until as Long)
            if (!cam.active && child.currentValue("motion") == "active") child.inactive()
        }
    }
    state.mutedCameras = (status.cameras ?: []).collectEntries { [(it.key as String): it.muted as Boolean] }
    getChildDevice(HUB_DNI)?.setBridgeVersion(status.version as String)
    updateHubMuteState()
}

// Hub device reflects all Scrypted cameras, not just the ones with Hubitat devices.
private void updateHubMuteState() {
    Map muted = state.mutedCameras ?: [:]
    getChildDevice(HUB_DNI)?.setMuteSummary(muted.count { k, v -> v } as Integer, muted.size() as Integer)
}

// ---------- logging ----------

def logsOff() {
    log.warn "Scrypted Connect: debug logging disabled"
    app.updateSetting("isDebug", [value: "false", type: "bool"])
}

void ifDebug(String msg) {
    if (settings.isDebug) log.debug "Scrypted Connect: ${msg}"
}
