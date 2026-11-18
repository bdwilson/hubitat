/**
 *  Scrypted Connect Camera
 *  Version: 0.2.0
 *
 *  One per Scrypted camera, created by the Scrypted Connect app.
 *    motion     active while Scrypted NVR verifies a selected detection type
 *               (person / vehicle / animal / package); raw pixel motion is ignored
 *    switch     on = Scrypted app notifications enabled for this camera,
 *               off = muted (recording continues)
 *    mute(seconds) mutes for a period; Scrypted restores notifications itself
 *
 *  Licensed under the Apache License, Version 2.0
 */
metadata {
    definition(
        name: "Scrypted Connect Camera",
        namespace: "brianwilson-hubitat",
        author: "Brian Wilson",
        importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/master/Scrypted/scrypted_connect-camera-driver.groovy"
    ) {
        capability "Motion Sensor"
        capability "Switch"
        capability "Sensor"
        capability "Refresh"

        command "mute", [[name: "seconds", type: "NUMBER", description: "Mute notifications for this many seconds (blank = until unmuted)"]]
        command "unmute"

        attribute "objects", "string"          // e.g. "person, vehicle" while active, "none" when inactive
        attribute "labels", "string"           // recognized names (faces/plates), if any
        attribute "score", "number"            // highest confidence in the current/last detection
        attribute "lastDetection", "string"    // human-readable description
        attribute "lastActive", "string"
        attribute "lastInactive", "string"
        attribute "duration", "number"         // seconds the last detection lasted
        attribute "notifications", "enum", ["enabled", "muted"]
        attribute "mutedUntil", "string"
    }
    preferences {
        input "maxActiveSeconds", "number", title: "Safety timeout: force motion inactive after this many seconds if Scrypted never reports the end",
            defaultValue: 900, range: "60..86400"
        input "txtEnable", "bool", title: "Log mute / unmute descriptions (with what caused them)", defaultValue: true
    }
}

def installed() {
    sendEvent(name: "motion", value: "inactive")
    sendEvent(name: "objects", value: "none")
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "notifications", value: "enabled")
}

// ---------- switch / mute ----------

def on() {
    parent.unmuteCameras([cameraKey()], "${device.displayName}: switch on")
}

def off() {
    parent.muteCameras([cameraKey()], null, "${device.displayName}: switch off")
}

def mute(seconds = null) {
    Integer secs = seconds ? (seconds as BigDecimal).intValue() : null
    parent.muteCameras([cameraKey()], secs, "${device.displayName}: mute command")
}

def unmute() {
    parent.unmuteCameras([cameraKey()], "${device.displayName}: unmute command")
}

def refresh() {
    parent.refreshAll()
}

private String cameraKey() {
    return getDataValue("cameraKey")
}

// ---------- called by the parent app ----------

void applyDetection(Map data) {
    String objects = (data.classes ?: []).join(", ") ?: "none"
    String labels = (data.labels ?: []).join(", ")
    if (data.state == "active") {
        String desc = "${objects.capitalize()} detected at ${device.displayName}${labels ? ' (' + labels + ')' : ''}"
        sendEvent(name: "objects", value: objects)
        sendEvent(name: "labels", value: labels ?: "none")
        if (data.maxScore != null) sendEvent(name: "score", value: data.maxScore)
        sendEvent(name: "lastDetection", value: desc)
        if (!data.update) sendEvent(name: "lastActive", value: nowString())
        sendEvent(name: "motion", value: "active", descriptionText: desc)
        runIn((settings.maxActiveSeconds ?: 900) as Integer, "inactive")
    } else if (data.state == "inactive") {
        if (data.durationSec != null) sendEvent(name: "duration", value: data.durationSec)
        if (data.maxScore != null) sendEvent(name: "score", value: data.maxScore)
        inactive()
    }
}

void inactive() {
    unschedule("inactive")
    if (device.currentValue("motion") == "inactive") return
    sendEvent(name: "objects", value: "none")
    sendEvent(name: "lastInactive", value: nowString())
    sendEvent(name: "motion", value: "inactive", descriptionText: "${device.displayName} detection ended")
}

// `reason` is who asked for the change (from the parent app), shown in the events' descriptionText.
// No reason on an unmute means Scrypted did it itself: a timed mute ran out, or it was undone there.
void applyMute(Boolean muted, Long until, String reason = null) {
    String sw = muted ? "off" : "on"
    String untilText = !muted ? "n/a" : (until ? new Date(until).format("yyyy-MM-dd HH:mm:ss", location.timeZone) : "until unmuted")
    Boolean switchChanged = device.currentValue("switch") != sw
    Boolean untilChanged = device.currentValue("mutedUntil") != untilText
    if (!switchChanged && !untilChanged) return   // periodic status sync with nothing new
    if (!muted && !switchChanged) {               // first sync after install
        sendEvent(name: "mutedUntil", value: untilText)
        return
    }

    String desc
    if (muted) {
        desc = "${device.displayName} notifications " + (switchChanged ?
            "muted " + (until ? "until ${untilText}" : "until unmuted") :
            "mute now " + (until ? "ends ${untilText}" : "lasts until unmuted"))
        if (reason) desc += " (${reason})"
    } else {
        Long prevUntil = state.muteUntil as Long
        String why = reason ?: (prevUntil && now() >= prevUntil - 5000 ? "timed mute ended" : "unmuted in Scrypted")
        desc = "${device.displayName} notifications restored (${why})"
    }
    state.muteUntil = muted ? until : null
    if (settings.txtEnable != false) log.info desc

    if (switchChanged) sendEvent(name: "switch", value: sw, descriptionText: desc)
    sendEvent(name: "notifications", value: muted ? "muted" : "enabled", descriptionText: desc)
    sendEvent(name: "mutedUntil", value: untilText, descriptionText: desc)
}

private String nowString() {
    return new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
}
