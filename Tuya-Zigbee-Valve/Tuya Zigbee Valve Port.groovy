/**
 *  Tuya Zigbee Valve Port - component child driver
 *
 *  Based on kkossev/Hubitat's "Tuya Zigbee Valve Component Child" (Apache License 2.0, original license header
 *  preserved below), re-namespaced to this fork ('bdwilson'/'Tuya Zigbee Valve Port') so it has a distinct
 *  identity from a separately-installed real kkossev driver, paired with this fork's parent driver
 *  ("Tuya Zigbee Valve", also namespace 'kkossev' - matches upstream exactly, only the importUrl differs).
 *
 *  The ONE addition on top of upstream: open() takes an optional duration (minutes). Earlier versions of this
 *  fork tried to make the PARENT handle a per-run duration - first by writing the shared Zigbee firmware
 *  attribute (FC11 0x501D, "manual run duration") before opening, then by having the parent arm a runIn() timer
 *  itself - both of which meant carrying real, hand-maintained deltas against upstream's parent driver, and
 *  neither one is what actually fixed a real Maker API 500 chased across several earlier attempts. This version
 *  drops all of that: the parent is upstream, completely unmodified in its open-related behavior (plain,
 *  zero-arg open()/close(), no duration parameter, no per-port auto-off preferences, no software timer). This
 *  driver's own open(duration) calls the normal open (exactly what on() already does) and, if a duration was
 *  given, arms its OWN runIn() timer - entirely local to this child device, calling this driver's own close()
 *  after the requested time. No parent involvement, no firmware writes, no shared/cross-port state.
 *
 *  Confirmed against three independently-working precedents using this same
 *  capability "Valve" + command "open", ["number"] shape (not a duplicate/ambiguous command - Maker API resolves
 *  this fine): a "Simple Valve Driver" and a "Raincloud Valve" driver, both previously used in production, and
 *  Raincloud's own connector app already implementing exactly this open-now-plus-runIn(15,...)-later pattern for
 *  its own follow-up status check.
 *
 *  Upgrade note: this fork's child DNI scheme changed to match upstream (-ZF2-N, was -PN) - upgrading orphans any
 *  previously-created child devices from an older version of this fork. Expected; recreate them (parent's
 *  Configure) after updating.
 *
 *  --- Original license header from kkossev/Hubitat's Tuya Zigbee Valve Component Child.groovy: ---
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

metadata {
    definition(
        name: 'Tuya Zigbee Valve Port',
        namespace: 'bdwilson',
        author: 'Krassimir Kossev (fork: Brian Wilson)',
        component: true,
        importUrl: 'https://raw.githubusercontent.com/bdwilson/hubitat/master/Tuya-Zigbee-Valve/Tuya%20Zigbee%20Valve%20Port.groovy'
    ) {
        capability 'Actuator'
        capability 'Valve'
        capability 'Switch'
        capability 'Refresh'

        // Optional duration (minutes) - see the file header for why this is safe (matches proven precedents) and
        // what it does (local runIn() timer only, nothing sent to the parent/firmware).
        command 'open', ['number']

        attribute 'irrigationStartTime', 'string'
        attribute 'irrigationEndTime', 'string'
        attribute 'lastIrrigationDuration', 'string'
        attribute 'lastValveOpenDuration', 'number'
        attribute 'valveStatus', 'enum', ['normal', 'shortage', 'leakage', 'fail-safe', 'shortage and leakage', 'shortage and fail-safe', 'leakage and fail-safe', 'shortage, leakage and fail-safe']

        attribute 'manualIrrigationDuration', 'number'
        attribute 'manualIrrigationMode', 'enum', ['duration', 'capacity']
        attribute 'manualIrrigationAmountUnit', 'enum', ['US gallon', 'liter']
        attribute 'manualIrrigationAmount', 'number'
        attribute 'manualFailSafe', 'number'

        command 'setManualIrrigationDuration', [[name:'duration', type:'NUMBER', constraints:['1..719']]]
        command 'setManualIrrigationAmount', [[name:'amount', type:'NUMBER', constraints:['0..10000']]]
    }

    preferences {
        input(name: 'txtEnable', type: 'bool', title: '<b>Description text logging</b>', description: 'Display child command activity in Hubitat logs.', defaultValue: true)
        input(name: 'logEnable', type: 'bool', title: '<b>Debug logging</b>', description: 'Detailed child-driver diagnostics. Automatically disables after 24 hours.', defaultValue: true)
        input(name: 'manualAmountUnitPreference', type: 'enum', title: '<b>Manual irrigation amount unit</b>', description: 'Shared by both SWV-ZF2 valve children. Used by the simple manual-irrigation commands.', options: ['US gallon', 'liter'], defaultValue: 'liter', required: true)
        input(name: 'manualFailSafePreference', type: 'number', title: '<b>Manual irrigation fail-safe</b>', description: 'Shared by both SWV-ZF2 valve children. Safety timeout in minutes (0..719).', range: '0..719', defaultValue: 0, required: true)
    }
}

static String version() { '1.0.0' }
static String timeStamp() { '2026/08/15 09:00 PM' }
String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() }

void installed() {
    log.info "${device.displayName} installed; driver version ${driverVersionAndTimeStamp()}"
    sendEvent(name: 'valve', value: 'unknown')
    sendEvent(name: 'switch', value: 'unknown')
    sendEvent(name: 'manualIrrigationMode', value: 'unknown')
    sendEvent(name: 'manualIrrigationAmountUnit', value: 'unknown')
    state.manualAmountUnitPreference = getManualAmountUnitPreference()
    state.manualFailSafePreference = getManualFailSafePreference()
}

void updated() {
    logInfo "preferences updated; description logging=${settings?.txtEnable == true}, debug logging=${settings?.logEnable == true}"
    if (settings?.logEnable == true) {
        runIn(86400, 'logsOff', [overwrite:true])
        logDebug 'debug logging will be disabled automatically after 24 hours'
    } else {
        unschedule('logsOff')
    }
    String amountUnit = getManualAmountUnitPreference()
    BigDecimal failSafe = getManualFailSafePreference()
    String reportedUnit = device.currentValue('manualIrrigationAmountUnit') in ['US gallon', 'liter'] ? device.currentValue('manualIrrigationAmountUnit') : null
    BigDecimal reportedFailSafe = getReportedManualFailSafe()
    String previousUnit = state.manualAmountUnitPreference ?: reportedUnit ?: 'liter'
    BigDecimal previousFailSafe = state.manualFailSafePreference != null ? state.manualFailSafePreference as BigDecimal : (reportedFailSafe ?: 0)
    state.manualAmountUnitPreference = amountUnit
    state.manualFailSafePreference = failSafe
    if (amountUnit != previousUnit || failSafe != previousFailSafe) {
        logInfo "manual irrigation preferences changed; applying unit=${amountUnit}, fail-safe=${failSafe} min"
        parent?.componentApplyManualIrrigationPreferences(device, amountUnit, failSafe)
    }
}

void parse(List<Map> events) {
    String previousSwitch = device.currentValue('switch')
    events.each { Map event ->
        logDebug "event ${event}"
        sendEvent(event)
    }
    Map switchEvent = events.find { it.name == 'switch' }
    if (switchEvent != null && switchEvent.value != previousSwitch) {
        logInfo "valve is ${switchEvent.value}"
    }
    Map amountUnitEvent = events.find { it.name == 'manualIrrigationAmountUnit' }
    Map failSafeEvent = events.find { it.name == 'manualFailSafe' }
    if (amountUnitEvent != null) {
        state.manualAmountUnitPreference = amountUnitEvent.value
        device.updateSetting('manualAmountUnitPreference', [value:amountUnitEvent.value, type:'enum'])
    }
    if (failSafeEvent != null) {
        state.manualFailSafePreference = failSafeEvent.value as BigDecimal
        device.updateSetting('manualFailSafePreference', [value:failSafeEvent.value, type:'number'])
    }
}

// duration (minutes), optional: opens normally (identical to a plain open - exactly what on() does), then, only
// if given, arms a LOCAL runIn() timer to close this same child device after that many minutes. Nothing about
// the duration is sent to the parent or written to the device's firmware - this device closing itself is the
// only mechanism. overwrite:true replaces any previously-armed timer from an earlier open(duration) on this
// same child, so re-opening with a new duration doesn't leave two competing close schedules.
void open(duration = null) {
    try {
        logInfo 'requesting valve open'
        parent?.componentOpen(device)
        if (duration != null) {
            BigDecimal mins = duration as BigDecimal
            if (mins > 0) {
                logInfo "opened for ${mins} minute${mins == 1 ? '' : 's'} - closing via a local timer on this device"
                runIn((mins * 60).toLong(), 'autoCloseAfterDuration', [overwrite: true])
            }
        }
    } catch (Exception e) {
        log.error "open(duration=${duration}) threw: ${e}"
        throw e
    }
}

// Cancels any pending open(duration) auto-close first - otherwise a stale timer from an earlier timed run could
// fire later and incorrectly close an unrelated, still-wanted-open future run.
void close() {
    unschedule('autoCloseAfterDuration')
    logInfo 'requesting valve close'
    parent?.componentClose(device)
}

void autoCloseAfterDuration() {
    logInfo 'open(duration) timer expired - closing'
    close()
}

// on()/off() route through open()/close() (not directly to the parent) so the auto-close timer arm/cancel logic
// above applies consistently regardless of which capability (Valve or Switch) is used to control this device.
void on() {
    open()
}

void off() {
    close()
}

void refresh() {
    logDebug 'requesting parent device refresh'
    parent?.componentRefresh(device)
}

String getManualAmountUnitPreference() {
    return settings?.manualAmountUnitPreference ?: device.currentValue('manualIrrigationAmountUnit') ?: 'liter'
}

BigDecimal getManualFailSafePreference() {
    def v = settings?.manualFailSafePreference
    if (v == null) {
        v = getReportedManualFailSafe() ?: 0
    }
    return v as BigDecimal
}

BigDecimal getReportedManualFailSafe() {
    try {
        return device.currentValue('manualFailSafe') as BigDecimal
    } catch (ignored) {
        return null
    }
}

void setManualIrrigationDuration(BigDecimal duration) {
    String amountUnit = getManualAmountUnitPreference()
    BigDecimal failSafe = getManualFailSafePreference()
    logInfo "setting shared manual irrigation duration to ${duration} min (unit=${amountUnit}, fail-safe=${failSafe} min)"
    parent?.componentSetManualIrrigationDuration(device, duration, amountUnit, failSafe)
}

void setManualIrrigationAmount(BigDecimal amount) {
    String amountUnit = getManualAmountUnitPreference()
    BigDecimal failSafe = getManualFailSafePreference()
    logInfo "setting shared manual irrigation amount to ${amount} ${amountUnit} (fail-safe=${failSafe} min)"
    parent?.componentSetManualIrrigationAmount(device, amount, amountUnit, failSafe)
}

void logsOff() {
    log.warn "${device.displayName} debug logging disabled automatically"
    device.updateSetting('logEnable', [value:false, type:'bool'])
}

void logDebug(String message) {
    if (settings?.logEnable == true) { log.debug "${device.displayName} ${message}" }
}

void logInfo(String message) {
    if (settings?.txtEnable != false) { log.info "${device.displayName} ${message}" }
}
