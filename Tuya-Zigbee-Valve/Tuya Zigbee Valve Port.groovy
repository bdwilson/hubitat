/**
 *  Tuya Zigbee Valve Port - child component driver
 *
 *  Thin shim device for a single port of a multi-port valve (currently SONOFF SWV-ZF2 dual-port).
 *  All Zigbee communication and parsing lives in the parent "Tuya Zigbee Valve" driver; this driver
 *  only forwards commands up to the parent (via the standard Hubitat componentXxx() convention) and
 *  renders the state the parent pushes back down via parse(). Each port gets its own independent
 *  irrigation history (start/end time, duration, fault state) instead of those being single attributes
 *  shared - and overwritten by whichever port last reported - on the parent device. Cumulative flow
 *  volume (irrigationVolume/waterConsumed) is NOT per-port - the ZF2 has a single physical flow meter
 *  shared across both valve outputs (confirmed via zigbee-herdsman-converters: unlike the per-channel
 *  irrigation duration attribute, the volume attributes have no per-endpoint declaration there) - so
 *  those remain on the parent device only.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  ver. 1.0.0 2026-07-10 bdwilson - initial version, created for SONOFF SWV-ZF2 dual-port valve
 *  ver. 1.1.0 2026-07-11 bdwilson - added per-port irrigation attributes (irrigationStartTime, irrigationEndTime,
 *                                  lastIrrigationDuration, irrigationVolume, lastValveOpenDuration, waterConsumed);
 *                                  replaces Hubitat's built-in Generic Component Switch, which couldn't hold them.
 *  ver. 1.2.0 2026-07-11 bdwilson - added valveStatus (per-channel water shortage/leakage/fail-safe fault state, decoded
 *                                  from the FC11 0x500C bitmask by the parent). No LiquidFlowRate/'rate' attribute -
 *                                  confirmed against zigbee-herdsman-converters that the ZF2 has no msFlowMeasurement
 *                                  cluster binding (unlike the classic single-port SWV); it only reports cumulative
 *                                  volume/duration (irrigationVolume, lastValveOpenDuration, waterConsumed above),
 *                                  not an instantaneous flow rate, so there is no real per-port data to expose here.
 *  ver. 1.3.0 2026-07-11 bdwilson - removed irrigationVolume/waterConsumed: zigbee-herdsman-converters confirms these
 *                                  come from a single shared flow meter (no per-endpoint declaration, unlike the
 *                                  per-channel irrigation duration attribute), so they were never really per-port
 *                                  data - they were incorrectly duplicated onto each child in v1.1.0. They remain on
 *                                  the parent device only.
 *  ver. 1.3.1 2026-07-11 bdwilson - updated importUrl to this fork's own repo/master branch (was pointing at the
 *                                  claude/tuya-zigbee-valve-dual-port-bbcxk4 working branch). This is a standalone
 *                                  fork with its own parent/child device pair, hosted and maintained independently
 *                                  rather than submitted upstream to kkossev/Hubitat.
 *  ver. 1.4.0 2026-07-11 bdwilson - open() now takes an optional duration parameter (minutes): open(30) opens this
 *                                  port for 30 minutes, overriding the port's auto-off preference on the parent for
 *                                  that one run. Forwarded to the parent's componentOpen(device, duration).
 */
static String version() { '1.4.0' }

metadata {
    definition(name: 'Tuya Zigbee Valve Port', namespace: 'bdwilson', author: 'Brian Wilson', component: true, importUrl: 'https://raw.githubusercontent.com/bdwilson/hubitat/master/Tuya-Zigbee-Valve/Tuya%20Zigbee%20Valve%20Port.groovy') {
        capability 'Actuator'
        capability 'Valve'
        capability 'Switch'
        capability 'Refresh'

        command 'open', [[name:'duration', type:'NUMBER', description:'Optional: open this port for the given number of minutes (overrides this port\'s auto-off preference on the parent for this run)']]

        attribute 'irrigationStartTime', 'string'
        attribute 'irrigationEndTime', 'string'
        attribute 'lastIrrigationDuration', 'string'
        attribute 'lastValveOpenDuration', 'number'
        attribute 'valveStatus', 'enum', ['normal', 'shortage', 'leakage', 'shortage, leakage', 'fail-safe', 'shortage, fail-safe', 'leakage, fail-safe', 'shortage, leakage, fail-safe']
    }

    preferences {
        input(name: 'txtEnable', type: 'bool', title: '<b>Description text logging</b>', defaultValue: true)
    }
}

void installed() { }

void updated() { }

void open(duration = null)  { parent?.componentOpen(device, duration) }
void close() { parent?.componentClose(device) }
void on()    { parent?.componentOpen(device) }
void off()   { parent?.componentClose(device) }

void refresh() { parent?.componentRefresh(device) }

void parse(String description) { log.warn 'parse(String description) not implemented - this is a component child device' }

// called by the parent driver to deliver state - a list of sendEvent-style maps
void parse(List<Map> events) {
    events.each { Map evt ->
        if (settings?.txtEnable != false && evt.descriptionText) { log.info evt.descriptionText }
        sendEvent(evt)
    }
}
