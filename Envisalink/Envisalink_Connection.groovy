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
 *
 *  Version: 2.0.5
 */

import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

// Per-device keystroke queue for ^03 partition keypresses (in memory: replies arrive in separate executions)
@Field static final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> keyQueues = new ConcurrentHashMap<>()
@Field static final ConcurrentHashMap<String, Map> keyInFlight = new ConcurrentHashMap<>()

metadata {
    definition(name: "Envisalink Connection", namespace: "bdwilson", author: "bdwilson",
               importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Envisalink/Envisalink_Connection.groovy") {
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
        input name: "statusPartition", type: "number", title: "Partition for arm/disarm, status and HSM", range: "1..8", defaultValue: 1
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
    if (keyInFlight.containsKey(queueKey())) abortKeys("connection reset")
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
    if (keyInFlight.containsKey(queueKey())) abortKeys("connection lost")
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
            // Cancel any pending connect() that was scheduled by the telnetStatus callback
            // triggered by telnetClose() inside connect(). Without this, connect() creates
            // a self-perpetuating reconnect loop every 10 seconds.
            unschedule("connect")
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

    // Every TPI message ends with a '$' terminator
    if (msg.endsWith('$')) msg = msg[0..-2]

    def parts = msg.split(",")
    def code  = parts[0]

    switch (code) {
        case "%00":
            // Virtual Keypad Update — drives all zone and partition state
            if (parts.length >= 6) handleKeypadUpdate(parts)
            break
        case "%FF":
            // Zone Timer Dump — backup mechanism for zone state
            if (parts.length >= 2) handleZoneTimerDump(parts[1])
            break
        case "^00":
            ifDebug("Poll response — connection alive")
            break
        case "^03":
            // Reply to a partition keystroke we sent
            handleKeyResponse(parts.length > 1 ? parts[1] : "")
            break
        case "^0C":
            // Invalid command — EnvisaLink firmware doesn't understand what we sent
            if (keyInFlight.get(queueKey())?.cmd) abortKeys("EnvisaLink rejected the partition keystroke command (^0C)")
            else ifDebug("EnvisaLink reported an invalid command (^0C)")
            break
        case "%01":
        case "%02":
        case "%03":
            // Zone state change, partition state change, and CID events are intentionally
            // not processed — all state is derived from %00, matching the STNP plugin behaviour
            break
        default:
            ifDebug("Unrecognised TPI code: ${code}")
    }
}

private handleKeypadUpdate(String[] parts) {
    // %00,<partition>,<flagsHex>,<userOrZone>,<beep>,<alphaText>
    def partitionNum = safeInt(parts[1], 1)
    def flagsHex     = parts[2]
    def userOrZone   = parts[3].trim()
    // Alpha text can contain commas, so rejoin everything after the beep field
    def alpha        = parts[5..-1].join(",").trim()

    def flagInt = 0
    try { flagInt = Integer.parseInt(flagsHex, 16) } catch (e) {
        log.warn "EnvisaLink: bad flags hex '${flagsHex}'"
        return
    }

    def flags = parseFlags(flagInt)
    def dscCode    = getDscCode(flags)
    def partState  = getPartitionState(flags, alpha)

    ifDebug("Keypad update: partition=${partitionNum} flags=${flagsHex} zone=${userOrZone} alpha='${alpha}' code=${dscCode} state=${partState}")

    // Only the configured partition drives the Security Panel device and HSM — the same partition
    // keystrokes go to. On multi-partition panels the EnvisaLink alternates %00 updates per
    // partition, which would otherwise flap the arm state.
    if (partitionNum == statusPartition()) {
        def partChild = getChildDevice("${device.id}_P1")
        if (partChild) partChild.partition(partState, alpha)

        // Suppressed briefly after sending a command to avoid the re-arm feedback loop
        // caused by panel state lag after disarm/arm
        if (!state.cmdSentAt || (now() - (state.cmdSentAt as long)) >= 3000) {
            parent?.updatePartitionState(partState, alpha)
        } else {
            ifDebug("Suppressing partition update — cmd sent ${now() - (state.cmdSentAt as long)}ms ago")
        }
    }

    // Zone state machine
    def zoneNum = safeInt(userOrZone, 0)

    if (dscCode == "READY") {
        // This partition is ready → close its open zones; other partitions' zones are untouched
        new HashMap(state.zones).each { zn, zs ->
            if (!inPartition(zn, partitionNum)) return
            state.zoneTimers.remove(zn)
            if (zs != "closed") {
                state.zones[zn] = "closed"
                state.zonePartition?.remove(zn.toString())
                updateZoneChild(zn as int, "closed")
            }
        }
    } else if (dscCode == "" && zoneNum > 0) {
        // A zone is being reported open/faulted
        def key = "${zoneNum}"
        setZonePartition(key, partitionNum)
        if (state.zones[key] != "open") {
            state.zoneTimers[key] = 0
            state.zones[key] = "open"
            updateZoneChild(zoneNum, "open")
        } else {
            // Zone already open; increment its tick counter
            state.zoneTimers[key] = (state.zoneTimers[key] ?: 0) + 1
            if (state.zoneTimers[key] == 2) {
                // Reset so the sweep can fire again on subsequent cycles
                state.zoneTimers[key] = 0
                // Increment timers for other open zones in this partition; close those that
                // have not been reported for 2+ sweeps (i.e. they stopped appearing in %00)
                new HashMap(state.zones).each { zn, zs ->
                    if (zs == "open" && zn != key && inPartition(zn, partitionNum)) {
                        state.zoneTimers[zn] = (state.zoneTimers[zn] ?: 0) + 1
                        if (state.zoneTimers[zn] >= 2) {
                            state.zones[zn] = "closed"
                            state.zoneTimers.remove(zn)
                            state.zonePartition?.remove(zn.toString())
                            updateZoneChild(zn as int, "closed")
                        }
                    }
                }
            }
        }
    } else if (dscCode == "IN_ALARM" && zoneNum > 0) {
        def key = "${zoneNum}"
        setZonePartition(key, partitionNum)
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
    state.cmdSentAt = now()
    sendKeys("${settings.securityCode}2")
}

def armStay() {
    state.cmdSentAt = now()
    sendKeys("${settings.securityCode}3")
}

def armInstant() {
    state.cmdSentAt = now()
    sendKeys("${settings.securityCode}7")
}

def disarm() {
    state.cmdSentAt = now()
    sendKeys("${settings.securityCode}1")
}

def chime() {
    sendKeys("${settings.securityCode}9")
}

def trigger1() {
    // Relay output 17: key on then off
    sendKeys("${settings.securityCode}#717")
    runIn(2, "trigger1Off")
}

def trigger1Off() {
    sendKeys("${settings.securityCode}#817")
}

def trigger2() {
    sendKeys("${settings.securityCode}#718")
    runIn(2, "trigger2Off")
}

def trigger2Off() {
    sendKeys("${settings.securityCode}#818")
}

def bypass(String zones) {
    if (!zones) return
    // Zero-pad each zone number to 2 digits and concatenate
    def zoneStr = zones.tokenize(",").collect { it.trim().padLeft(2, "0") }.join("")
    sendKeys("${settings.securityCode}6${zoneStr}")
}

// ─── Keystroke routing ───────────────────────────────────────────────────────

// Partition 1 uses plain keystrokes (the EnvisaLink's default partition), exactly as before.
// Other partitions use ^03,<partition>,<key>$ one key at a time, each waiting for the
// EnvisaLink's ^03 reply — the same approach as pyenvisalink.
private sendKeys(String keys) {
    int p = statusPartition()
    if (p == 1) {
        sendCommand(keys)
        return
    }
    keyQueues.putIfAbsent(queueKey(), new ConcurrentLinkedQueue<String>())
    def q = keyQueues.get(queueKey())
    keys.each { q.add("^03,${p},${it}\$".toString()) }
    // putIfAbsent claims the sender slot atomically so two commands can't interleave keys
    if (keyInFlight.putIfAbsent(queueKey(), [cmd: null, attempts: 0]) == null) sendNextKey()
}

private sendNextKey() {
    def q = keyQueues.get(queueKey())
    def cmd = q?.poll()
    if (cmd == null) {
        keyInFlight.remove(queueKey())
        unschedule("keyTimeout")
        // Keys queued by a command that arrived as we were finishing would otherwise be stranded
        if (q && !q.isEmpty() && keyInFlight.putIfAbsent(queueKey(), [cmd: null, attempts: 0]) == null) sendNextKey()
        return
    }
    keyInFlight.put(queueKey(), [cmd: cmd, attempts: 1])
    sendCommand(cmd)
    runIn(3, "keyTimeout")
}

private handleKeyResponse(String rc) {
    def inFlight = keyInFlight.get(queueKey())
    if (inFlight?.cmd == null) {
        ifDebug("^03 reply '${rc}' with no keystroke pending")
        return
    }
    switch (rc) {
        case "00":
            sendNextKey()
            break
        case "01":   // receive buffer overrun — EnvisaLink was busy, retry
        case "04":   // receive buffer overflow — retry
            if (inFlight.attempts >= 3) {
                abortKeys("EnvisaLink busy (reply ${rc}) after 3 attempts")
            } else {
                inFlight.attempts = inFlight.attempts + 1
                unschedule("keyTimeout")
                runInMillis(500 * inFlight.attempts, "resendKey")
            }
            break
        default:
            abortKeys("EnvisaLink rejected keystroke (reply ${rc})")
    }
}

def resendKey() {
    def inFlight = keyInFlight.get(queueKey())
    if (inFlight?.cmd == null) return
    sendCommand(inFlight.cmd)
    runIn(3, "keyTimeout")
}

def keyTimeout() {
    if (keyInFlight.get(queueKey())?.cmd) abortKeys("no reply from EnvisaLink within 3 seconds")
}

private abortKeys(String reason) {
    log.error "EnvisaLink: ${reason} — command to partition ${statusPartition()} was not completed"
    keyQueues.get(queueKey())?.clear()
    keyInFlight.remove(queueKey())
    unschedule("keyTimeout")
    unschedule("resendKey")
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

// Interim 2.0.4 app builds from the development branch passed a zone partition; it isn't needed
def addZone(int zoneNum, String zoneName, String zoneType, int partition) {
    addZone(zoneNum, zoneName, zoneType)
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

// A zone belongs to whichever partition's %00 last reported it. Zones with no record (e.g. open
// before upgrading) match every partition, which is the original single-partition behaviour.
private boolean inPartition(zoneKey, int partitionNum) {
    def p = state.zonePartition?.get(zoneKey.toString())
    return p == null || (p as int) == partitionNum
}

private setZonePartition(zoneKey, int partitionNum) {
    if (state.zonePartition == null) state.zonePartition = [:]
    state.zonePartition[zoneKey.toString()] = partitionNum
}

private int statusPartition() {
    return (settings.statusPartition ?: 1) as int
}

private String queueKey() {
    return device.id.toString()
}

private int safeInt(String s, int fallback) {
    try { return s.trim().toInteger() } catch (e) { return fallback }
}

private ifDebug(String msg) {
    if (settings.logEnable) log.debug "EnvisaLink Connection: ${msg}"
}
