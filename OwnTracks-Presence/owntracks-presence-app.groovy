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
 *			1.1.3.7: Added history attribute to store the last 10 locations. 
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
    version: "2.0.0",
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
            paragraph ("<i>Coming from a version before 2.0?</i> You can skip device creation in step 1 and instead select your pre-existing devices in step 2.")
	}
	section("<h2>1. Create an OwnTracks Virtual Presence Device</h2>") {
            paragraph ("<b>Quick Setup:</b> enter the region/location and user you'll configure in OwnTracks below, then click <b>Create Device</b>. This creates a new <b>OwnTracks Virtual Mobile Presence Driver</b> device and configures it for you. Devices created this way are automatically usable by this app right away - you don't need to select them in step 2 below.")
            input "newLocation", "text", title: "Location/Region to Track (e.g. Home)", required: false, submitOnChange: true
            input "newUser", "text", title: "User to Track (e.g. Brian)", required: false, submitOnChange: true
            input "createDeviceBtn", "button", title: "Create Device"
            if (state.createMessage) {
                paragraph "<b>${state.createMessage}</b>"
                state.remove("createMessage")
            }
            paragraph ("<i>Prefer to do it yourself, or already have a virtual presence device?</i> Go to <i>Devices -> Add Virtual Device</i> and create a new virtual device of type <b>OwnTracks Virtual Mobile Presence Driver</b> corresponding to each user and region/location you wish to monitor within OwnTracks - or update your existing virtual presence devices to use this device type. You will then need to add device preference entries for each device to correspond to both the <b>user</b> and <b>region/location</b> that you will configure in OwnTracks. Devices created this way need to be selected in step 2 below.")
            paragraph ("<b>Upgrading from a version before 2.0?</b> If you already had virtual presence devices set up before Quick Setup existed (2.0), you may skip creation above and instead select your pre-existing devices in <b>step 2</b> below (\"Select Additional Virtual Presence Devices\") - they are not created as child devices of this app, so make sure they're still selected there. Check there if a device you were relying on before stops working after upgrading.")
        }
        section ("<h2>2. Select Additional Virtual Presence Devices</h2>") {
            paragraph ("Devices you created with <b>Quick Setup</b> in step 1 are already usable and don't need to be selected here. Use this only for devices you created yourself outside this app (or with a version before 2.0). If you select devices that are not <b>OwnTracks Virtual Mobile Presence Driver</b> devices, they will not work - the instructions in step 3 below will tell you this.")
    		input "presence", "capability.presenceSensor", multiple: true, required: false, submitOnChange: true
    	}
        section("<h2>3. Setup URL in OwnTracks App</h2>"){
            paragraph("Listed below are each of your regions/locations that you'll need to configure in OwnTracks and the URL to use for each person.")
        }
        def allDevices = getAllPresenceDevices()
        if (allDevices) {
            def configured = allDevices.findAll { it.currentValue("region")?.trim() && it.currentValue("user")?.trim() }.sort { it.currentValue("user") }
            def unconfigured = allDevices.findAll { !(it.currentValue("region")?.trim() && it.currentValue("user")?.trim()) }
            def entryNum = 0
            configured.each { d ->
                entryNum++
                def u = d.currentValue("user").trim()
                def loc = d.currentValue("region").trim()
                def perUserUri = extUri.replace("?access_token=", "${java.net.URLEncoder.encode(u, 'UTF-8')}?access_token=")
                section("OwnTracks Presence Entry ${entryNum}: ${loc} - ${u}", hideable: true, hidden: false) {
                    paragraph("&bull; <b>Device:</b> ${d.displayName}<br>&bull; <b>Region/Location:</b> ${loc}<br>&bull; <b>User:</b> ${u}<br>&bull; <b>URL:</b> <a href='${perUserUri}'>${perUserUri}</a>")
                    paragraph("<b>In the OwnTracks app (on ${u}'s phone):</b><br>" +
                        "&bull; Go to <b>Regions</b>. Create or select a Region named <b>${loc}</b> (or an iBeacon), adjusting the radius if needed, to match this device.<br>" +
                        "&bull; Within that Region, tap <b>i</b> -> Settings. Set mode to <b>HTTP</b> - this clears any regions/friends set up under MQTT. Set Username to <b>${u}</b>, disable authentication, and enter the URL above into the URL field (you may need to tap <b>Continue</b> for the URL change to take effect).<br>" +
                        "&bull; There's no dedicated test button in OwnTracks. With Debug Mode enabled below, the most reliable check is <b>Send debug information</b> in the OwnTracks app, which pushes an update to the endpoint immediately - watch Hubitat's Logs and the '${d.displayName}' device's events to confirm it arrived. Toggling between <b>significant</b> and <b>move</b> a couple of times works too, but is less immediate.")
                }
            }
            unconfigured.each { d ->
                entryNum++
                section("OwnTracks Presence Entry ${entryNum}: ${d.displayName} (NOT CONFIGURED)", hideable: true, hidden: false) {
                    paragraph("<font color='red'><b>This selected device is not usable yet</b> - it has no Region/Location and/or User set, so no webhook URL can be generated for it. Either it is not an <b>OwnTracks Virtual Mobile Presence Driver</b> device, or its <b>Location/Region to Track</b>/<b>User to Track</b> preferences have not been saved yet. Open the device, set both, and click Save Preferences (or re-create it with Quick Setup in step 1, or de-select it in step 2 above if you don't intend to use it).</font>")
                }
            }
        }
        section(""){
            paragraph("Detailed installation instructions for OwnTracks can be found <a href='https://github.com/bdwilson/hubitat/tree/master/OwnTracks-Presence#configure'>here</a>.")
            paragraph("If for some reason you want to use the Internal URL it would be <a href='${uri}'>${uri}</a> however it's inaccessible from outside your home. ")
        }
	section("<h2>4. Enable Debug Mode</h2>") {
            paragraph("Debug is enabled by default and will disable automatically after 1 hour. Having debug mode enabled will allow your presence device(s) status to be updated without leaving/entering your regions - this will aid in making sure things are working correctly but would generate unnecessary presence updates if enabled long-term.")
       		input "isDebug", "bool", title: "Enable Debug Mode", required: false, multiple: false, defaultValue: true, submitOnChange: true
    	}
        section("<h2>5. Testing your installation</h2>") {
            paragraph("To test your installation, make sure debug mode is enabled, then follow the testing step in each entry above (you probably want to leave OwnTracks on <b>significant</b> long-term because of battery life, but can review what these settings do <a href='https://owntracks.org/booklet/features/location'>here</a>). Review your logs & virtual devices events.")
            paragraph("<b>NOTE:</b> Not all fields (battery, battery status, SSID, BSSID) will be available from all devices - this is a limitation with OwnTracks.")
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "createDeviceBtn") {
        createPresenceDevice()
    }
}

