/**
 *  Aeon HEM1
 *   
 * https://community.hubitat.com/t/power-monitoring-not-working-so-well/3182/15
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
 *  Aeon Home Energy Meter v1 (US)
 *
 */
metadata {
    definition (name: "My Aeon Home Energy Monitor v3", namespace: "jscgs350", author: "SmartThings") 
	{
    	capability "Energy Meter"
    	capability "Power Meter"
    	capability "Configuration"
    	capability "Sensor"
    	capability "Refresh"
    	capability "Polling"
    	capability "Battery"
    
    	attribute "energy", "string"
    	attribute "energyDisp", "string"
    	attribute "energyOne", "string"
    	attribute "energyTwo", "string"
    
    	attribute "power", "string"
    	attribute "powerDisp", "string"
    	attribute "powerOne", "string"
    	attribute "powerTwo", "string"
    
    	command "reset"
    	command "configure"
    	command "resetmaxmin"
    
    fingerprint deviceId: "0x2101", inClusters: " 0x70,0x31,0x72,0x86,0x32,0x80,0x85,0x60"

}

	preferences {
		input "kWhCost", "string", title: "\$/kWh (0.16)", defaultValue: "0.16" as String, displayDuringSetup: true
	}
}


def parse(String description) {
    //log.debug "Parse received ${description}"
	def result = []
	//log.debug "desc: ${description}"
    def cmd = zwave.parse(description, [0x31: 1, 0x32: 1, 0x60: 3])
    if (cmd) {
        result << createEvent(zwaveEvent(cmd))
    }
    //if (result) log.debug "Parse returned ${result}"
    def statusTextmsg = ""
   // statusTextmsg = "Min was ${device.currentState('powerOne')?.value}\nMax was ${device.currentState('powerTwo')?.value}"
   // sendEvent("name":"statusText", "value":statusTextmsg)
    //log.debug statusTextmsg
    return result
}

def zwaveEvent(hubitat.zwave.commands.meterv1.MeterReport cmd) {
    //log.debug "zwaveEvent received ${cmd} ${cmd.payload} ${cmd.format()}"
    def dispValue
    def newValue
    def timeString = new Date().format("yyyy-MM-dd h:mm a", location.timeZone)
    if (cmd.meterType == 33) {
        if (cmd.scale == 0) {
            newValue = cmd.scaledMeterValue
            //log.debug state
            if (newValue != state.energyValue) {
                dispValue = String.format("%5.2f",newValue)+"\nkWh"
                sendEvent(name: "energyDisp", value: dispValue as String, unit: "")
                state.energyValue = newValue
                BigDecimal costDecimal = newValue * ( kWhCost as BigDecimal)
                def costDisplay = String.format("%3.2f",costDecimal)
                sendEvent(name: "energyTwo", value: "Cost\n\$${costDisplay}", unit: "")
                [name: "energy", value: newValue, unit: "kWh"]
            }
        } else if (cmd.scale == 1) {
            newValue = cmd.scaledMeterValue
            if (newValue != state.energyValue) {
                dispValue = String.format("%5.2f",newValue)+"\nkVAh"
                sendEvent(name: "energyDisp", value: dispValue as String, unit: "")
                state.energyValue = newValue
                [name: "energy", value: newValue, unit: "kVAh"]
            }
        }
        else if (cmd.scale==2) {                
            newValue = Math.round( cmd.scaledMeterValue )       // really not worth the hassle to show decimals for Watts
            if (newValue != state.powerValue) {
                dispValue = newValue+"w"
                sendEvent(name: "powerDisp", value: dispValue as String, unit: "")
                if (newValue < state.powerLow) {
                    dispValue = newValue+"w"+" on "+timeString
                    sendEvent(name: "powerOne", value: dispValue as String, unit: "")
                    state.powerLow = newValue
                }
                if (newValue > state.powerHigh) {
                    dispValue = newValue+"w"+" on "+timeString
                    sendEvent(name: "powerTwo", value: dispValue as String, unit: "")
                    state.powerHigh = newValue
                }
                state.powerValue = newValue
                [name: "power", value: newValue, unit: "W"]
            }
        }
    }
}

