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
	importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/geofency-presence.groovy", 
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
   			section("<h1>Geofency Presence</h1>") {
            	paragraph ("Please read all the steps below in order to link your presence to a Geofency Location. This integration requires the <a href='https://www.geofency.com/'>Geofency</a> <b>iOS</b> app.")
			}
			section("<h2>1. Create Geofency Virtual Presence Devices</h2>") {
            	paragraph ("Go to <i>Devices -> Add Virtual Device</i> and create a new virtual device of type <b>Geofency Virtual Mobile Presence Device</b> corresponding to each user and location you wish to monitor within Geofency - or update your existing virtual presence devices to use this device type. You will then need to add device preference entries for each device to correspond to both the <b>user</b> and <b>location</b> that you will configure in Geofency.")
        	}
        section ("<h2>2. Select Virtual Presence Devices</h2>") {
            paragraph ("This will allow this App to control the devices you created above")
    		input "presence", "capability.presenceSensor", multiple: true, required: true
    	}
        section("<h2>3. Setup URL in Geofency App</h2>"){ 
            paragraph("Use the following as the URL for Geofency but make sure that you add <b>your</b> user info after /location/ in the URL using the same <b>user</b> you configured in your virtual device in step 1: <a href='${extUri}'>${extUri}</a>. You will also need to create a location in Geofency that matches the location configured in your device.")
            paragraph("Detailed installation instructions for Geofency can be found <a href='https://github.com/bdwilson/hubitat/tree/master/Geofency-Presence#Installation'>here</a>.")
            paragraph("If for some reason you want to use the Internal URL it would be <a href='${uri}'>${uri}</a>, however it's inaccessible from outside your home. ")
        }
		section("<h2>4. Enable Debug Mode</h2>") {
            paragraph("Debug is enabled by default and will disable automatically after 1 hour. Having debug mode enabled will enable to see your tests made within Geofency")
       		input "isDebug", "bool", title: "Enable Debug Mode", required: false, multiple: false, defaultValue: true, submitOnChange: true
    	}
        section("<h2>5. Testing your installation</h2>") {
            paragraph("To test your installation, make sure debug mode is enabled, your URL above is configured in Geofency location (under Settings -> Webhook but <b>with the addition of your user info after /location/</b>), then within Geofency create your location and name it corresponding to your location in step 1. Also make sure that HTTP Method is set to POST(JSON)")
            paragraph("Once added, use the <b>Test Connection Entry</b> items under Webhooks to test entering and existing and review your logs & virtual devices events.")
        }

    }
}

def installed() {
    log.warn "debug logging for Geofency: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
    ifDebug("Installed with settings: ${settings}")

}

def updated() {
    log.warn "debug logging for Geofency: ${state.isDebug == true}"
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
	def msg = ["Yep, this is the right URL, just put it into Geofency Web Hook, set to POST and do a test. Make sure your Geofency location name matches the device location and user (${params.user}) configured in the preferences"]
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
    //def device = devices.find { it.displayName == deviceName }
    def device = devices.find { it.currentValue("region") + "-" + it.currentValue("user") == deviceName }

   	ifDebug("event: ${event} device: ${device} location: ${location} user: ${user} deviceName: ${deviceName}")
      
 	if (location){
        if (!device) {
            def msg = ["Error: device not found. Make sure a device a with type: Geofency Virtual Mobile Presence Device exists AND is configured with the proper location and user settings."]
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
     } else {
		ifDebug("Location not found. You need to make sure you configure the name of your location on Geofency to match the settings configured in your Geofency Virtual Mobile Presence Device.")
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

def logsOff() {
    log.warn "debug logging for Geofency now disabled..."
    app.updateSetting("isDebug", [value: "false", type: "bool"])
    state.isDebug = false 
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'Geofency-Presence: ' + msg  
}
