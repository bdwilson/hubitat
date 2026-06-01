/**
 * Genmon Generator Monitor — Hubitat Driver
 * 
 *   - Converted by Brian Wilson based on https://github.com/jgyates/genmon-ha using Claude Code
 *   - v2.0 - 31MAY26 - Initial Release
 *
 * Communicates with the genhalink REST API (same backend as the Home
 * Assistant integration).  Maintains a persistent WebSocket connection
 * for real-time push updates; falls back to HTTP polling when the
 * WebSocket is unavailable.
 *
 * API endpoints:
 *   GET  /api/health   — unauthenticated connectivity probe
 *   GET  /api/info     — controller type, model, serial, firmware
 *   GET  /api/status   — full state snapshot
 *   POST /api/command  — send command  { "command": "<cmd>" }
 *   WS   /ws           — push updates (auth via first JSON message)
 *
 * WebSocket message flow:
 *   → send  {"type": "auth", "token": "<api_key>"}
 *   ← recv  {"type": "auth_ok"}
 *   ← recv  {"type": "state_update"|"full_state", "state": {...}}
 *
 * Requirements:
 *   - Genmon v2.0 or later
 *   - "Native Home Assistant integration via REST/WebSocket API" plugin enabled
 *     in Genmon (this provides the REST API and WebSocket used by this driver)
 *   - HTTPS/WSS is enabled by default here, matching the genmon Home Assistant
 *     plugin default.  Disable only if your genhalink instance is configured
 *     for plain HTTP.
 */

