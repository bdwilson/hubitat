/**
 *  EnvisaLink TPI Connection Driver for Hubitat
 *
 *  Native TCP/TPI integration for Honeywell Vista panels via EnvisaLink.
 *  Replaces SmartThings Node Proxy dependency.
 *
 *  Original SmartThings Node Proxy envisalink plugin by redloro@gmail.com
 *  Hubitat native port by bdwilson
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.
 */

metadata {
    definition(name: "Envisalink Connection", namespace: "bdwilson", author: "bdwilson") {
        capability "Initialize"

        command "connect"
        command "disconnect"
        command "armAway"
        command "armStay"
        command "armInstant"
        command "disarm"
        command "chime"
        command "trigger1"
        command "trigger2"
        command "bypass", [[name: "zones", type: "STRING", description: "Comma-separated zone numbers"]]

        attribute "connectionStatus", "String"
    }

    preferences {
        input name: "evlAddress",    type: "text",     title: "EnvisaLink IP Address",       required: true
        input name: "evlPort",       type: "number",   title: "EnvisaLink Port",              defaultValue: 4025, required: true
        input name: "evlPassword",   type: "password", title: "EnvisaLink Network Password",  defaultValue: "user", required: true
        input name: "securityCode",  type: "password", title: "Panel Security Code",          required: true
        input name: "logEnable",     type: "bool",     title: "Enable Debug Logging",         defaultValue: false
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    unschedule()
    state.loginState = "disconnected"
    if (state.zones == null) state.zones = [:]
    state.zoneTimers = [:]
    sendEvent(name: "connectionStatus", value: "connecting")
    connect()
}

// ─── Telnet lifecycle ────────────────────────────────────────────────────────

def connect() {
    ifDebug("Connecting to ${settings.evlAddress}:${settings.evlPort}")
    try { telnetClose() } catch (e) { /* ignore */ }
    pauseExecution(2000)
    try {
        telnetConnect([termChars: [13, 10]], settings.evlAddress, settings.evlPort as int, null, null)
    } catch (e) {
        log.error "EnvisaLink connect failed: ${e}"
        sendEvent(name: "connectionStatus", value: "disconnected")
        runIn(30, "connect")
    }
}

def disconnect() {
    unschedule()
    telnetClose()
    state.loginState = "disconnected"
    sendEvent(name: "connectionStatus", value: "disconnected")
}

def telnetStatus(String status) {
    log.warn "EnvisaLink telnet status: ${status}"
    state.loginState = "disconnected"
    sendEvent(name: "connectionStatus", value: "disconnected")
    if (status != "transmit error") {
        runIn(10, "connect")
    }
}

// ─── TPI message parsing ─────────────────────────────────────────────────────

def parse(String msg) {
    msg = msg.trim()
    ifDebug("RX: ${msg}")

    // Login handshake (plain text, not TPI format)
    switch (msg) {
        case "Login:":
            ifDebug("Sending network password")
            sendCommand(settings.evlPassword)
            return
        case "OK":
            log.info "EnvisaLink authenticated"
            state.loginState = "authenticated"
            sendEvent(name: "connectionStatus", value: "connected")
            runEvery5Minutes("healthCheck")
            return
        case "FAILED":
            log.error "EnvisaLink login FAILED — check network password in preferences"
            return
        case "Timed Out!":
            log.warn "EnvisaLink login timed out"
            runIn(10, "connect")
            return
    }

    if (!msg.startsWith("%") && !msg.startsWith("^")) return

    def parts = msg.split(",")
    def code  = parts[0]

    switch (code) {
        case "%00":
            // Virtual Keypad Update — drives all zone and partition state
            if (parts.length >= 7) handleKeypadUpdate(parts)
            break
        case "%FF":
            // Zone Timer Dump — backup mechanism for zone state
            if (parts.length >= 2) handleZoneTimerDump(parts[1])
            break
        case "^00":
            ifDebug("Poll response — connection alive")
            break
        // %01 zone state change and %02 partition state change are intentionally
        // not processed; all state is derived from %00 as in the STNP plugin
        default:
            ifDebug("Unhandled TPI code: ${code}")
    }
}

private handleKeypadUpdate(String[] parts) {
    // %00,<partition>,<flagsHex>,<userOrZone>,<beep>,<alphaText>,<checksum>
    def partitionNum = safeInt(parts[1], 1)
    def flagsHex     = parts[2]
    def userOrZone   = parts[3].trim()
    def alpha        = (parts.length > 5) ? parts[5].trim() : ""
    // parts[-1] is checksum — ignored

    def flagInt = 0
    try { flagInt = Integer.parseInt(flagsHex, 16) } catch (e) {
        log.warn "EnvisaLink: bad flags hex '${flagsHex}'"
        return
    }

    def flags = parseFlags(flagInt)
    def dscCode    = getDscCode(flags)
    def partState  = getPartitionState(flags, alpha)

    ifDebug("Keypad update: partition=${partitionNum} flags=${flagsHex} zone=${userOrZone} alpha='${alpha}' code=${dscCode} state=${partState}")

    // Update partition child device
    def partChild = getChildDevice("${device.id}_P1")
    if (partChild) partChild.partition(partState, alpha)

    // Notify parent app for HSM sync
    parent?.updatePartitionState(partState, alpha)

    // Zone state machine
    def zoneNum = safeInt(userOrZone, 0)

    if (dscCode == "READY") {
        // Panel is ready → close all known open zones
        state.zoneTimers = [:]
        new HashMap(state.zones).each { zn, zs ->
            if (zs != "closed") {
                state.zones[zn] = "closed"
                updateZoneChild(zn as int, "closed")
            }
        }
    } else if (dscCode == "" && zoneNum > 0) {
        // A zone is being reported open
        def key = "${zoneNum}"
        if (state.zones[key] != "open") {
            state.zoneTimers[key] = 0
            state.zones[key] = "open"
            updateZoneChild(zoneNum, "open")
        } else {
            // Zone already open; increment tick and sweep orphans after 2 ticks
            state.zoneTimers[key] = (state.zoneTimers[key] ?: 0) + 1
            if (state.zoneTimers[key] >= 2) {
                state.zoneTimers[key] = 0
                // Close any open zone that hasn't been the active zone recently
                new HashMap(state.zones).each { zn, zs ->
                    if (zs == "open" && zn != key && (state.zoneTimers[zn] ?: 0) >= 2) {
                        state.zones[zn] = "closed"
                        updateZoneChild(zn as int, "closed")
                    }
                }
            }
        }
    } else if (dscCode == "IN_ALARM" && zoneNum > 0) {
        def key = "${zoneNum}"
        if (state.zones[key] != "alarm") {
            state.zones[key] = "alarm"
            updateZoneChild(zoneNum, "alarm")
        }
    }
}

private handleZoneTimerDump(String data) {
    // 256-char hex string: 64 zones × 4-char little-endian 16-bit timer
    // Timer is in 5-second ticks; < 30 s → open, ≥ 30 s → closed
    if (data.length() < 256) return
    for (int i = 0; i < 256; i += 4) {
        def zoneNum = (i / 4) + 1
        // Little-endian byte swap: bytes at i+2,i+3 are high byte; i,i+1 are low byte
        def timerHex  = data[i + 2..i + 3] + data[i..i + 1]
        def timerSecs = (Integer.parseInt("FFFF", 16) - Integer.parseInt(timerHex, 16)) * 5
        def zoneState = (timerSecs < 30) ? "open" : "closed"
        def key = "${zoneNum}"
        if (state.zones.containsKey(key) && state.zones[key] != zoneState) {
            ifDebug("Zone timer dump: zone ${zoneNum} → ${zoneState} (${timerSecs}s)")
            state.zones[key] = zoneState
            updateZoneChild(zoneNum, zoneState)
        }
    }
}

// ─── Flag and state helpers ──────────────────────────────────────────────────

private Map parseFlags(int flagInt) {
    [
        alarm:                  (flagInt & 0x0001) != 0,
        alarm_in_memory:        (flagInt & 0x0002) != 0,
        armed_away:             (flagInt & 0x0004) != 0,
        ac_present:             (flagInt & 0x0008) != 0,
        bypass:                 (flagInt & 0x0010) != 0,
        chime:                  (flagInt & 0x0020) != 0,
        armed_zero_entry_delay: (flagInt & 0x0080) != 0,
        alarm_fire_zone:        (flagInt & 0x0100) != 0,
        system_trouble:         (flagInt & 0x0200) != 0,
        ready:                  (flagInt & 0x1000) != 0,
        fire:                   (flagInt & 0x2000) != 0,
        low_battery:            (flagInt & 0x4000) != 0,
        armed_stay:             (flagInt & 0x8000) != 0
    ]
}

private String getDscCode(Map flags) {
    if (flags.alarm || flags.alarm_fire_zone || flags.fire) return "IN_ALARM"
    if (flags.ready && !flags.armed_away && !flags.armed_stay)  return "READY"
    if (flags.armed_away || flags.armed_stay)                   return "ARMED"
    return ""
}

private String getPartitionState(Map flags, String alpha) {
    if (flags.alarm || flags.alarm_fire_zone || flags.fire)            return "alarm"
    if (flags.alarm_in_memory)                                         return "alarmcleared"
    if (alpha.toLowerCase().contains("may exit"))                      return "arming"
    if (flags.armed_stay  && flags.armed_zero_entry_delay)             return "armedinstant"
    if (flags.armed_away  && flags.armed_zero_entry_delay)             return "armedmax"
    if (flags.armed_stay)                                              return "armedstay"
    if (flags.armed_away)                                              return "armedaway"
    if (flags.ready)                                                   return "ready"
    return "notready"
}

// ─── Command methods ─────────────────────────────────────────────────────────

private sendCommand(String cmd) {
    ifDebug("TX: ${cmd}")
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.TELNET))
}

