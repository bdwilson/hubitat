/**
 * Wyze Robot Vacuum Driver
 *
 * 1.0.0 - Brian Wilson / bubba@bubba.org
 *
 * Child driver for the Wyze Vacuum Connect App. All network calls happen in the
 * parent app (which owns the Wyze session); this driver just relays commands to it
 * and displays the attributes the app populates via sendEvent.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at:
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

metadata {
    definition(
        name: "Wyze Robot Vacuum Driver",
        namespace: "brianwilson-hubitat",
        author: "Brian Wilson",
        importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Wyze-Vacuum/Wyze-Vacuum-Driver.groovy"
    ) {
        capability "Battery"
        capability "Refresh"

        command "start"
        command "pause"
        command "dock"
        command "refresh"
        command "setSuctionLevel", [[name: "level*", type: "ENUM", description: "Suction level", constraints: ["Quiet", "Standard", "Strong"]]]

        attribute "status", "STRING"        // Standby / Cleaning / Returning to charge / Docked / Mapping / Paused / Error
        attribute "mode", "STRING"          // finer-grained device mode text
        attribute "suctionLevel", "STRING"  // Quiet / Standard / Strong
        attribute "charging", "STRING"      // true / false
        attribute "cleanTime", "NUMBER"     // minutes, current/last cleaning run
        attribute "cleanSize", "NUMBER"     // sq ft, current/last cleaning run
        attribute "fault", "STRING"
        attribute "lastRefresh", "STRING"
    }

    preferences {
        input "isDebug", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def installed() {
    refresh()
}

def updated() {
    if (isDebug) runIn(3600, logsOff)
}

def start() {
    ifDebug("start() called")
    sendEvent(name: "status", value: "Cleaning")
    parent.startVacuum(device.deviceNetworkId)
}

def pause() {
    ifDebug("pause() called")
    sendEvent(name: "status", value: "Paused")
    parent.pauseVacuum(device.deviceNetworkId)
}

def dock() {
    ifDebug("dock() called")
    sendEvent(name: "status", value: "Returning to charge")
    parent.dockVacuum(device.deviceNetworkId)
}

def setSuctionLevel(String level) {
    ifDebug("setSuctionLevel(${level}) called")
    parent.setVacuumSuctionLevel(device.deviceNetworkId, level)
}

def refresh() {
    ifDebug("refresh() called")
    parent.refreshVacuum(device.deviceNetworkId)
}

def logsOff() {
    log.warn "Wyze Robot Vacuum Driver: debug logging disabled"
    device.updateSetting("isDebug", [value: "false", type: "bool"])
}

private void ifDebug(String msg) {
    if (isDebug) log.debug "Wyze Vacuum Driver [${device.displayName}]: ${msg}"
}