metadata {
    definition(
        name:      "Genmon Generator Monitor",
        namespace: "brianwilson-hubitat",
	author: "Brian Wilson",
	importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Genmon/genmon-driver.groovy"
    ) {
        capability "Refresh"
        capability "Sensor"
        capability "PowerMeter"          // power (W)
        capability "VoltageMeasurement"  // voltage (V)
        capability "Switch"              // on = running, off = stopped
        capability "Initialize"          // tells Hubitat to call initialize() on hub start

        // ── Engine / status ──────────────────────────────────────────────
        attribute "generatorStatus",      "string"
        attribute "engineState",          "string"
        attribute "outputVoltage",        "number"
        attribute "outputFrequency",      "number"
        attribute "outputCurrent",        "number"
        attribute "outputPower",          "number"   // kW
        attribute "batteryVoltage",       "number"
        attribute "engineRPM",            "number"
        attribute "coolantTemperature",   "number"
        attribute "ambientTemperature",   "number"

        // ── Fuel ─────────────────────────────────────────────────────────
        attribute "fuelLevel",            "number"
        attribute "fuelType",             "string"

        // ── Runtime ──────────────────────────────────────────────────────
        attribute "runHours",             "number"

        // ── Alarms ───────────────────────────────────────────────────────
        attribute "alarmActive",          "string"   // "true" / "false"
        attribute "alarmDescription",     "string"

        // ── Transfer switch ───────────────────────────────────────────────
        attribute "transferSwitchState",  "string"   // "Utility" / "Generator"
        attribute "utilityVoltage",       "number"

        // ── Exercise / maintenance ────────────────────────────────────────
        attribute "exerciseSchedule",     "string"
        attribute "lastExercise",         "string"

        // ── Controller info ───────────────────────────────────────────────
        attribute "controllerType",       "string"
        attribute "generatorModel",       "string"
        attribute "serialNumber",         "string"
        attribute "firmwareVersion",      "string"

        // ── Quiet mode ────────────────────────────────────────────────────
        attribute "quietMode",            "string"   // "On" / "Off"

        // ── Connectivity ──────────────────────────────────────────────────
        attribute "connectionStatus",     "string"   // "connected (ws)" | "connected (poll)" | "error: ..."
        attribute "lastUpdate",           "string"

        // ── Custom commands ───────────────────────────────────────────────
        command "startGenerator"
        command "stopGenerator"
        command "autoMode"
        command "manualMode"
        command "muteAlarm"
        command "resetAlarm"
        command "startExercise"
        command "setQuietModeOn"
        command "setQuietModeOff"
        command "disconnect"
        command "sendCustomCommand", [[name: "command*", type: "STRING", description: "Raw genmon command string"]]
    }

    preferences {
        input name: "host",         type: "text",     title: "Genmon IP Address (must be a dotted-decimal IP, e.g. 192.168.1.100)", required: true
        input name: "port",         type: "number",   title: "Port",               defaultValue: 9083
        input name: "apiKey",       type: "password", title: "API Key — requires Genmon v2.0 or later with the 'Native Home Assistant integration via REST/WebSocket API' plugin enabled", required: true
        input name: "useSSL",       type: "bool",     title: "Use HTTPS / WSS (default: on — matches the genmon Home Assistant plugin default)", defaultValue: true
        input name: "pollInterval", type: "enum",     title: "Fallback Poll Interval (when WebSocket is unavailable)",
                                    options: ["5": "5 sec", "10": "10 sec", "30": "30 sec",
                                              "60": "1 min", "300": "5 min"],
                                    defaultValue: "30"
        input name: "logEnable",    type: "bool",     title: "Enable debug logging", defaultValue: false
    }
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────

def installed() {
    log.info "Genmon driver: installed"
    initialize()
}

def updated() {
    log.info "Genmon driver: preferences updated"
    initialize()
}

def initialize() {
    unschedule()
    state.wsConnected   = false
    state.wsAuthPending = false

    updateDNI()

    // Probe HTTP health first.  If the server isn't reachable yet, schedule a
    // retry rather than immediately firing off WebSocket (which would just
    // produce a redundant "unexpected end of stream" error on top of the HTTP
    // failure).
    if (checkHealth()) {
        fetchInfo()
        refresh()
        connectWebSocket()
    } else {
        sendEvent(name: "connectionStatus", value: "unreachable — retrying in 30s")
        runIn(30, "initialize")
    }
}

private boolean checkHealth() {
    def params = buildParams("/api/health")
    // Health endpoint is unauthenticated — drop the auth header
    params.headers = [:]
    try {
        def ok = false
        httpGet(params) { resp -> ok = (resp.status == 200) }
        return ok
    } catch (Exception e) {
        log.warn "Genmon: health check failed — ${e.message} (is genhalink running on ${host}:${port}?)"
        return false
    }
}

private void updateDNI() {
    if (!host) return

    // Hubitat's sandbox does not allow InetAddress.getByName(), so hostname
    // resolution is not available.  The host preference must be a dotted-decimal
    // IP address (e.g. 192.168.1.100).  This is normal for a local LAN device.
    if (!(host ==~ /\d+\.\d+\.\d+\.\d+/)) {
        log.warn "Genmon driver: 'Host' must be an IP address, not a hostname. DNI not set."
        return
    }

    try {
        def p   = (port ?: 9083).toInteger()
        def dni = ipPortToHex(host, p)
        if (device.deviceNetworkId != dni) {
            device.deviceNetworkId = dni
            log.info "Genmon driver: DNI set to ${dni} (${host}:${p})"
        }
    } catch (Exception e) {
        log.warn "Genmon driver: could not set DNI — ${e.message}"
    }
}

private String ipPortToHex(String ip, int port) {
    // Hubitat LAN DNI format: uppercase hex IP (8 chars) + ":" + uppercase hex port (4 chars)
    // e.g. 192.168.1.100:9083 → C0A80164:237B
    def octets = ip.tokenize(".").collect { Integer.parseInt(it) }
    def hexIP   = octets.collect { String.format("%02X", it) }.join("")
    def hexPort = String.format("%04X", port)
    return "${hexIP}:${hexPort}"
}

def disconnect() {
    unschedule()
    interfaces.webSocket.close()
    state.wsConnected = false
    sendEvent(name: "connectionStatus", value: "disconnected")
    log.info "Genmon driver: disconnected"
}

// ── WebSocket ─────────────────────────────────────────────────────────────────

private void connectWebSocket() {
    def scheme = useSSL ? "wss" : "ws"
    def p      = (port ?: 9083).toInteger()
    def wsUrl  = "${scheme}://${host}:${p}/ws"

    if (logEnable) log.debug "Genmon WS: connecting to ${wsUrl}"
    state.wsAuthPending = true

    try {
        interfaces.webSocket.connect(wsUrl, headers: [:], ignoreSSLIssues: useSSL)
    } catch (Exception e) {
        log.warn "Genmon WS: connect failed — ${e.message}. Falling back to polling."
        startPolling()
    }
}

// Called by Hubitat when WebSocket connection status changes
def webSocketStatus(String statusMsg) {
    if (logEnable) log.debug "Genmon WS status: ${statusMsg}"

    if (statusMsg.startsWith("status: open")) {
        // Connection established — authenticate
        def authMsg = groovy.json.JsonOutput.toJson([type: "auth", token: apiKey])
        interfaces.webSocket.sendMessage(authMsg)

    } else if (statusMsg.startsWith("failure:") || statusMsg.startsWith("status: closing")
               || statusMsg == "status: closed") {
        state.wsConnected   = false
        state.wsAuthPending = false
        log.warn "Genmon WS: disconnected (${statusMsg}). Falling back to polling, will retry WS in 30s."
        sendEvent(name: "connectionStatus", value: "connected (poll)")
        startPolling()
        runIn(30, "reconnectWebSocket")
    }
}

def reconnectWebSocket() {
    if (state.wsConnected) return  // already reconnected
    if (logEnable) log.debug "Genmon WS: attempting reconnect"
    updateDNI()          // refresh in case IP changed (DHCP)
    if (checkHealth()) {
        connectWebSocket()
    } else {
        log.warn "Genmon WS: server still unreachable, will retry in 30s"
        runIn(30, "reconnectWebSocket")
    }
}

// Called by Hubitat for every incoming WebSocket text frame
def parse(String rawMsg) {
    if (logEnable) log.debug "Genmon WS recv: ${rawMsg}"

    def msg
    try { msg = new groovy.json.JsonSlurper().parseText(rawMsg) }
    catch (Exception e) { log.warn "Genmon WS: failed to parse message — ${e.message}"; return }

    def msgType = msg?.type

    if (msgType == "auth_ok") {
        state.wsConnected   = true
        state.wsAuthPending = false
        log.info "Genmon WS: authenticated — push updates active"
        unschedule("pollAndReschedule")  // stop polling now that WS is live
        unschedule("refresh")
        sendEvent(name: "connectionStatus", value: "connected (ws)")

    } else if (msgType == "auth_invalid" || (state.wsAuthPending && msgType != "auth_ok")) {
        log.error "Genmon WS: authentication failed — check API key"
        interfaces.webSocket.close()
        state.wsAuthPending = false
        sendEvent(name: "connectionStatus", value: "error: WS auth failed")
        startPolling()  // fall back to polling

    } else if (msgType in ["state_update", "full_state"]) {
        def stateData = msg?.state
        if (stateData) {
            parseStatus(stateData)
            sendEvent(name: "lastUpdate",       value: new Date().toString())
            sendEvent(name: "connectionStatus", value: "connected (ws)")
        }

    } else if (msgType == "ping") {
        // Some genhalink versions send heartbeat pings — pong back
        interfaces.webSocket.sendMessage(groovy.json.JsonOutput.toJson([type: "pong"]))

    } else {
        if (logEnable) log.debug "Genmon WS: unhandled message type '${msgType}'"
    }
}

// ── HTTP polling (fallback) ───────────────────────────────────────────────────

private void startPolling() {
    unschedule("pollAndReschedule")
    unschedule("refresh")

    def secs = (pollInterval ?: "30").toInteger()
    if (secs < 5) secs = 5

    if (secs < 60) {
        runIn(secs, "pollAndReschedule")
    } else if (secs == 60) {
        runEvery1Minute("refresh")
    } else if (secs == 300) {
        runEvery5Minutes("refresh")
    } else {
        schedule("0/${secs} * * ? * *", "refresh")
    }
}

def pollAndReschedule() {
    if (state.wsConnected) return  // WS is live — stop polling
    refresh()
    def secs = (pollInterval ?: "30").toInteger()
    runIn(secs, "pollAndReschedule")
}

def refresh() {
    if (logEnable) log.debug "Genmon: polling /api/status"
    def params = buildParams("/api/status")
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                def body      = resp.data
                def stateData = body?.state ?: body
                parseStatus(stateData)
                sendEvent(name: "lastUpdate", value: new Date().toString())
                if (!state.wsConnected) {
                    sendEvent(name: "connectionStatus", value: "connected (poll)")
                }
            } else {
                log.warn "Genmon: /api/status returned HTTP ${resp.status}"
                sendEvent(name: "connectionStatus", value: "error: HTTP ${resp.status}")
            }
        }
    } catch (Exception e) {
        log.error "Genmon: refresh failed — ${e.message}"
        sendEvent(name: "connectionStatus", value: "error: ${e.message}")
    }
}

