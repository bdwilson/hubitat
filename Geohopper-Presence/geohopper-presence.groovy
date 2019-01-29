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
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Geohopper Multi-User API Presence App",
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
	// TODO: subscribe to attributes, devices, locations, etc.
}

def listLocations() {
    def resp = []
    presence.each {
      log.debug "RECEIVED: ${it.displayName}, attribute ${it.name}, ID: ${it.id}"
      resp << [Name: it.displayName, ID: it.id]
    }
    return resp
}

def deviceHandler(evt) {}

void correctURL () {
	 httpError(200, "Yep, this is the right URL.")
}

void validCommands() {
	 httpError(200, "Valid Commands: /listLocations, /location/<user>")
}

void updateLocation() {
    update(presence)
}

private void update (devices) {
   
	// worked on ST :(
	//def data = request.JSON
    //def location = data.location
    //def event = data.event
   	def jsonSlurper = new JsonSlurper()
    def plexJSON = params.findAll { key,value -> key =~ /location/ } 
	def firstKey = plexJSON.keySet().stream().findFirst().get();
	def json = jsonSlurper.parseText(firstKey)
	log.debug "json: ${json.location}"
	log.debug "json event ${json.event}"
	def location = json.location
   	def event = json.event
   	def user = params.user
   	def deviceName = location + "-" + user
   	def device = devices.find { it.displayName == deviceName }
   	log.debug "update, request: params: ${params}, data, ${data}, devices: $devices.id, $devices.displayName"
   	log.debug "event: ${event} device: ${device} location: ${location} user: ${user} deviceName: ${deviceName}"
   
   	if (location){
        if (!device) {
        	log.debug "Error: device (${device}) not found"
            httpError(404, "Device not found")
     	} else {
            if(event == "LocationExit"){
            	log.debug "Turning device off"
                device.off();
            } else { 
            	log.debug "Turning device on"
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
