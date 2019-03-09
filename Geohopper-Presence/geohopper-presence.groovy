/**
 *  Geohopper API Presence App
 *
 *  Copyright 2015 Brian Wilson
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
definition(
    name: "Geohopper Multi-User API Presence App",
    namespace: "bw",
    author: "Brian Wilson",
    description: "Geohopper API Presence App",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true)


preferences {
  page(name: "setupScreen")
}

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def setupScreen(){
    state.isDebug = isDebug
    if(!state.accessToken){	
        //enable OAuth in the app settings or this call will fail
        createAccessToken()	
    }
    def uri = getFullLocalApiServerUrl() + "/?access_token=${state.accessToken}"
	def extUri = fullApiServerUrl() + "/?access_token=${state.accessToken}"
    return dynamicPage(name: "setupScreen", uninstall: true, install: true){
		section ("Allow external service to control these things...") {
    		input "presence", "capability.presenceSensor", multiple: true, required: true
    	}
        section(){ 
            paragraph("Use the following URI to access the page: <a href='${uri}'>${uri}</a> or <a href='${extUri}'>${extUri}</a> for external access.")
        }
	    section("") {
       		input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
    	}
    }
}

def installed() {
	ifDebug("Installed with settings: ${settings}")
}

def updated() {
	ifDebug("Updated with settings: ${settings}")
}

def listLocations() {
    def resp = []
    presence.each {
      ifDebug("RECEIVED: ${it.displayName}, attribute ${it.name}, ID: ${it.id}")
      resp << [Name: it.displayName, ID: it.id]
    }
    return resp
}

def deviceHandler(evt) {}

void correctURL () {
	 httpError(200, "Yep, this is the right URL. But do a POST to /location/${params.user}")
}

void validCommands() {
	 httpError(200, "Valid Commands: /listLocations, /location/<user>")
}

void updateLocation() {
    update(presence)
}

private void update (devices) {
   	def data = request.JSON
   	def location = data.location
   	def event = data.event
   	def user = params.user
   	def deviceName = location + "-" + user
    def device = devices.find { it.displayName == deviceName }
   	ifDebug("event: ${event} device: ${device} location: ${location} user: ${user} deviceName: ${deviceName}")
   
   	if (location){
        if (!device) {
        	ifDebug("log.debug "Error: device (${device}) not found")
            httpError(404, "Device not found")
     	} else {
            if(event == "LocationExit"){
            	logDebug("Turning ${device} off")
                device.off();
            } else { 
            	logDebug("Turning ${device} on")
                device.on();
            }
        }
    }
}

mappings {
	path("/") {
		action: [
			GET: "validCommands"
		]
	}
    path("/listLocations") {
        action: [
            GET: "listLocations"
        ]
    }
    path("/location/:user") {
    	action: [
            POST: "updateLocation",
            GET: "correctURL"
        ]
    }
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'GeoHopper-MultiUser-Presence' + msg  
}
