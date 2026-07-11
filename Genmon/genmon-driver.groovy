/**
 * Genmon Generator Monitor — Hubitat Driver
 *
 *   - Converted by Brian Wilson based on https://github.com/jgyates/genmon-ha using Claude Code
 *   - v2.01 - 021MAY26 - Initial Release
 *   - v2.1.0 - 30JUN26 - Native Genmon addon; Home Assistant no longer required.
 *                        Default port updated to 9084.
 *
 * Communicates directly with the Genmon REST/WebSocket API using the native
 * Genmon addon for Hubitat.  Home Assistant is NOT required.
 * Maintains a persistent WebSocket connection for real-time push updates;
 * falls back to HTTP polling when the WebSocket is unavailable.
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
 *   - Native Genmon REST/WebSocket API addon enabled in Genmon (port 9084 by default).
 *     The Home Assistant integration addon is no longer required.
 *   - HTTPS/WSS is enabled by default here.  Disable only if your Genmon
 *     instance is configured for plain HTTP.
 */

metadata {
    definition(
        name:      "Genmon Generator Monitor",
        namespace: "brianwilson-hubitat",
        author:    "Brian Wilson",
        importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Genmon/genmon-driver.groovy"
    ) {
        capability "Refresh"
        capability "Sensor"
        capability "PowerMeter"          // power (W)
        capability "VoltageMeasurement"  // voltage (V)
        capability "Switch"              // on = running, off = stopped
        capability "Initialize"          // tells Hubitat to call initialize() on hub start

        // ── Engine / status  (path: Status/Engine/...) ───────────────────
        attribute "engineState",            "string"   // e.g. "Off - Ready", "Running"
        attribute "switchState",            "string"   // e.g. "Auto", "Manual"
        attribute "activeAlarms",           "string"   // "No Active Alarms" or description
        attribute "alarmActive",            "string"   // "true" / "false" derived from above
        attribute "batteryVoltage",         "number"   // V   Status/Engine/Battery Voltage
        attribute "engineRPM",             "number"   //     Status/Engine/RPM
        attribute "outputFrequency",        "number"   // Hz  Status/Engine/Frequency
        attribute "outputVoltage",          "number"   // V   Status/Engine/Output Voltage
        attribute "outputCurrent",          "number"   // A   Status/Engine/Output Current
        attribute "outputPower",            "number"   // kW  Status/Engine/Output Power (Single Phase)
        attribute "batteryChargerCurrent",  "number"   // mA  Status/Engine/Battery Charger Current
        attribute "currentL1",             "number"   // A   Status/Engine/Current L1
        attribute "currentL2",             "number"   // A   Status/Engine/Current L2
        attribute "activeRotorPoles",       "number"   //     Status/Engine/Active Rotor Poles (Calculated)

        // ── Utility line  (path: Status/Line/...) ────────────────────────
        attribute "utilityVoltage",         "number"   // V   Status/Line/Utility Voltage
        attribute "utilityVoltageMax",      "number"   // V   Status/Line/Utility Max Voltage
        attribute "utilityVoltageMin",      "number"   // V   Status/Line/Utility Min Voltage
        attribute "utilityThreshold",       "number"   // V   Status/Line/Utility Threshold Voltage

        // ── Outage  (path: Outage/...) ───────────────────────────────────
        attribute "systemInOutage",         "string"   //     Outage/System In Outage  ("Yes"/"No")
        attribute "utilityPowerPresent",    "string"   //     derived: inverse of systemInOutage
        attribute "outageStatus",           "string"   //     Outage/Status
        attribute "startupDelay",           "number"   // s   Outage/Startup Delay
        attribute "utilityVoltageMaximum",  "number"   // V   Outage/Utility Voltage Maximum
        attribute "utilityVoltageMinimum",  "number"   // V   Outage/Utility Voltage Minimum

        // ── Maintenance — general  (path: Maintenance/...) ───────────────
        attribute "generatorModel",         "string"   //     Maintenance/Model
        attribute "controllerDetected",     "string"   //     Maintenance/Controller Detected
        attribute "nominalRPM",            "number"   // RPM Maintenance/Nominal RPM
        attribute "ratedKW",               "number"   // kW  Maintenance/Rated kW
        attribute "fuelType",              "string"   //     Maintenance/Fuel Type
        attribute "serialNumber",           "string"   //     Maintenance/Generator Serial Number
        attribute "nominalFrequency",       "number"   // Hz  Maintenance/Nominal Frequency
        attribute "generatorPhase",         "number"   //     Maintenance/Generator Phase
        attribute "engineDisplacement",     "string"   //     Maintenance/Engine Displacement
        attribute "energy30Days",           "number"   // kWh Maintenance/kW Hours in last 30 days
        attribute "fuelConsumption30Days",  "number"   // gal Maintenance/Fuel Consumption in last 30 days
        attribute "runHoursLast30Days",     "number"   // h   Maintenance/Run Hours in last 30 days
        attribute "estimatedFuelInTank",    "number"   // gal Maintenance/Estimated Fuel In Tank
        attribute "hoursOfFuelRemaining",   "number"   // h   Maintenance/Hours of Fuel Remaining (Estimated 0.50 Load)
        attribute "hoursOfFuelAtLoad",      "number"   // h   Maintenance/Hours of Fuel Remaining (Current Load)
        attribute "fuelLevel",             "number"   // %   Maintenance/Fuel Level Sensor
        attribute "fuelInTank",            "number"   // gal Maintenance/Fuel In Tank (Sensor)
        attribute "fuelLevelState",        "string"   //     Maintenance/Fuel Level State

        // ── Maintenance — service  (path: Maintenance/Service/...) ───────
        attribute "totalRunHours",          "number"   // h   Maintenance/Service/Total Run Hours
        attribute "hardwareVersion",        "string"   //     Maintenance/Service/Hardware Version
        attribute "firmwareVersion",        "string"   //     Maintenance/Service/Firmware Version
        attribute "serviceADue",           "string"   //     Maintenance/Service/Service A Due
        attribute "serviceBDue",           "string"   //     Maintenance/Service/Service B Due
        attribute "batteryCheckDue",        "string"   //     Maintenance/Service/Battery Check Due

        // ── Maintenance — controller settings  (Maintenance/Controller Settings/...) ──
        attribute "ratedMaxPower",          "number"   // kW  Maintenance/Controller Settings/Rated Max Power
        attribute "nominalLineVoltage",     "number"   // V   Maintenance/Controller Settings/Nominal Line Voltage
        attribute "calibrateCurrent1",      "number"   //     Maintenance/Controller Settings/Calibrate Current 1
        attribute "calibrateCurrent2",      "number"   //     Maintenance/Controller Settings/Calibrate Current 2
        attribute "calibrateVolts",         "number"   //     Maintenance/Controller Settings/Calibrate Volts

        // ── Maintenance — exercise  (path: Maintenance/Exercise/...) ─────
        attribute "exerciseSchedule",       "string"   //     Maintenance/Exercise/Exercise Time
        attribute "quietMode",             "string"   //     Maintenance/Quiet Mode (if supported)

        // ── Status logs  (path: Status/Last Log Entries/Logs/...) ────────
        attribute "lastAlarmLog",           "string"   //     Status/Last Log Entries/Logs/Alarm Log
        attribute "lastRunLog",            "string"   //     Status/Last Log Entries/Logs/Run Log
        attribute "lastServiceLog",         "string"   //     Status/Last Log Entries/Logs/Service Log

        // ── Status time  (path: Status/Time/...) ─────────────────────────
        attribute "monitorTime",            "string"   //     Status/Time/Monitor Time
        attribute "generatorTime",          "string"   //     Status/Time/Generator Time

        // ── Monitor — generator stats  (Monitor/Generator Monitor Stats/...) ──
        attribute "monitorHealth",          "string"   //     Monitor/Generator Monitor Stats/Monitor Health
        attribute "genmonVersion",          "string"   //     Monitor/Generator Monitor Stats/Generator Monitor Version
        attribute "monitorRunTime",         "string"   //     Monitor/Generator Monitor Stats/Run time
        attribute "powerLogSize",           "string"   //     Monitor/Generator Monitor Stats/Power log file size
        attribute "updateAvailable",        "string"   //     Monitor/Generator Monitor Stats/Update Available
        attribute "updateVersion",          "string"   //     Monitor/Generator Monitor Stats/Update Version

        // ── Monitor — communication stats  (Monitor/Communication Stats/...) ──
        attribute "crcErrors",             "number"   //     Monitor/Communication Stats/CRC Errors
        attribute "crcPercentErrors",       "number"   // %   Monitor/Communication Stats/CRC Percent Errors
        attribute "timeoutErrors",          "string"   //     Monitor/Communication Stats/Timeout Errors
        attribute "timeoutPercentErrors",   "number"   // %   Monitor/Communication Stats/Timeout Percent Errors
        attribute "modbusExceptions",       "number"   //     Monitor/Communication Stats/Modbus Exceptions
        attribute "validationErrors",       "number"   //     Monitor/Communication Stats/Validation Errors
        attribute "syncErrors",            "number"   //     Monitor/Communication Stats/Sync Errors
        attribute "invalidData",           "number"   //     Monitor/Communication Stats/Invalid Data
        attribute "discardedBytes",         "number"   //     Monitor/Communication Stats/Discarded Bytes
        attribute "commRestarts",           "number"   //     Monitor/Communication Stats/Comm Restarts
        attribute "packetsPerSecond",       "number"   //     Monitor/Communication Stats/Packets Per Second
        attribute "averageTransactionTime", "string"   //     Monitor/Communication Stats/Average Transaction Time
        attribute "modbusTransport",        "string"   //     Monitor/Communication Stats/Modbus Transport
        attribute "serialDataRate",         "string"   //     Monitor/Communication Stats/Serial Data Rate
        attribute "packetCount",           "string"   //     Monitor/Communication Stats/Packet Count

        // ── Monitor — platform stats  (Monitor/Platform Stats/...) ───────
        attribute "cpuUsage",              "number"   // %   Monitor/Platform Stats/CPU Utilization
        attribute "memoryUtilization",      "number"   // %   Monitor/Platform Stats/Memory Utilization
        attribute "diskUtilization",        "number"   // %   Monitor/Platform Stats/Disk Utilization
        attribute "wlanSignalLevel",        "number"   // dBm Monitor/Platform Stats/WLAN Signal Level
        attribute "wlanSignalQuality",      "string"   //     Monitor/Platform Stats/WLAN Signal Quality
        attribute "wlanSignalPercent",      "number"   // %   Monitor/Platform Stats/WLAN Signal Percent
        attribute "wlanSignalNoise",        "number"   // dBm Monitor/Platform Stats/WLAN Signal Noise
        attribute "wlanESSID",             "string"   //     Monitor/Platform Stats/WLAN ESSID
        attribute "piModel",               "string"   //     Monitor/Platform Stats/Pi Model
        attribute "piCpuThrottling",        "string"   //     Monitor/Platform Stats/Pi CPU Frequency Throttling
        attribute "piArmFrequencyCap",      "string"   //     Monitor/Platform Stats/Pi ARM Frequency Cap
        attribute "piUndervoltage",         "string"   //     Monitor/Platform Stats/Pi Undervoltage
        attribute "osName",                "string"   //     Monitor/Platform Stats/OS Name
        attribute "osVersion",             "string"   //     Monitor/Platform Stats/OS Version
        attribute "systemUptime",           "string"   //     Monitor/Platform Stats/System Uptime
        attribute "networkInterfaceUsed",   "string"   //     Monitor/Platform Stats/Network Interface Used
        attribute "systemTime",            "string"   //     Monitor/Platform Stats/System Time
        attribute "cpuTemperature",         "number"   // °C  Tiles/CPU Temp/value

        // ── Monitor — weather  (Monitor/Weather/...) ─────────────────────
        attribute "weatherConditions",      "string"   //     Monitor/Weather/Conditions
        attribute "currentTemperature",     "number"   // °F  Monitor/Weather/Current Temperature

        // ── Connectivity ──────────────────────────────────────────────────
        attribute "connectionStatus",       "string"
        attribute "lastUpdate",             "string"

        // ── Custom commands ───────────────────────────────────────────────
        command "startGenerator"
        command "stopGenerator"
        command "startTransfer"
        command "startExercise"
        command "setQuietModeOn"
        command "setQuietModeOff"
        command "disconnect"
        command "sendCustomCommand", [[name: "command*", type: "STRING", description: "Raw genmon command string"]]
    }

    preferences {
        input name: "host",         type: "text",     title: "Genmon IP Address (must be a dotted-decimal IP, e.g. 192.168.1.100)", required: true
        input name: "port",         type: "number",   title: "Port",               defaultValue: 9084
        input name: "apiKey",       type: "password", title: "API Key — requires Genmon v2.0 or later with the native REST/WebSocket API addon enabled", required: true
        input name: "useSSL",       type: "bool",     title: "Use HTTPS / WSS (default: on)", defaultValue: true
        input name: "pollInterval", type: "enum",     title: "Fallback Poll Interval (when WebSocket is unavailable)",
                                    options: ["5": "5 sec", "10": "10 sec", "30": "30 sec",
                                              "60": "1 min", "300": "5 min"],
                                    defaultValue: "30"
        input name: "wsMinInterval", type: "enum",    title: "Minimum interval between WebSocket state updates (genmon pushes very frequently — throttle here to avoid Hubitat event queue overload)",
                                    options: ["10": "10 sec", "15": "15 sec", "30": "30 sec", "60": "1 min", "120": "2 min"],
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
    state.wsConnected        = false
    state.wsAuthPending      = false
    atomicState.lastWsParsed = 0L

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
        log.warn "Genmon: health check failed — ${e.message} (is genmon running on ${host}:${port}?)"
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
        def p   = (port ?: 9084).toInteger()
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
    // e.g. 192.168.1.100:9084 → C0A80164:237C
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
    def p      = (port ?: 9084).toInteger()
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
    try {
        _parse(rawMsg)
    } catch (com.hubitat.app.exception.LimitExceededException e) {
        // Hubitat's event queue is full — silently discard this update.
        // The throttle below should prevent this in steady state; if it
        // keeps appearing, increase the "Minimum WS interval" preference.
        if (logEnable) log.warn "Genmon WS: event queue full, discarding message"
    } catch (Exception e) {
        log.warn "Genmon WS: unexpected error in parse — ${e.message}"
    }
}

private void _parse(String rawMsg) {
    if (logEnable) log.debug "Genmon WS recv: ${rawMsg.take(120)}"  // truncate noisy full payloads

    def msg
    try { msg = new groovy.json.JsonSlurper().parseText(rawMsg) }
    catch (Exception e) { log.warn "Genmon WS: failed to parse JSON — ${e.message}"; return }

    def msgType = msg?.type

    if (msgType == "auth_ok") {
        state.wsConnected   = true
        state.wsAuthPending = false
        atomicState.lastWsParsed = 0L   // force immediate parse on first real update
        log.info "Genmon WS: authenticated — push updates active"
        unschedule("pollAndReschedule")
        unschedule("refresh")
        updateStringIfChanged("connectionStatus", "connected (ws)")

    } else if (msgType == "auth_invalid" || (state.wsAuthPending && msgType != "auth_ok")) {
        log.error "Genmon WS: authentication failed — check API key"
        interfaces.webSocket.close()
        state.wsAuthPending = false
        updateStringIfChanged("connectionStatus", "error: WS auth failed")
        startPolling()

    } else if (msgType in ["state_update", "full_state"]) {
        def stateData = msg?.state
        if (!stateData) return

        // atomicState is thread-safe — concurrent parse() callbacks all read
        // the same committed value so only one wins the throttle window.
        def minMs    = (wsMinInterval ?: "30").toLong() * 1000L
        def nowMs    = now()
        def lastMs   = (atomicState.lastWsParsed ?: 0L)
        if ((nowMs - lastMs) < minMs) {
            // Still within throttle window — drop silently
            return
        }
        atomicState.lastWsParsed = nowMs

        parseStatus(stateData)
        updateStringIfChanged("lastUpdate",       new Date().toString())
        updateStringIfChanged("connectionStatus", "connected (ws)")

    } else if (msgType == "ping") {
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
                safeEvent("controllerType",  d?.controller       ?: d?.Controller)
                safeEvent("generatorModel",  d?.model            ?: d?.Model)
                safeEvent("serialNumber",    d?.serial           ?: d?.SerialNumber)
                safeEvent("firmwareVersion", d?.firmware         ?: d?.FirmwareVersion)
                safeEvent("hardwareVersion", d?.hardware_version ?: d?.HardwareVersion ?: d?."Hardware Version")
            }
        }
    } catch (Exception e) {
        if (logEnable) log.debug "Genmon: fetchInfo failed — ${e.message}"
    }
}

