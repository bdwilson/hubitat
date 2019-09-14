/**
 *  Geofency API Presence App
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
    name: "Geofency Multi-User API Presence App",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Geofency API Presence App",
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
 	def uri = getFullLocalApiServerUrl() + "/location/?access_token=${state.accessToken}"
    def extUri = fullApiServerUrl() + "/?access_token=${state.accessToken}"
    extUri = extUri.replaceAll("null","location")
    return dynamicPage(name: "setupScreen", uninstall: true, install: true){
		section ("Allow external service to control these things...") {
    		input "presence", "capability.presenceSensor", multiple: true, required: true
    	}
        section(){ 
            paragraph("Use the following as the base URI for Geofency; but follow instructions: <a href='${extUri}'>${extUri}</a>. If for some reason you want to use the Internal URI it would be <a href='${uri}'>${uri}</a>, however it's inaccessible from outside your home. ")
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

def correctURL () {
	def msg = ["Yep, this is the right URL, just put it into Geofency Web Hook, set to POST and do a test. Make sure your Geofency location name matches the device '<location>-${params.user}'"]
	ifDebug("${msg}")
	return msg
}

def validCommandsg() {
	def msg = ["Valid Commands: GET:/listLocations, POST:/location/<user>, GET:/location/<user> (to verify correct URL only)."]
	ifDebug("${msg}")
	return msg
}

def validCommandsp() {
	def msg = ["You're missing a user: /location/<user>?access_token=....."]
	ifDebug("${msg}")
	return msg
}

void updateLocation() {
    update(presence)
}

def update (devices) {
   	def data = request.JSON
   	def location = data.name
   	def event = data.entry
   	def user = params.user
   	def deviceName = location + "-" + user
    def device = devices.find { it.displayName == deviceName }
   	ifDebug("event: ${event} device: ${device} location: ${location} user: ${user} deviceName: ${deviceName}")
      
 	if (location){
        if (!device) {
			def msg = ["Error: device (${deviceName}) not found. Make sure a device a virtual mobile presense device exists with Device Name of: ${deviceName} if you expect this to work."]
			ifDebug("${msg}")
            // render's aren't working. maybe someone can fix this. 
            // render contentType: "text/html", msg: out, status: 404
        } else {
            if(event == "0"){
                def msg = "${user} has exited ${location} - turning ${device} off"
                ifDebug("${msg}")
                device.off();
                //render contentType: "text/html", data: msg, status: 200
            } else {
                def msg = "${user} has entered ${location} - turning ${device} on"
                ifDebug("${msg}")
                device.on();
                //render contentType: "text/html", msg: out, status: 200
            }
        }
     }
}

mappings {
	path("/") {
		action: [
			GET: "validCommandsg"
		]
	}
    path("/listLocations") {
        action: [
            GET: "listLocations"
        ]
    }
	path("/location") {
		action: [
            POST: "validCommandsp",
			GET: "validCommandsg"
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
    if (msg && state.isDebug)  log.debug 'Geofency-MultiUser-Presence: ' + msg  
}

