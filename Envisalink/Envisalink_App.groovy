/**
 *  Envisalink Security App for Hubitat
 *
 *  Native TPI integration for Honeywell Vista panels via EnvisaLink.
 *  No SmartThings Node Proxy required.
 *
 *  Based on original SmartThings work by redloro@gmail.com,
 *  updated for Hubitat by bubba@bubba.org,
 *  native TPI port by bdwilson
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

definition(
    name: "Envisalink Security",
    namespace: "bdwilson",
    author: "bdwilson",
    description: "Native EnvisaLink TPI integration for Honeywell Vista panels — no proxy required",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
    page(name: "zonesPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Envisalink Security", install: true, uninstall: true) {
        section("EnvisaLink Device") {
            input "evlAddress",  "text",     title: "IP Address",        description: "e.g. 192.168.1.11", required: true
            input "evlPort",     "number",   title: "Port",              defaultValue: 4025, required: true
            input "evlPassword", "password", title: "Network Password",  defaultValue: "user", required: true
        }
        section("Security Panel") {
            input "securityCode", "password", title: "Security Code", description: "Arm/disarm code", required: true
        }
        section("Zones") {
            href "zonesPage", title: "Configure Zones",
                 description: configuredZoneCount() > 0 ?
                     "${configuredZoneCount()} zone(s) configured" :
                     "Tap to add zones"
        }
        section("Hubitat Safety Monitor") {
            input "enableHSM", "bool", title: "Integrate with Hubitat Safety Monitor", defaultValue: true
        }
        section {
            input "logEnable", "bool", title: "Enable Debug Logging", defaultValue: false
        }
    }
}

def zonesPage() {
    dynamicPage(name: "zonesPage", title: "Zone Configuration") {
        section {
            paragraph "Configure each zone below. Leave Name blank to skip a slot."
            input "numZones", "number", title: "Number of zone slots",
                  defaultValue: 8, required: true, submitOnChange: true
        }
        def num = (settings.numZones ?: 8) as int
        for (int i = 1; i <= num; i++) {
            section("Zone Slot ${i}") {
                input "zone${i}Name", "text",   title: "Name",              required: false
                input "zone${i}Num",  "number", title: "Zone Number (1–64)", required: false
                input "zone${i}Type", "enum",   title: "Type",
                      options: ["Contact", "Motion", "Smoke", "Water", "CO"], required: false
            }
        }
    }
}

// ─── Lifecycle ───────────────────────────────────────────────────────────────

def installed() {
    updated()
}

def uninstalled() {
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def updated() {
    unsubscribe()
    state.lastHSMStatus = null

    def connDev = getOrCreateConnectionDevice()

    // Push settings to the connection device
    connDev.updateSetting("evlAddress",   [value: settings.evlAddress,              type: "text"])
    connDev.updateSetting("evlPort",      [value: settings.evlPort ?: 4025,         type: "number"])
    connDev.updateSetting("evlPassword",  [value: settings.evlPassword,             type: "password"])
    connDev.updateSetting("securityCode", [value: settings.securityCode,            type: "password"])
    connDev.updateSetting("logEnable",    [value: settings.logEnable ?: false,      type: "bool"])

    syncZones(connDev)
    connDev.initialize()

    if (settings.enableHSM) {
        subscribe(location, "hsmStatus", hsmHandler)
    }
}

// ─── Zone sync ───────────────────────────────────────────────────────────────

private syncZones(connDev) {
    def num = (settings.numZones ?: 8) as int
    def zones = []
    for (int i = 1; i <= num; i++) {
        def name = settings["zone${i}Name"]
        def zoneNum = settings["zone${i}Num"]
        def type = settings["zone${i}Type"]
        if (name && zoneNum && type) zones << [num: zoneNum as int, name: name, type: type]
    }

    ifDebug("Syncing ${zones.size()} zone(s) — adding missing, preserving existing")
    // Ensure the partition device exists (addPartition is idempotent)
    connDev.addPartition()
    // Add any zones that don't exist yet (addZone is idempotent)
    zones.each { z -> connDev.addZone(z.num, z.name, z.type) }
    // Note: zones removed from config are NOT auto-deleted; delete them manually
    // in the Hubitat devices page if no longer needed.
}

private int configuredZoneCount() {
    def num = (settings.numZones ?: 8) as int
    int count = 0
    for (int i = 1; i <= num; i++) {
        if (settings["zone${i}Name"] && settings["zone${i}Num"] && settings["zone${i}Type"]) count++
    }
    return count
}

// ─── Connection device management ────────────────────────────────────────────

private getOrCreateConnectionDevice() {
    def dni = "envisalink-${app.id}"
    def dev = getChildDevice(dni)
    if (!dev) {
        ifDebug("Creating Envisalink Connection device (dni=${dni})")
        dev = addChildDevice("bdwilson", "Envisalink Connection", dni,
                             [name: "Envisalink Connection", label: "Envisalink Connection", isComponent: false])
    }
    return dev
}

// ─── HSM integration ─────────────────────────────────────────────────────────

def hsmHandler(evt) {
    if (!settings.enableHSM) return
    // Guard: skip duplicate events and the disarm→disarmed round-trip
    if (state.lastHSMStatus == evt.value) return
    if (state.lastHSMStatus == "disarm" && evt.value == "disarmed") return

    ifDebug("HSM event: ${evt.value} (was: ${state.lastHSMStatus})")
    state.lastHSMStatus = evt.value

    def connDev = getOrCreateConnectionDevice()
    switch (evt.value) {
        case "armedHome":
        case "armedNight": connDev.armStay();  break
        case "armedAway":  connDev.armAway();  break
        case "disarmed":   connDev.disarm();   break
    }
}

// Called by the connection device when the partition state changes
def updatePartitionState(String partState, String alpha) {
    if (!settings.enableHSM) return
    // Suppress feedback during exit delay
    if (partState == "arming" || (alpha?.toLowerCase()?.contains("may exit"))) return

    def newHSM = null
    if (partState in ["armedstay", "armedinstant"])  newHSM = "armHome"
    else if (partState in ["armedaway", "armedmax"]) newHSM = "armAway"
    else if (partState == "ready")                   newHSM = "disarm"

    if (newHSM && newHSM != state.lastHSMStatus) {
        ifDebug("Sending hsmSetArm: ${newHSM} (panel: ${partState})")
        state.lastHSMStatus = newHSM
        sendLocationEvent(name: "hsmSetArm", value: newHSM)
    }
}

private ifDebug(String msg) {
    if (settings.logEnable) log.debug "Envisalink App: ${msg}"
}