// ── Status parsing ────────────────────────────────────────────────────────────
// All paths verified against /api/entities response.

private void parseStatus(Map data) {
    if (!data) return

    // ── Status/Engine ─────────────────────────────────────────────────
    def engineState = pathGet(data, "Status/Engine/Engine State")
    safeStringEvent("engineState", engineState)
    if (engineState != null) {
        def es      = engineState.toString()
        def running = es in ["Running", "Exercising", "Running Manual"]
        updateStringIfChanged("switch", running ? "on" : "off")
    }

    safeStringEvent("switchState",      pathGet(data, "Status/Engine/Switch State"))
    safeNumericEvent("batteryVoltage",  pathGet(data, "Status/Engine/Battery Voltage"),        "V")
    safeNumericEvent("engineRPM",       pathGet(data, "Status/Engine/RPM"),                    "RPM")
    safeNumericEvent("outputFrequency", pathGet(data, "Status/Engine/Frequency"),              "Hz")
    safeNumericEvent("outputVoltage",   pathGet(data, "Status/Engine/Output Voltage"),         "V")
    safeNumericEvent("outputCurrent",   pathGet(data, "Status/Engine/Output Current"),         "A")
    safeNumericEvent("batteryChargerCurrent", pathGet(data, "Status/Engine/Battery Charger Current"), "mA")
    safeNumericEvent("currentL1",       pathGet(data, "Status/Engine/Current L1"),             "A")
    safeNumericEvent("currentL2",       pathGet(data, "Status/Engine/Current L2"),             "A")
    safeNumericEvent("activeRotorPoles", pathGet(data, "Status/Engine/Active Rotor Poles (Calculated)"))

    def powerRaw = pathGet(data, "Status/Engine/Output Power (Single Phase)")
    if (powerRaw != null) {
        def (num, unit) = extractNumeric(powerRaw)
        if (num != null) {
            def kw    = (unit == "W") ? num / 1000.0 : num
            def kwVal = kw.round(3)
            def wVal  = (kw * 1000).round(0)
            if (kwVal.toString() != device.currentValue("outputPower")?.toString())
                sendEvent(name: "outputPower", value: kwVal, unit: "kW")
            if (wVal.toString() != device.currentValue("power")?.toString())
                sendEvent(name: "power", value: wVal, unit: "W")
        }
    }

    // Active alarms — path: Status/Engine/System In Alarm
    def alarmRaw = pathGet(data, "Status/Engine/System In Alarm")
    def alarmStr = extractString(alarmRaw)
    def hasAlarm = alarmStr && alarmStr.trim() && alarmStr.trim() != "No Active Alarms"
    updateStringIfChanged("activeAlarms", alarmStr ?: "No Active Alarms")
    updateStringIfChanged("alarmActive",  hasAlarm.toString())

    // Transfer to generator: derived — frequency > 0 means generator is supplying load
    def freqRaw = pathGet(data, "Status/Engine/Frequency")
    if (freqRaw != null) {
        def (freqNum, _) = extractNumeric(freqRaw)
        if (freqNum != null) updateStringIfChanged("transferToGenerator", (freqNum > 0) ? "on" : "off")
    }

    // ── Status/Line (utility) ─────────────────────────────────────────
    safeNumericEvent("utilityVoltage",   pathGet(data, "Status/Line/Utility Voltage"),           "V")
    safeNumericEvent("utilityVoltageMax", pathGet(data, "Status/Line/Utility Max Voltage"),      "V")
    safeNumericEvent("utilityVoltageMin", pathGet(data, "Status/Line/Utility Min Voltage"),      "V")
    safeNumericEvent("utilityThreshold",  pathGet(data, "Status/Line/Utility Threshold Voltage"), "V")

    // ── Status/Last Log Entries/Logs ──────────────────────────────────
    safeStringEvent("lastAlarmLog",   pathGet(data, "Status/Last Log Entries/Logs/Alarm Log"))
    safeStringEvent("lastRunLog",     pathGet(data, "Status/Last Log Entries/Logs/Run Log"))
    safeStringEvent("lastServiceLog", pathGet(data, "Status/Last Log Entries/Logs/Service Log"))

    // ── Status/Time ───────────────────────────────────────────────────
    safeStringEvent("monitorTime",   pathGet(data, "Status/Time/Monitor Time"))
    safeStringEvent("generatorTime", pathGet(data, "Status/Time/Generator Time"))

    // ── Outage ────────────────────────────────────────────────────────
    def outageRaw = pathGet(data, "Outage/System In Outage")
    if (outageRaw != null) {
        def outageStr = extractString(outageRaw)
        updateStringIfChanged("systemInOutage",     outageStr)
        updateStringIfChanged("utilityPowerPresent", (outageStr == "No") ? "true" : "false")
    }
    safeStringEvent("outageStatus",        pathGet(data, "Outage/Status"))
    safeNumericEvent("startupDelay",       pathGet(data, "Outage/Startup Delay"),          "s")
    safeNumericEvent("utilityVoltageMaximum", pathGet(data, "Outage/Utility Voltage Maximum"), "V")
    safeNumericEvent("utilityVoltageMinimum", pathGet(data, "Outage/Utility Voltage Minimum"), "V")

    // ── Maintenance (general) ─────────────────────────────────────────
    safeStringEvent("generatorModel",     pathGet(data, "Maintenance/Model"))
    safeStringEvent("controllerDetected", pathGet(data, "Maintenance/Controller Detected"))
    safeNumericEvent("nominalRPM",        pathGet(data, "Maintenance/Nominal RPM"),         "RPM")
    safeNumericEvent("ratedKW",           pathGet(data, "Maintenance/Rated kW"),            "kW")
    safeStringEvent("fuelType",           pathGet(data, "Maintenance/Fuel Type"))
    safeStringEvent("serialNumber",       pathGet(data, "Maintenance/Generator Serial Number"))
    safeNumericEvent("nominalFrequency",  pathGet(data, "Maintenance/Nominal Frequency"),   "Hz")
    safeStringEvent("generatorPhase",     pathGet(data, "Maintenance/Generator Phase"))
    safeStringEvent("engineDisplacement", pathGet(data, "Maintenance/Engine Displacement"))
    safeNumericEvent("energy30Days",      pathGet(data, "Maintenance/kW Hours in last 30 days"), "kWh")
    safeNumericEvent("fuelConsumption30Days", pathGet(data, "Maintenance/Fuel Consumption in last 30 days"), "gal")
    safeNumericEvent("runHoursLast30Days",   pathGet(data, "Maintenance/Run Hours in last 30 days"), "h")
    safeNumericEvent("estimatedFuelInTank",  pathGet(data, "Maintenance/Estimated Fuel In Tank "), "gal")  // trailing space is in the API
    safeNumericEvent("hoursOfFuelRemaining", pathGet(data, "Maintenance/Hours of Fuel Remaining (Estimated 0.50 Load )"), "h")
    safeNumericEvent("hoursOfFuelAtLoad",    pathGet(data, "Maintenance/Hours of Fuel Remaining (Current Load)"), "h")
    safeNumericEvent("fuelLevel",            pathGet(data, "Maintenance/Fuel Level Sensor"), "%")
    safeNumericEvent("fuelInTank",           pathGet(data, "Maintenance/Fuel In Tank (Sensor)"), "gal")
    safeStringEvent("fuelLevelState",        pathGet(data, "Maintenance/Fuel Level State"))
    safeStringEvent("exerciseSchedule",      pathGet(data, "Maintenance/Exercise/Exercise Time"))
    safeStringEvent("quietMode",             pathGet(data, "Maintenance/Quiet Mode"))

    // ── Maintenance/Service ───────────────────────────────────────────
    safeNumericEvent("totalRunHours",   pathGet(data, "Maintenance/Service/Total Run Hours"),  "h")
    safeStringEvent("hardwareVersion",  pathGet(data, "Maintenance/Service/Hardware Version"))
    safeStringEvent("firmwareVersion",  pathGet(data, "Maintenance/Service/Firmware Version"))
    safeStringEvent("serviceADue",      pathGet(data, "Maintenance/Service/Service A Due"))
    safeStringEvent("serviceBDue",      pathGet(data, "Maintenance/Service/Service B Due"))
    safeStringEvent("batteryCheckDue",  pathGet(data, "Maintenance/Service/Battery Check Due"))

    // ── Maintenance/Controller Settings ──────────────────────────────
    safeNumericEvent("ratedMaxPower",     pathGet(data, "Maintenance/Controller Settings/Rated Max Power"),     "kW")
    safeNumericEvent("nominalLineVoltage", pathGet(data, "Maintenance/Controller Settings/Nominal Line Voltage"), "V")
    safeNumericEvent("calibrateCurrent1", pathGet(data, "Maintenance/Controller Settings/Calibrate Current 1"))
    safeNumericEvent("calibrateCurrent2", pathGet(data, "Maintenance/Controller Settings/Calibrate Current 2"))
    safeNumericEvent("calibrateVolts",    pathGet(data, "Maintenance/Controller Settings/Calibrate Volts"))

    // ── Monitor/Generator Monitor Stats ──────────────────────────────
    safeStringEvent("monitorHealth",  pathGet(data, "Monitor/Generator Monitor Stats/Monitor Health"))
    safeStringEvent("genmonVersion",  pathGet(data, "Monitor/Generator Monitor Stats/Generator Monitor Version"))
    safeStringEvent("monitorRunTime", pathGet(data, "Monitor/Generator Monitor Stats/Run time"))
    safeStringEvent("powerLogSize",   pathGet(data, "Monitor/Generator Monitor Stats/Power log file size"))
    safeStringEvent("updateAvailable", pathGet(data, "Monitor/Generator Monitor Stats/Update Available"))
    safeStringEvent("updateVersion",   pathGet(data, "Monitor/Generator Monitor Stats/Update Version"))

    // ── Monitor/Communication Stats ───────────────────────────────────
    safeNumericEvent("crcErrors",            pathGet(data, "Monitor/Communication Stats/CRC Errors"))
    safeNumericEvent("crcPercentErrors",     pathGet(data, "Monitor/Communication Stats/CRC Percent Errors"),     "%")
    safeStringEvent("timeoutErrors",         pathGet(data, "Monitor/Communication Stats/Timeout Errors"))
    safeNumericEvent("timeoutPercentErrors", pathGet(data, "Monitor/Communication Stats/Timeout Percent Errors"), "%")
    safeNumericEvent("modbusExceptions",     pathGet(data, "Monitor/Communication Stats/Modbus Exceptions"))
    safeNumericEvent("validationErrors",     pathGet(data, "Monitor/Communication Stats/Validation Errors"))
    safeNumericEvent("syncErrors",           pathGet(data, "Monitor/Communication Stats/Sync Errors"))
    safeNumericEvent("invalidData",          pathGet(data, "Monitor/Communication Stats/Invalid Data"))
    safeNumericEvent("discardedBytes",       pathGet(data, "Monitor/Communication Stats/Discarded Bytes"))
    safeNumericEvent("commRestarts",         pathGet(data, "Monitor/Communication Stats/Comm Restarts"))
    safeNumericEvent("packetsPerSecond",     pathGet(data, "Monitor/Communication Stats/Packets Per Second"))
    safeStringEvent("averageTransactionTime", pathGet(data, "Monitor/Communication Stats/Average Transaction Time"))
    safeStringEvent("modbusTransport",       pathGet(data, "Monitor/Communication Stats/Modbus Transport"))
    safeStringEvent("serialDataRate",        pathGet(data, "Monitor/Communication Stats/Serial Data Rate"))
    safeStringEvent("packetCount",           pathGet(data, "Monitor/Communication Stats/Packet Count"))

    // ── Monitor/Platform Stats ────────────────────────────────────────
    safeNumericEvent("cpuUsage",          pathGet(data, "Monitor/Platform Stats/CPU Utilization"),    "%")
    safeNumericEvent("memoryUtilization", pathGet(data, "Monitor/Platform Stats/Memory Utilization"), "%")
    safeNumericEvent("diskUtilization",   pathGet(data, "Monitor/Platform Stats/Disk Utilization"),   "%")
    safeNumericEvent("wlanSignalLevel",   pathGet(data, "Monitor/Platform Stats/WLAN Signal Level"),  "dBm")
    safeStringEvent("wlanSignalQuality",  pathGet(data, "Monitor/Platform Stats/WLAN Signal Quality"))
    safeNumericEvent("wlanSignalPercent", pathGet(data, "Monitor/Platform Stats/WLAN Signal Percent"), "%")
    safeNumericEvent("wlanSignalNoise",   pathGet(data, "Monitor/Platform Stats/WLAN Signal Noise"),  "dBm")
    safeStringEvent("wlanESSID",          pathGet(data, "Monitor/Platform Stats/WLAN ESSID"))
    safeStringEvent("piModel",            pathGet(data, "Monitor/Platform Stats/Pi Model"))
    safeStringEvent("piCpuThrottling",    pathGet(data, "Monitor/Platform Stats/Pi CPU Frequency Throttling"))
    safeStringEvent("piArmFrequencyCap",  pathGet(data, "Monitor/Platform Stats/Pi ARM Frequency Cap"))
    safeStringEvent("piUndervoltage",     pathGet(data, "Monitor/Platform Stats/Pi Undervoltage"))
    safeStringEvent("osName",             pathGet(data, "Monitor/Platform Stats/OS Name"))
    safeStringEvent("osVersion",          pathGet(data, "Monitor/Platform Stats/OS Version"))
    safeStringEvent("systemUptime",       pathGet(data, "Monitor/Platform Stats/System Uptime"))
    safeStringEvent("networkInterfaceUsed", pathGet(data, "Monitor/Platform Stats/Network Interface Used"))
    safeStringEvent("systemTime",         pathGet(data, "Monitor/Platform Stats/System Time"))

    // ── Monitor/Weather ───────────────────────────────────────────────
    safeStringEvent("weatherConditions", pathGet(data, "Monitor/Weather/Conditions"))
    safeNumericEvent("currentTemperature", pathGet(data, "Monitor/Weather/Current Temperature"), "°F")

    // ── Tiles (CPU temp) ──────────────────────────────────────────────
    safeNumericEvent("cpuTemperature", pathGet(data, "Tiles/CPU Temp/value"), "°C")
}

