/**
 *  Smarter Bulbs - https://raw.githubusercontent.com/bdwilson/hubitat/master/Arduino/SmarterBulbs.groovy
 *
 *  Copyright 2016 Nick Sweet.
 *  Updates by Brian Wilson for Hubitat
 *  - only update status on init or if change (no scheduled job) - 02/20/2020
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
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld.png",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Arduino/SmarterBulbs.groovy", 
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld@2x.png"
)
preferences {
    section("Canary Bulb") {
        input "canary", "capability.switch", title: "Who sings?"
    }
    section("Zigbee bulbs to monitor") {    
        input "slaves","capability.switch", multiple: true
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(slaves, "switch", saveStates)
    subscribe(canary,"switch.on", checkRestore)
    //canary.refresh() // no need to refresh canary as we'll do this via ESP12 
    initStates()

    
}

def initStates() {
    def lightsOff = [:]
    slaves?.each {
			if (it.currentSwitch == "off"){
        	    //log.debug "${it.id} value ${it.currentSwitch}" 
        	    lightsOff[it.id]="off"
        	}
	}
   	state.lOff = lightsOff   
}
    
       
def saveStates(evt) {
    if (canary.currentSwitch == "off") {  // only keep track of state if canary is off
        state.lOff[evt?.deviceId]=evt.value
        //log.debug "Saving State for ${evt.displayName} (${evt?.deviceId}) = ${evt.value}" 
    }
}

def checkRestore(evt) {
    if ("on" == canary.currentSwitch) { 
    	log.info "Canary came on; restoring off states."
        restoreState()
        pauseExecution(200)
        canary.off()
        }
}

private restoreState() {
  slaves?.each {
      if (state.lOff[it.id] == "off") { 
    	log.info "Turning $it.label off based on previous state"
		it.off()
    }
  }
}
