/**
 *  Smarter Bulbs - https://raw.githubusercontent.com/nsweet68/SmartThingsPublic/master/smartapps/smartthings/smarter-bulbs.src/smarter-bulbs.groovy 
 *
 *  Copyright 2016 Nick Sweet.
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
    saveStates() 
	// changed to 10 minutes vs 5. each bulb refresh shows in logs so trying to
	// trim that down.
	runEvery10Minutes(checkRestore) 
    
}

def saveStates(evt) {
	//log.debug "Checking States"
    if ("off" == canary.currentSwitch ) {
    	def lightsOff = [:]
    	slaves?.each {
			if (it.currentSwitch == "off"){
        	//log.debug "${it.id} value ${it.currentSwitch}" 
        	lightsOff[it.id]="off"
        	}
		}
   	state.lOff = lightsOff
	}
}

def checkRestore(evt) {
    //log.debug "Checking Restore"  
    //canary.refresh() 
    //log.debug "Canary is ${canary.currentSwitch}"
    if ("on" == canary.currentSwitch) { 
    	log.debug "Turning stuff off due to power outage"
        restoreState()
        canary.off()
        }
    slaves*.refresh() // Sengled bulbs don't support poll 
    saveStates(evt)
}

private restoreState() {
  slaves?.each {
	if (state.lOff[it.id] == "off") {
    	log.debug "turning $it.label off based on previous state"
		it.off()
    }
  }
}