// ── Switch capability ─────────────────────────────────────────────────────────

def on()  { startGenerator() }
def off() { stopGenerator()  }

// ── Custom commands ───────────────────────────────────────────────────────────

// Commands verified against /api/entities "buttons" definitions
def startGenerator()  { sendGenmonCommand("setremote=START") }
def stopGenerator()   { sendGenmonCommand("setremote=STOP") }
def startTransfer()   { sendGenmonCommand("setremote=STARTTRANSFER") }
def startExercise()   { sendGenmonCommand("setremote=STARTEXERCISE") }
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
    def p      = (port ?: 9084).toInteger()
    return [
        uri:                "${scheme}://${host}:${p}${path}",
        headers:            ["Authorization": "Bearer ${apiKey}"],
        requestContentType: "application/json",
        ignoreSSLIssues:    (useSSL as boolean),
        timeout:            10,
    ]
}

// ── Value extraction helpers ──────────────────────────────────────────────────

// Traverse a "/" separated path through nested Maps.
// e.g. pathGet(data, "Monitor/Communication Stats/CRC Percent Errors")
private Object pathGet(Object data, String path) {
    def current = data
    for (String key : path.tokenize("/")) {
        if (current instanceof Map) {
            current = current.get(key)
        } else {
            return null
        }
        if (current == null) return null
    }
    return current
}

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
    if (num == null) return
    def u        = unit ?: defaultUnit
    def newVal   = num.toString()
    def curVal   = device.currentValue(attr)?.toString()
    if (newVal == curVal) return   // no change — skip sendEvent
    if (u) sendEvent(name: attr, value: num, unit: u)
    else   sendEvent(name: attr, value: num)
}

private void safeStringEvent(String attr, Object raw) {
    if (raw == null) return
    def s = extractString(raw)
    if (s == null) return
    updateStringIfChanged(attr, s)
}

private void updateStringIfChanged(String attr, String newVal) {
    if (newVal == null) return
    def curVal = device.currentValue(attr)?.toString()
    if (newVal == curVal) return   // no change — skip sendEvent
    sendEvent(name: attr, value: newVal)
}

private void safeEvent(String attr, Object value) {
    if (value == null) return
    updateStringIfChanged(attr, value.toString())
}
