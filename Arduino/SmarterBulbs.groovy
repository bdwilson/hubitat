/**
 *  Smarter Bulbs - https://raw.githubusercontent.com/bdwilson/hubitat/master/Arduino/SmarterBulbs.groovy 
 *
 *  Copyright 2016 Nick Sweet.
 *  Updates by Brian Wilson
 *  - only update status on init or if change (no scheduled job) - 02/20/2020
 *  - only do one update if there's a series of light changes (unschedule/schedule) - 4/16
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
 */

/************
 * Metadata *
 ************/
definition(
	name: "Smarter Bulbs",
	namespace: "smartthings",
	author: "nick@sweet-stuff.cc",
	description: "Save the state of a bunch of bulbs and reset when 'Canary' bulb turns on",
	category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Arduino/SmarterBulbs.groovy",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld@2x.png"
)
preferences {
    section("Canary Bulb") {
        input "canary", "capability.switch", title: "Who sings?"
    }
    section("Zigbee bulbs to monitor") {    
        input "slaves","capability.switch", multiple: true
    }
    section("Logs") {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

def installed() {
    log.debug "SmarterBulbs: Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "SmarterBulbs: Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.warn "SmarterBulbbs: Debug logging is: ${logEnable == true}"
    if (logEnable) runIn(3600, logsOff)
    subscribe(slaves, "switch", saveStates)
    subscribe(canary,"switch.on", checkRestore)
    checkStates()
}

def checkStates() {
    canary.refresh()
    def lightsOff = [:]
    slaves?.each {
			//if (it.currentSwitch == "off"){
        	    //log.debug "${it.id} value ${it.currentSwitch}" 
                if (logEnable) log.debug "SmarterBulbs: Saving State for ${it.displayName} (${it.id}) = ${it.currentSwitch}" 
        	    lightsOff[it.id]=it.currentSwitch
        	//}
	}
   	state.lOff = lightsOff   
}
    
       
def saveStates(evt) {
    if (canary.currentSwitch == "off") {  // only keep track of state if canary is off
            unschedule(checkStates) // unschedule + runIn combo keeps you from having 
            runInMillis(5000, checkStates)  // each light re-checked if 4 or 5 lights go off at once
        //state.lOff[evt?.deviceId]=evt.value
        //log.debug "SmarterBulbs: Saving State for ${evt.displayName} (${evt?.deviceId}) = ${evt.value}" 
    } else {
       log.info "SmarterBulbs: Found canary bulb on while saving states; doing a restore."
       unschedule(checkRestore)
       runInMillis(5000, checkRestore)
       //checkRestore()
    }
}

def checkRestore(evt) {
    if ("on" == canary.currentSwitch) { 
    	log.info "SmarterBulbs: Canary came on; restoring off states."
        restoreState()
        pauseExecution(200)
        log.info "SmarterBulbs: Turning off canary"
        canary.off()
    } 
}

private restoreState() {
  slaves?.each {
      if (state.lOff[it.id] == "off") { 
    	log.info "SmarterBulbs: Turning $it.label off based on previous state"
        //pauseExecution(200)
		it.off()
    }
  }
}
