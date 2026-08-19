/**
 * Wyze Robot Vacuum Driver
 *
 * 1.7.0 - Brian Wilson / bubba@bubba.org
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
        // TODO: point back at master once this branch is merged
        importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/claude/hubitat-wyze-vacuum-integration-x2euom/Wyze-Vacuum/Wyze-Vacuum-Driver.groovy"
    ) {
        capability "Battery"
        capability "Refresh"
        capability "Switch"

        command "start"
        command "pause"
        command "dock"
        command "refresh"
        command "setSuctionLevel", [[name: "level*", type: "ENUM", description: "Suction level", constraints: ["Quiet", "Standard", "Strong"]]]
        command "cleanRooms", [[name: "roomNames*", type: "STRING", description: "Comma-separated room names to clean now, e.g. 'Kitchen, Living Room'"]]
        command "cleanNextRooms"
        command "markRoomsCleaned", [[name: "roomNames*", type: "STRING", description: "Comma-separated room names to mark as cleaned right now WITHOUT actually cleaning -- corrects rotation history, e.g. 'Kitchen, Living Room'"]]
        command "resetBinTimer"
        command "learnRoomTimes"
        command "cancelLearning"

        // Fixed, no-argument, one-tap Dashboard-tile-friendly commands. Map each
        // slot to a room in the app's "Room Buttons" section, then add one
        // Dashboard tile per slot (same device, different command) for a
        // one-tap "clean this room" button with no typing/picker and no
        // extra child devices.
        command "cleanRoomSlot1"
        command "cleanRoomSlot2"
        command "cleanRoomSlot3"
        command "cleanRoomSlot4"
        command "cleanRoomSlot5"
        command "cleanRoomSlot6"
        command "cleanRoomSlot7"
        command "cleanRoomSlot8"

        attribute "status", "STRING"        // Standby / Cleaning / Returning to charge / Docked / Mapping / Paused / Error
        attribute "workStatusCode", "NUMBER" // raw numeric code behind "status" -- unverified label mapping, useful for cross-checking
        attribute "mode", "STRING"          // finer-grained device mode text
        attribute "suctionLevel", "STRING"  // Quiet / Standard / Strong
        attribute "charging", "STRING"      // true / false
        attribute "cleanTime", "NUMBER"     // minutes, current/last cleaning run
        attribute "cleanSize", "NUMBER"     // sq ft, current/last cleaning run
        attribute "fault", "STRING"
        attribute "lastCleanedRooms", "STRING"       // rooms confirmed cleaned by the most recent room-clean run
        attribute "lastRunCompleteness", "NUMBER"    // rough % of the last dispatched batch's expected time that actually elapsed
        attribute "nextRoomsToClean", "STRING"       // what cleanNextRooms() would pick right now, per current rotation config
        attribute "roomsPendingThisCycle", "NUMBER"  // rotation rooms not cleaned within the configured cycle window
        attribute "hoursSinceEmptied", "NUMBER"      // cumulative cleaning hours since the bin was last reset
        attribute "learningStatus", "STRING"         // Idle / Learning <room> (N more queued) / Stopped early
        attribute "lastRefresh", "STRING"
    }

    preferences {
        input "switchOnAction", "enum", title: "Switch \"on\" action",
            options: ["cleanNextRooms": "Clean Next Rooms (rotation)", "start": "Start (whole house)"],
            defaultValue: "cleanNextRooms", required: true
        input "switchOffAction", "enum", title: "Switch \"off\" action",
            options: ["dock": "Dock", "pause": "Pause"],
            defaultValue: "dock", required: true
        input "isDebug", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def installed() {
    refresh()
}

def updated() {
    if (isDebug) runIn(3600, logsOff)
}

// Switch capability -- lets the vacuum be turned on/off like any other
// switch (Alexa/Google routines, Rule Machine switch triggers, a plain
// switch Dashboard tile) without a separate virtual device. Which real
// action "on"/"off" perform is configurable above.
def on() {
    def action = switchOnAction ?: "cleanNextRooms"
    ifDebug("on() called -> ${action}")
    sendEvent(name: "switch", value: "on")
    if (action == "start") start() else cleanNextRooms()
}

def off() {
    def action = switchOffAction ?: "dock"
    ifDebug("off() called -> ${action}")
    sendEvent(name: "switch", value: "off")
    if (action == "pause") pause() else dock()
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

def cleanRooms(String roomNames) {
    ifDebug("cleanRooms(${roomNames}) called")
    sendEvent(name: "status", value: "Cleaning")
    parent.cleanSpecificRooms(device.deviceNetworkId, roomNames)
}

def cleanNextRooms() {
    ifDebug("cleanNextRooms() called")
    sendEvent(name: "status", value: "Cleaning")
    parent.cleanNextRooms(device.deviceNetworkId)
}

def markRoomsCleaned(String roomNames) {
    ifDebug("markRoomsCleaned(${roomNames}) called")
    parent.markRoomsCleanedByName(device.deviceNetworkId, roomNames)
}

def refresh() {
    ifDebug("refresh() called")
    parent.refreshVacuum(device.deviceNetworkId)
}

def resetBinTimer() {
    ifDebug("resetBinTimer() called")
    parent.resetBinTimer(device.deviceNetworkId)
}

def learnRoomTimes() {
    ifDebug("learnRoomTimes() called")
    sendEvent(name: "status", value: "Cleaning")
    parent.startLearningMode(device.deviceNetworkId)
}

def cancelLearning() {
    ifDebug("cancelLearning() called")
    parent.cancelLearningMode(device.deviceNetworkId)
}

def cleanRoomSlot1() { cleanRoomSlot(1) }
def cleanRoomSlot2() { cleanRoomSlot(2) }
def cleanRoomSlot3() { cleanRoomSlot(3) }
def cleanRoomSlot4() { cleanRoomSlot(4) }
def cleanRoomSlot5() { cleanRoomSlot(5) }
def cleanRoomSlot6() { cleanRoomSlot(6) }
def cleanRoomSlot7() { cleanRoomSlot(7) }
def cleanRoomSlot8() { cleanRoomSlot(8) }

private void cleanRoomSlot(int slot) {
    ifDebug("cleanRoomSlot(${slot}) called")
    sendEvent(name: "status", value: "Cleaning")
    parent.cleanRoomSlot(device.deviceNetworkId, slot)
}

def logsOff() {
    log.warn "Wyze Robot Vacuum Driver: debug logging disabled"
    device.updateSetting("isDebug", [value: "false", type: "bool"])
}

private void ifDebug(String msg) {
    if (isDebug) log.debug "Wyze Vacuum Driver [${device.displayName}]: ${msg}"
}