private void fetchInfo() {
    def params = buildParams("/api/info")
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                def d = resp.data
                safeEvent("controllerType",  d?.controller ?: d?.Controller)
                safeEvent("generatorModel",  d?.model      ?: d?.Model)
                safeEvent("serialNumber",    d?.serial     ?: d?.SerialNumber)
                safeEvent("firmwareVersion", d?.firmware   ?: d?.FirmwareVersion)
            }
        }
    } catch (Exception e) {
        if (logEnable) log.debug "Genmon: fetchInfo failed — ${e.message}"
    }
}

// ── Status parsing ────────────────────────────────────────────────────────────

private void parseStatus(Map data) {
    if (!data) return

    def status = deepGet(data, "Status")
    def engine = deepGet(status, "Engine")
    def output = deepGet(status, "Line") ?: deepGet(status, "Output")
    def maint  = deepGet(data, "Maintenance")

    // Generator / engine state
    def genStatus = extractString(deepGet(engine, "Generator Status") ?: deepGet(status, "Generator Status"))
    if (genStatus) {
        sendEvent(name: "generatorStatus", value: genStatus)
        def running = genStatus?.toLowerCase()?.contains("running")
        sendEvent(name: "switch", value: running ? "on" : "off")
    }

    def engineState = extractString(deepGet(engine, "Engine State") ?: deepGet(engine, "Status"))
    if (engineState) sendEvent(name: "engineState", value: engineState)

    // Electrical output
    safeNumericEvent("outputVoltage",   deepGet(engine, "Output Voltage")    ?: deepGet(output, "Output Voltage"),   "V")
    safeNumericEvent("outputFrequency", deepGet(engine, "Output Frequency")  ?: deepGet(output, "Output Frequency"), "Hz")
    safeNumericEvent("outputCurrent",   deepGet(engine, "Output Current")    ?: deepGet(output, "Output Current"),   "A")

    def powerRaw = deepGet(engine, "Output Power (Single Phase)") ?:
                   deepGet(engine, "Output Power") ?:
                   deepGet(output, "Output Power")
    if (powerRaw != null) {
        def (num, unit) = extractNumeric(powerRaw)
        if (num != null) {
            def kw = (unit == "W") ? num / 1000.0 : num
            sendEvent(name: "outputPower", value: kw.round(3), unit: "kW")
            sendEvent(name: "power",       value: (kw * 1000).round(0), unit: "W")
        }
    }

    // Battery / engine internals
    safeNumericEvent("batteryVoltage",   deepGet(engine, "Battery Voltage"), "V")
    safeNumericEvent("engineRPM",        deepGet(engine, "RPM"),             "RPM")

    def coolantRaw = deepGet(engine, "Coolant Temp") ?: deepGet(engine, "Engine Temperature")
    def ambientRaw = deepGet(engine, "Ambient Temp")
    if (coolantRaw != null) safeNumericEvent("coolantTemperature", coolantRaw, "°F")
    if (ambientRaw != null) safeNumericEvent("ambientTemperature", ambientRaw, "°F")

    // Utility / transfer
    safeNumericEvent("utilityVoltage", deepGet(deepGet(status, "Utility"), "Utility Voltage") ?:
                                       deepGet(status, "Utility Voltage"), "V")
    def xferState = extractString(deepGet(status, "Transfer Switch State") ?: deepGet(status, "Transfer Switch"))
    if (xferState) sendEvent(name: "transferSwitchState", value: xferState)

    // Fuel
    safeNumericEvent("fuelLevel", deepGet(engine, "Fuel Level") ?: deepGet(status, "Fuel Level"), "%")

    // Alarms
    def alarmDesc = extractString(deepGet(engine, "System In Alarm") ?: deepGet(status, "System In Alarm"))
    def hasAlarm  = alarmDesc && alarmDesc.trim() && alarmDesc.trim() != "No Active Alarms"
    sendEvent(name: "alarmActive",      value: hasAlarm.toString())
    sendEvent(name: "alarmDescription", value: hasAlarm ? alarmDesc : "No Active Alarms")

    // Run hours
    def runHoursRaw = deepGet(maint, "Run Hours") ?: deepGet(engine, "Run Hours")
    if (runHoursRaw != null) {
        def (num, _) = extractNumeric(runHoursRaw)
        if (num != null) sendEvent(name: "runHours", value: num, unit: "h")
    }

    // Exercise schedule
    def exercise = deepGet(maint, "Exercise")
    def exTime   = extractString(deepGet(exercise, "Exercise Time"))
    if (exTime) sendEvent(name: "exerciseSchedule", value: exTime)
    def lastEx   = extractString(deepGet(exercise, "Last Exercise Time"))
    if (lastEx)  sendEvent(name: "lastExercise", value: lastEx)

    // Quiet mode
    def quietRaw = deepGet(maint, "Quiet Mode") ?: deepGet(data, "Quiet Mode")
    if (quietRaw != null) {
        def quietStr = extractString(quietRaw)
        if (quietStr) sendEvent(name: "quietMode", value: quietStr)
    }
}

