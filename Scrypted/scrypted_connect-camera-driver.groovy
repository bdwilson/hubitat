/**
 *  Scrypted Connect Camera
 *  Version: 0.1.0
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
        importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/scrypted-connect/Scrypted/scrypted_connect-camera-driver.groovy"
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
    parent.unmuteCameras([cameraKey()])
}

def off() {
    parent.muteCameras([cameraKey()], null)
}

def mute(seconds = null) {
    Integer secs = seconds ? (seconds as BigDecimal).intValue() : null
    parent.muteCameras([cameraKey()], secs)
}

def unmute() {
    parent.unmuteCameras([cameraKey()])
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

void applyMute(Boolean muted, Long until) {
    String sw = muted ? "off" : "on"
    String untilText = !muted ? "" : (until ? new Date(until).format("yyyy-MM-dd HH:mm:ss", location.timeZone) : "until unmuted")
    if (device.currentValue("switch") != sw) {
        sendEvent(name: "switch", value: sw, descriptionText: "${device.displayName} Scrypted notifications ${muted ? 'muted' : 'enabled'}")
    }
    sendEvent(name: "notifications", value: muted ? "muted" : "enabled")
    sendEvent(name: "mutedUntil", value: untilText ?: "n/a")
}

private String nowString() {
    return new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
}