def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    def map = [:]
    map.name = "battery"
    map.unit = "%"
    if (cmd.batteryLevel == 0xFF) {
        map.value = 1
        map.descriptionText = "${device.displayName} has a low battery"
        map.isStateChange = true
    } else {
        map.value = cmd.batteryLevel
    }
    //log.debug map
    return map
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    //log.debug "Unhandled event ${cmd}"
    [:]
}

def refresh() {
    log.debug "Refreshed ${device.name}"
    def cmds = delayBetween([
    zwave.meterV2.meterGet(scale: 0).format(),
    zwave.meterV2.meterGet(scale: 2).format()
	])
log.debug "refresh cmds ${cmds}"
cmds
}

def poll() {
    refresh()
}

def reset() {
    log.debug "${device.name} reset kWh/Cost values"
    state.powerHigh = 0
	state.powerLow = 99999

	def timeString = new Date().format("yyyy-MM-dd h:mm a", location.timeZone)
    sendEvent(name: "energyOne", value: "Energy Data (kWh/Cost) Reset On:\n"+timeString, unit: "")       
    sendEvent(name: "energyDisp", value: "", unit: "")
    sendEvent(name: "energyTwo", value: "Cost\n--", unit: "")

    def cmd = delayBetween( [
        zwave.meterV2.meterReset().format(),
        zwave.meterV2.meterGet(scale: 0).format(),
    	zwave.meterV2.meterGet(scale: 2).format()
    ])
    
    cmd
}

def resetmaxmin() {
    log.debug "${device.name} reset max/min values"
    state.powerHigh = 0
    state.powerLow = 99999
    
	def timeString = new Date().format("yyyy-MM-dd h:mm a", location.timeZone)
    sendEvent(name: "energyOne", value: "Watts Data (min/max) Reset On:\n"+timeString, unit: "")
    sendEvent(name: "powerOne", value: "", unit: "")    
    sendEvent(name: "powerTwo", value: "", unit: "")    

    def cmd = delayBetween( [
        zwave.meterV2.meterGet(scale: 0).format(),
    	zwave.meterV2.meterGet(scale: 2).format()
    ])
    
    cmd
}

def configure() {
    log.debug "${device.name} configuring device"
    if (kDelay == null) {               // Shouldn't have to do this, but there seem to be initialization errors
                kDelay = 300
        }

        if (dDelay == null) {
                dDelay = 30
        }

        def cmd = delayBetween([
                zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 0).format(),                      // Disable (=0) selective reporting
                zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: 50).format(),                     // Don't send whole HEM unless watts have changed by 30
                zwave.configurationV1.configurationSet(parameterNumber: 5, size: 2, scaledConfigurationValue: 50).format(),                     // Don't send L1 Data unless watts have changed by 15
                zwave.configurationV1.configurationSet(parameterNumber: 6, size: 2, scaledConfigurationValue: 50).format(),                     // Don't send L2 Data unless watts have changed by 15
        zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: 5).format(),                      // Or by 5% (whole HEM)
        zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: 5).format(),                      // Or by 5% (L1)
        zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: 5).format(),                     // Or by 5% (L2)
                zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 6145).format(),         // Whole HEM and L1/L2 power in kWh
                zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: kDelay).format(),       // Default every 120 Seconds
                zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 1573646).format(),  // L1/L2 for Amps & Watts, Whole HEM for Amps, Watts, & Volts
                zwave.configurationV1.configurationSet(parameterNumber: 112, size: 4, scaledConfigurationValue: dDelay).format(),       // Defaul every 30 seconds
                zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0).format(),            // Disable Report 3
                zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 0).format()             // eDesable Report 3
        ], 2000)
        log.debug cmd
    cmd
}