// ── Switch capability ─────────────────────────────────────────────────────────

def on()  { startGenerator() }
def off() { stopGenerator()  }

// ── Custom commands ───────────────────────────────────────────────────────────

def startGenerator()  { sendGenmonCommand("start_now") }
def stopGenerator()   { sendGenmonCommand("stop_now") }
def autoMode()        { sendGenmonCommand("auto") }
def manualMode()      { sendGenmonCommand("manual") }
def muteAlarm()       { sendGenmonCommand("mute") }
def resetAlarm()      { sendGenmonCommand("faultreset") }
def startExercise()   { sendGenmonCommand("startexercise") }
def setQuietModeOn()  { sendGenmonCommand("setquiet=on") }
def setQuietModeOff() { sendGenmonCommand("setquiet=off") }

def sendCustomCommand(String command) {
    if (!command) { log.warn "Genmon: sendCustomCommand called with empty command"; return }
    sendGenmonCommand(command)
}

private void sendGenmonCommand(String command) {
    log.info "Genmon: sending command '${command}'"
    def params      = buildParams("/api/command")
    params.body     = groovy.json.JsonOutput.toJson([command: command])
    params.contentType = "application/json"

    try {
        httpPost(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "Genmon: command '${command}' accepted"
                // Brief pause then refresh — WS push will usually arrive faster
                runIn(2, "refresh")
            } else {
                log.warn "Genmon: command '${command}' returned HTTP ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Genmon: command '${command}' failed — ${e.message}"
    }
}

// ── HTTP helpers ──────────────────────────────────────────────────────────────

private Map buildParams(String path) {
    def scheme = useSSL ? "https" : "http"
    def p      = (port ?: 9083).toInteger()
    return [
        uri:                "${scheme}://${host}:${p}${path}",
        headers:            ["Authorization": "Bearer ${apiKey}"],
        requestContentType: "application/json",
        ignoreSSLIssues:    (useSSL as boolean),
        timeout:            10,
    ]
}

// ── Value extraction helpers ──────────────────────────────────────────────────

private Object deepGet(Object data, String key) {
    if (data instanceof Map) return data?.get(key)
    return null
}

private String extractString(Object raw) {
    if (raw == null) return null
    if (raw instanceof Map && raw.containsKey("value")) {
        def v = raw.value
        def u = raw.unit ?: ""
        return u ? "${v} ${u}" : v?.toString()
    }
    return raw?.toString()?.trim() ?: null
}

private List extractNumeric(Object raw) {
    if (raw == null) return [null, null]
    if (raw instanceof Number) return [raw.toDouble(), null]

    if (raw instanceof Map && raw.containsKey("value")) {
        def v = raw.value
        def u = raw.unit ?: null
        try { return [v.toDouble(), u] } catch (e) { return [null, u] }
    }

    def s = raw.toString().trim()
    def unitSuffixes = [
        " kW": "kW", " W": "W", " V": "V", " A": "A",
        " Hz": "Hz", " RPM": "RPM", " °C": "°C", " °F": "°F",
        " C": "°C", " F": "°F", " %": "%", " gal": "gal", " hours": "h",
    ]
    for (entry in unitSuffixes) {
        if (s.endsWith(entry.key)) {
            def numStr = s[0..(s.length() - entry.key.length() - 1)].trim().replace(",", "")
            try { return [numStr.toDouble(), entry.value] } catch (e) { return [null, entry.value] }
        }
    }
    def m = s =~ /^([+-]?\d[\d,]*\.?\d*)\s*(.*)$/
    if (m.matches()) {
        try {
            def num  = m.group(1).replace(",", "").toDouble()
            def unit = m.group(2).trim() ?: null
            return [num, unit]
        } catch (e) {}
    }
    return [null, null]
}

private void safeNumericEvent(String attr, Object raw, String defaultUnit = null) {
    if (raw == null) return
    def (num, unit) = extractNumeric(raw)
    if (num != null) {
        def u = unit ?: defaultUnit
        if (u) sendEvent(name: attr, value: num, unit: u)
        else   sendEvent(name: attr, value: num)
    }
}

private void safeEvent(String attr, Object value) {
    if (value == null) return
    sendEvent(name: attr, value: value.toString())
}
