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
 *  Version   1.1.2: bdwilson - initial version tracking. 
 *          1.1.3.1: bdwilson - moved location/user prefs to device preferences (thanks @cjkeenan)
 *                   updated instructions page, renamed device driver.
 * 			1.1.3.3: Updated attribute types from TEXT to STRING
 * 			1.1.3.4: Added lat/lon attributes
 *          1.1.3.5: Check for null requests
 *          1.1.3.6: Added lastUpdated attribute
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
            paragraph ("Please read all the steps below in order to link your presence to an OwnTracks Region. This integration requires the <a href='https://owntracks.org/'>OwnTracks</a> app.")
	}
	section("<h2>1. Create OwnTracks Virtual Presence Devices</h2>") {
            paragraph ("Go to <i>Devices -> Add Virtual Device</i> and create a new virtual device of type <b>OwnTracks Virtual Mobile Presence Device</b> corresponding to each user and region/location you wish to monitor within OwnTracks - or update your existing virtual presence devices to use this device type. You will then need to add device preference entries for each device to correspond to both the <b>user</b> and <b>region/location</b> that you will configure in OwnTracks.")
        }
        section ("<h2>2. Select Virtual Presence Devices</h2>") {
            paragraph ("This will allow this App to control the devices you created above")
    		input "presence", "capability.presenceSensor", multiple: true, required: true
    	}
        section("<h2>3. Setup URL in OwnTracks App</h2>"){ 
            paragraph("Use the following as the URL for OwnTracks but make sure that you add <b>your</b> user info after /location/ in the URL using the same <b>user</b> you configured in your virtual device in step 1: <a href='${extUri}'>${extUri}</a>. You will also need to create a region in OwnTracks that matches the region/location configured in your device.")
            paragraph("Detailed installation instructions for OwnTracks can be found <a href='https://github.com/bdwilson/hubitat/tree/master/OwnTracks-Presence#configure'>here</a>.  When you change from HTTP mode from MQTT mode, you will lose your regions & any features you use that take advantage of MQTT (like Friends tracking).")
            paragraph("If for some reason you want to use the Internal URL it would be <a href='${uri}'>${uri}</a>, however it's inaccessible from outside your home. ")
        }
	section("<h2>4. Enable Debug Mode</h2>") {
            paragraph("Debug is enabled by default and will disable automatically after 1 hour. Having debug mode enabled will allow your presence device(s) status to be updated without leaving/entering your regions - this will aid in making sure things are working correctly but would generate unnecessary presence updates if enabled long-term.")
       		input "isDebug", "bool", title: "Enable Debug Mode", required: false, multiple: false, defaultValue: true, submitOnChange: true
    	}
        section("<h2>5. Testing your installation</h2>") {
            paragraph("To test your installation, make sure debug mode is enabled, your URL above is configured in OwnTracks (<b>with the addition of your user info after /location/</b>), then within OwnTracks create your region and name it corresponding to your region/location in step 1.")
            paragraph("Once created, go back and forth between <b>significant</b> and <b>move</b> a few times and review your logs & virtual devices (you probably want to leave this on <b>significant</b> long-term because of battery life, but can review what these settings do <a href='https://owntracks.org/booklet/features/location'>here</a>). Device presence status should update to reflect correct presence for your devices.")
            paragraph("<b>NOTE:</b> Not all fields (battery, battery status, SSID, BSSID) will be available from all devices - this is a limitation with OwnTracks.")
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
	def msg = ["This is the right URL! Add it directly into the OwnTracks URL field and make sure your virtual presence device is configured with the the location/region and user (${params.user}) within the device preferences."]
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

def updateLocation() {
    update(presence)
}

def update (devices) {
  if (request.body) { 
    ifDebug("DBGREQ: ${request}")
    ifDebug("DBGREQBODY: ${request.body}")
    data = parseJson(request.body)
    ifDebug("DATA: ${data} PARAMS: ${params}")
    if (data._type == "transition") {
	    // https://owntracks.org/booklet/tech/json/#_typetransition
	    ifDebug("Received transition event")
	    def event = data.event
	    def user = params.user
	    def location = data.desc 
	    def deviceName = location + "-" + user
	    def device = devices.find { it.currentValue("region") + "-" + it.currentValue("user") == deviceName }
	    ifDebug("event: ${event} device: ${device} location: ${location} user: ${user} deviceName: ${deviceName}")     
	    if (location) {
              if (!device) {
		          def msg = ["Error: device (${deviceName}) not found. Make sure a device a with type: OwnTracks Virtual Mobile Presence Device exists AND is configured with the proper region and user settings."]
		          ifDebug("${msg}")
              } else {
                  lastUpdated(device)
                  if (event == "leave") {
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
              ifDebug("Location not found. You need to make sure you configure the name of your region on OwnTracks to match the settings configured in your OwnTracks Virtual Mobile Presence Device.")
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
          def lat = data.lat ?: 0.0
          def lon = data.lon ?: 0.0
          devices?.each { myDevice -> 
                 def name = myDevice.displayName
                 def DNI = myDevice.deviceNetworkId
                 ifDebug("Found device: ${name} with DNI ${DNI}")
		         def myLocation = myDevice.currentValue("region")
		         def myUser = myDevice.currentValue("user")
		         if (!myLocation) {
			         log.warn "OwnTracks Device ${name} does not have a region/location configured. Please configure it in device settings"
		         }
		         if (!myUser) {
			         log.warn "OwnTracks Device ${name} does not have a user configured. Please configure it in device settings"
		         }
                 def found = 0
                 ifDebug("MyUser: ${myUser} MyLocation: ${myLocation}")
                 if (myUser == user) {
                     lastUpdated(myDevice)
                     myDevice.sendEvent(name: "battery", value: "${batt}")
                     myDevice.sendEvent(name: "ssid", value: "${ssid}")
                     myDevice.sendEvent(name: "bssid", value: "${bssid}")
                     myDevice.sendEvent(name: "batteryStatus", value: "${batteryStatus}")
                     myDevice.sendEvent(name: "lat", value: lat)
                     myDevice.sendEvent(name: "lon", value: lon)
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
    render contentType: "application/json", data: JsonOutput.toJson([])
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

def lastUpdated(device) {
    def date = new Date()
    date = date.format("MM/dd/yyyy HH:mm:ss")
    device.sendEvent(name: "lastUpdated", value: date)
}

def logsOff() {
    log.warn "debug logging for OwnTracks now disabled..."
    app.updateSetting("isDebug", [value: "false", type: "bool"])
    state.isDebug = false 
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'OwnTracks-Presence: ' + msg  
}