def armAway() {
    sendCommand("${settings.securityCode}2")
}

def armStay() {
    sendCommand("${settings.securityCode}3")
}

def armInstant() {
    sendCommand("${settings.securityCode}7")
}

def disarm() {
    sendCommand("${settings.securityCode}1")
    // Send twice for Vista panel reliability
    runIn(1, "disarmAgain")
}

def disarmAgain() {
    sendCommand("${settings.securityCode}1")
}

def chime() {
    sendCommand("${settings.securityCode}9")
}

def trigger1() {
    // Relay output 17: key on then off
    sendCommand("${settings.securityCode}#717")
    runIn(2, "trigger1Off")
}

def trigger1Off() {
    sendCommand("${settings.securityCode}#817")
}

def trigger2() {
    sendCommand("${settings.securityCode}#718")
    runIn(2, "trigger2Off")
}

def trigger2Off() {
    sendCommand("${settings.securityCode}#818")
}

def bypass(String zones) {
    if (!zones) return
    // Zero-pad each zone number to 2 digits and concatenate
    def zoneStr = zones.tokenize(",").collect { it.trim().padLeft(2, "0") }.join("")
    sendCommand("${settings.securityCode}6${zoneStr}")
}

def healthCheck() {
    if (state.loginState != "authenticated") {
        log.warn "EnvisaLink health check: not authenticated, reconnecting"
        connect()
    }
}