private void createPresenceDevice() {
    state.createMessage = null
    def loc = newLocation?.trim()
    def usr = newUser?.trim()
    if (!loc || !usr) {
        state.createMessage = "Please enter both a Location/Region and a User above, then click Create Device again."
        return
    }
    def dni = childDni(loc, usr)
    if (getChildDevice(dni)) {
        state.createMessage = "A device for region/location '${loc}' and user '${usr}' already exists."
        return
    }
    def label = "OwnTracks - ${loc} - ${usr}"
    def child
    try {
        child = addChildDevice("brianwilson-hubitat", "OwnTracks Virtual Mobile Presence Driver", dni, null,
            [name: "OwnTracks Virtual Mobile Presence Driver", label: label, completedSetup: true])
    } catch (e) {
        state.createMessage = "Error creating device: ${e.message}. Make sure 'OwnTracks Virtual Mobile Presence Driver' is installed under Drivers Code."
        return
    }
    child.updateSetting("region", [value: loc, type: "text"])
    child.updateSetting("user", [value: usr, type: "text"])
    child.updated()
    app.updateSetting("newLocation", [value: "", type: "text"])
    app.updateSetting("newUser", [value: "", type: "text"])
    state.createMessage = "Created device '${label}'. It's ready to use - see step 3 below for its webhook URL."
}

private String childDni(String loc, String usr) {
    def safeLoc = loc.toLowerCase().replaceAll(/[^a-z0-9]+/, "-").replaceAll(/(^-+|-+$)/, "")
    def safeUser = usr.toLowerCase().replaceAll(/[^a-z0-9]+/, "-").replaceAll(/(^-+|-+$)/, "")
    return "owntracks-${safeLoc}-${safeUser}-${app.id}"
}

// Devices this app can control: everything it created itself via Quick Setup
// (owned as child devices, no selection needed) plus anything manually picked
// in step 2, de-duplicated in case a device somehow ends up in both.
private List getAllPresenceDevices() {
    def children = getChildDevices() ?: []
    def childIds = children*.id as Set
    def selected = (presence ?: []).findAll { !childIds.contains(it.id) }
    return children + selected
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
    getAllPresenceDevices().each {
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
    update(getAllPresenceDevices())
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
		          def msg = ["Error: device (${deviceName}) not found. Make sure a device a with type: OwnTracks Virtual Mobile Presence Driver exists AND is configured with the proper region and user settings."]
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
              ifDebug("Location not found. You need to make sure you configure the name of your region on OwnTracks to match the settings configured in your OwnTracks Virtual Mobile Presence Driver device.")
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
                     // keep location history
                     def history = myDevice.currentValue("history")
                     // add current history to front of list
                     // format: lat,lng,date
                     def dt = new Date().format("yyyyMMddHHmmss")
                     history = "${lat},${lon},${dt}\n" + history
                     // only save last 10 locations
                     def historyArr = history.split('\n')
                     if (historyArr.length >= 10) {
                         history = ""
                         for(int i = 0; i < 10; i++) {
                             if (i > 0) history += "\n"
                             history += historyArr[i]
                         }                         
                     }
                     myDevice.sendEvent(name: "history", value: history)
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


