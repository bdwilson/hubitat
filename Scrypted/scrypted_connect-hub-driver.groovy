/**
 *  Scrypted Connect Hub
 *  Version: 0.2.0
 *
 *  Parent-level device created by the Scrypted Connect app.
 *    switch on  = Scrypted notifications enabled for every camera
 *    switch off = Scrypted notifications muted for every camera (recording continues)
 *  muteAll(seconds) mutes every camera for a period; Scrypted restores them itself.
 *
 *  Licensed under the Apache License, Version 2.0
 */
metadata {
    definition(
        name: "Scrypted Connect Hub",
        namespace: "brianwilson-hubitat",
        author: "Brian Wilson",
        importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/scrypted-connect/Scrypted/scrypted_connect-hub-driver.groovy"
    ) {
        capability "Switch"
        capability "Refresh"
        capability "Sensor"

        command "muteAll", [[name: "seconds", type: "NUMBER", description: "Mute all cameras for this many seconds (blank = until unmuted)"]]
        command "unmuteAll"

        attribute "healthStatus", "enum", ["online", "offline"]
        attribute "mutedCameras", "number"
        attribute "totalCameras", "number"
        attribute "notifications", "enum", ["enabled", "partially muted", "muted"]
        attribute "bridgeVersion", "string"
        attribute "homeMode", "enum", ["off", "away", "home", "sleeping"]
    }
}

def installed() {
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "healthStatus", value: "offline")
}

def on() {
    parent.unmuteAll("${device.displayName}: switch on")
}

def off() {
    parent.muteAll(null, "${device.displayName}: switch off")
}

def muteAll(seconds = null) {
    Integer secs = seconds ? (seconds as BigDecimal).intValue() : null
    parent.muteAll(secs, "${device.displayName}: muteAll command")
}

def unmuteAll() {
    parent.unmuteAll("${device.displayName}: unmuteAll command")
}

def refresh() {
    parent.refreshAll()
}

// ---------- called by the parent app ----------

void setHealth(String value) {
    if (device.currentValue("healthStatus") != value) {
        sendEvent(name: "healthStatus", value: value, descriptionText: "Scrypted bridge is ${value}")
        if (value == "offline") log.warn "${device.displayName}: Scrypted bridge is offline"
    }
}

// off = Home / Away not set up, away, home (muting the chosen cameras), sleeping (home, inside a sleep window)
void setHomeMode(String value) {
    if (device.currentValue("homeMode") != value) {
        sendEvent(name: "homeMode", value: value, descriptionText: "Scrypted Connect Home / Away is ${value}")
    }
}

void setBridgeVersion(String version) {
    if (version) sendEvent(name: "bridgeVersion", value: version)
}

void setMuteSummary(Integer muted, Integer total) {
    sendEvent(name: "mutedCameras", value: muted)
    sendEvent(name: "totalCameras", value: total)
    String summary = muted == 0 ? "enabled" : (muted >= total ? "muted" : "partially muted")
    sendEvent(name: "notifications", value: summary)
    // off only when every camera is muted
    sendEvent(name: "switch", value: (total > 0 && muted >= total) ? "off" : "on")
}