// ─── Child device management (called from app) ───────────────────────────────

def addZone(int zoneNum, String zoneName, String zoneType) {
    def dni = "${device.id}_Z${zoneNum}"
    if (getChildDevice(dni)) {
        ifDebug("Zone ${zoneNum} device already exists (${dni})")
        return
    }
    def driverName = "Envisalink Zone ${zoneType}"
    try {
        addChildDevice("bdwilson", driverName, dni,
                       [name: zoneName, label: zoneName, isComponent: false])
        state.zones["${zoneNum}"] = "closed"
        ifDebug("Created zone device: ${zoneName} (zone ${zoneNum}) dni=${dni}")
    } catch (e) {
        log.error "Failed to create zone device ${zoneNum} '${zoneName}': ${e}"
    }
}

def addPartition() {
    def dni = "${device.id}_P1"
    if (getChildDevice(dni)) {
        ifDebug("Partition device already exists")
        return
    }
    try {
        addChildDevice("bdwilson", "Envisalink Partition", dni,
                       [name: "Security Panel", label: "Security Panel", isComponent: false])
        ifDebug("Created partition device dni=${dni}")
    } catch (e) {
        log.error "Failed to create partition device: ${e}"
    }
}

def removeAllChildren() {
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
    state.zones = [:]
    state.zoneTimers = [:]
}

// ─── Utilities ───────────────────────────────────────────────────────────────

private updateZoneChild(int zoneNum, String zoneState) {
    def child = getChildDevice("${device.id}_Z${zoneNum}")
    if (child) {
        ifDebug("Zone ${zoneNum} → ${zoneState}")
        child.zone(zoneState)
    }
}

private int safeInt(String s, int fallback) {
    try { return s.trim().toInteger() } catch (e) { return fallback }
}

private ifDebug(String msg) {
    if (settings.logEnable) log.debug "EnvisaLink Connection: ${msg}"
}
