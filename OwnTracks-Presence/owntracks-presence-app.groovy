/**
 *  OwnTracks Presence App
 *
 *  Copyright 2020 Brian Wilson
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
    name: "OwnTracks Presence",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "OwnTracks Presence ",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/owntracks-presence-app.groovy",
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
 	def uri = getFullLocalApiServerUrl() + "/location/[USER]?access_token=${state.accessToken}"
    def extUri = fullApiServerUrl() + "/[USER]?access_token=${state.accessToken}"
    extUri = extUri.replaceAll("null","location")
    return dynamicPage(name: "setupScreen", uninstall: true, install: true){
        section() {
            paragraph("Before the step below, go add virtual presence devices for the users you wish to monitor with OwnTracks - name them in the format [Location]-[Name] (i.e. Home-Bob).")
        }
        section ("Allow this app to control these virtual presence devices:") {
    		input "presence", "capability.presenceSensor", multiple: true, required: true
    	}
        section(){ 
            paragraph("Use the following as the base URL for OwnTrack; but make sure the Region Name and that you adjust the [USER] in the URL with the correct user matching your virtual device. <a href='${extUri}'>${extUri}</a>. If for some reason you want to use the Internal URL it would be <a href='${uri}'>${uri}</a>, however it's inaccessible from outside your home. ")
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
	def msg = ["Yep, this is the right URL, just put it into OwnTracks URL. Make sure your Device name matches the format: '[Region Name in OwnTracks]-[UserID From OwnTracks]'"]
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
    data = parseJson(request.body)
    ifDebug("DATA: ${data}")
    if (data._type == "transition") {
          ifDebug("Received transition event")
   	      def event = data.event
   	      def user = params.user
          def location = data.desc  
          def deviceName = location + "-" + user
          def device = devices.find { it.displayName == deviceName }
   	      ifDebug("event: ${event} device: ${device} location: ${location} user: ${user} deviceName: ${deviceName}")     
 	      if (location){
              if (!device) {
			      def msg = ["Error: device (${deviceName}) not found. Make sure a device a virtual mobile presense device exists with Device Name of: ${deviceName} if you expect this to work."]
			      ifDebug("${msg}")
              } else {
                  if(event == "leave"){
                      def msg = "${user} has exited ${location} - turning ${device} off"
                      ifDebug("${msg}")
                      device.off()
                 } else {
                     def msg = "${user} has entered ${location} - turning ${device} on"
                     ifDebug("${msg}")
                     device.on()
                 }
             }
          } else {
               ifDebug("Location not found. You need to make sure you configure the name of your region on OwnTracks to have a location name to match your device name and person in hubitat. It should be named in the format of Location-${params.user}.")
          }
     } else if (data._type == "location") {
          ifDebug("Received location event")
          def batt = data.batt
   	      def user = params.user
          def ssid = data.SSID
          def bssid = data.BSSID
          devices?.each { myDevice -> 
                 def name = myDevice.displayName
                 def DNI = myDevice.deviceNetworkId
                 //ifDebug("Found device: ${name} with DNI ${DNI}")
                 def myLocation = name.split('-')[0]
                 def myUser = name.split('-')[1]
                 def found = 0
                 //ifDebug("MyUser: ${myUser} MyLocation: ${myLocation}")
                 if (myUser == user) {
                     myDevice.sendEvent(name: "battery", value: "${batt}")
                     myDevice.sendEvent(name: "ssid", value: "${ssid}")
                     myDevice.sendEvent(name: "bssid", value: "${bssid}")
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
			GET: "correctURL"
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
    if (msg && state.isDebug)  log.debug 'OwnTracks-MultiUser-Presence: ' + msg  
}
