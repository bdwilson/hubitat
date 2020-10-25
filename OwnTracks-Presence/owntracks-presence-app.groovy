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
 * Version 1.1.2: bdwilson - initial version tracking. 
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
 	def uri = getFullLocalApiServerUrl() + "/location/?access_token=${state.accessToken}"
    def extUri = fullApiServerUrl() + "/?access_token=${state.accessToken}"
    extUri = extUri.replaceAll("null","location")
    return dynamicPage(name: "setupScreen", uninstall: true, install: true){
        section("<h1>OwnTracks Presence</h1>") {
            paragraph ("Before the step below, go add virtual presence devices for the users you wish to monitor with OwnTracks name them in the format [Location]-[Name] (i.e. Home-Bob) and assign the device type to be <b>Virtual Mobile Presence for Owntracks</b>. If you already have Virtual Presence Devices then you'll need to update them to use the <b>Virtual Mobile Presence for Owntracks</b> device type.")
        }
        section ("<h2>Select Virtual Presence sensors to Control</h2>") {
            //paragraph("Allow this app to control these virtual presence devices:")
    		input "presence", "capability.presenceSensor", multiple: true, required: true
    	}
        section("<h2>Setup URL in OwnTracks App</h2>"){ 
            paragraph("Use the following as the URL for OwnTracks but make sure that you add <b>your</b> User info after /location/ in the URL with the correct User matching your virtual device: <a href='${extUri}'>${extUri}</a>. You will also need to create a region in OwnTracks that matches the beginning part of your virtual device.")
            paragraph("Detailed installation instructions for OwnTracks can be found <a href='https://github.com/bdwilson/hubitat/tree/master/OwnTracks-Presence#configure'>here</a>.  When you change from HTTP mode from MQTT mode, you will lose your regions & any features you use that take advantage of MQTT (like Friends tracking).")
            paragraph("If for some reason you want to use the Internal URL it would be <a href='${uri}'>${uri}</a>, however it's inaccessible from outside your home. ")
        }
	    section("<h2>Debug Mode</h2>") {
            paragraph("Debug is enabled by default and will disable automatically after 1 hour. Having debug mode enabled will allow your presence device(s) status to be updated without leaving/entering your regions - this will aid in making sure things are working correctly but would generate unnecessary presence updates if enabled long-term.")
       		input "isDebug", "bool", title: "Enable Debug Mode", required: false, multiple: false, defaultValue: true, submitOnChange: true
    	}
        section("<h2>Testing your installation</h2>") {
            paragraph("To test your installation, make sure debug mode is enabled, your URL above is configured in OwnTracks (with the addition of your user info after /location), then within OwnTracks create your regions and give them the same names as the beginning part of your virtual presence device. ")
            paragraph("Once created, go back and forth between <b>significant</b> and <b>move</b> a few times and review your logs & virtual devices (you probably want to leave this on <b>significant</b> long-term because of battery life, but can review what these settings do <a href='https://owntracks.org/booklet/features/location'>here</a>). Device presence status should update to reflect correct presence for your devices.")
            paragraph("Not all fields (battery, battery status, SSID, BSSID) will be available from all devices - this is a limitation with OwnTracks.")
        }
    }
}

def installed() {
    log.warn "debug logging for OwnTracks: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
    ifDebug("Installed with settings: ${settings}")

}

def updated() {
    log.warn "debug logging for OwnTracks: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
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
	def msg = ["This is the right URL! Add it directly into the OwnTracks URL field and make sure your Hubitat device name matches the format: '[Region Name in OwnTracks]-${params.user}'"]
	ifDebug("${msg}")
	return msg
}

def validCommandsg() {
	def msg = ["Valid Commands: GET:/listLocations, POST:/location/<user>, GET:/location/<user> (to verify correct URL only)."]
	ifDebug("${msg}")
	return msg
}

def validCommandsp() {
	def msg = ["You're missing a user - check the directions and add a user after the /location/: /location/<user>?access_token=....."]
	ifDebug("${msg}")
	return msg
}

void updateLocation() {
    update(presence)
}

def update (devices) {
    data = parseJson(request.body)
    ifDebug("DATA: ${data} PARAMS: ${params}")
    if (data._type == "transition") {
          // https://owntracks.org/booklet/tech/json/#_typetransition
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
          // https://owntracks.org/booklet/tech/json/#_typelocation
          ifDebug("Received location event")
          def batt = data.batt ?: "0"
   	      def user = params.user
          def ssid = data.SSID ?: "N/A"
          def bssid = data.BSSID ?: "N/A"  
          def batteryStatus = "N/A"
          def regions = data.inregions
          // 0=unknown, 1=unplugged, 2=charging, 3=full 
          if (data.bs == 0) {
              batteryStatus = "unknown"
          } else if (data.bs == 1) {
              batteryStatus = "unplugged"
          } else if (data.bs == 2) {
              batteryStatus = "charging"
          } else if (data.bs == 3) {
              batteryStatus = "full"
          } 
          devices?.each { myDevice -> 
                 def name = myDevice.displayName
                 def DNI = myDevice.deviceNetworkId
                 ifDebug("Found device: ${name} with DNI ${DNI}")
                 def myLocation = name.split('-')[0]
                 def myUser = name.split('-')[1]
                 def found = 0
                 ifDebug("MyUser: ${myUser} MyLocation: ${myLocation}")
                 if (myUser == user) {
                     myDevice.sendEvent(name: "battery", value: "${batt}")
                     myDevice.sendEvent(name: "ssid", value: "${ssid}")
                     myDevice.sendEvent(name: "bssid", value: "${bssid}")
                     myDevice.sendEvent(name: "batteryStatus", value: "${batteryStatus}")
                     
                     // since location is only updated when a transition event is sent, we can force the location
                     // to be updated if debug mode is on.
                     if (state.isDebug) {
                         if (regions?.contains(myLocation)) { 
                             //ifDebug("In debug mode - updating presence if necessary for location: ${myLocation}")
                             found = 1
                         }
                         if (found) {  
                             if (myDevice.currentSwitch == "off") {
                                 ifDebug("${user} entered ${myLocation} (forced because debug mode is on - ${user} did not really transition)")
                                 myDevice.on()
                             }
                         } else {
                             if (myDevice.currentSwitch == "on") {
                                 ifDebug("${user} exited ${myLocation} (forced because debug mode is on - ${user} did not really transition)")
                                 myDevice.off()
                             }
                        }
                     }
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
			GET: "validCommandsp"
		]
	}
    path("/location/:user") {
    	action: [
            POST: "updateLocation",
            GET: "correctURL"
        ]
    }
}

def logsOff() {
    log.warn "debug logging for OwnTracks now disabled..."
    app.updateSetting("isDebug", [value: "false", type: "bool"])
    state.isDebug = false 
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'OwnTracks-Presence: ' + msg  
}

