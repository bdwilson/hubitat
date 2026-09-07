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
	importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/claude/geofency-presence-install-42ldx6/Geofency-Presence/geofency-presence.groovy",
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
   			section("<h1>Geofency Presence</h1>") {
            	paragraph ("Please read all the steps below in order to link your presence to a Geofency Location. This integration requires the <a href='https://www.geofency.com/'>Geofency</a> <b>iOS</b> app.")
            	paragraph ("<i>Coming from a version before 2.0?</i> You can skip device creation in step 1 and instead select your pre-existing devices in step 2.")
			}
			section("<h2>1. Create a Geofency Virtual Presence Device</h2>") {
            	paragraph ("<b>Quick Setup:</b> enter the location and user you'll configure in Geofency below, then click <b>Create Device</b>. This creates a new <b>Geofency Virtual Mobile Presence Device</b> and configures it for you. Devices created this way are automatically usable by this app right away - you don't need to select them in step 2 below.")
            	input "newLocation", "text", title: "Location to Track (e.g. Home)", required: false, submitOnChange: true
            	input "newUser", "text", title: "User to Track (e.g. Brian)", required: false, submitOnChange: true
            	input "createDeviceBtn", "button", title: "Create Device"
            	if (state.createMessage) {
            	    paragraph "<b>${state.createMessage}</b>"
            	    state.remove("createMessage")
            	}
            	paragraph ("<i>Prefer to do it yourself, or already have a virtual presence device?</i> Go to <i>Devices -> Add Virtual Device</i> and create a new virtual device of type <b>Geofency Virtual Mobile Presence Device</b> corresponding to each user and location you wish to monitor within Geofency - or update your existing virtual presence devices to use this device type. You will then need to add device preference entries for each device to correspond to both the <b>user</b> and <b>location</b> that you will configure in Geofency. Devices created this way need to be selected in step 2 below.")
            	paragraph ("<b>Upgrading from a version before 2.0?</b> If you already had virtual presence devices set up before Quick Setup existed (2.0), you may skip creation above and instead select your pre-existing devices in <b>step 2</b> below (\"Select Additional Virtual Presence Devices\") - they are not created as child devices of this app, so make sure they're still selected there. Check there if a device you were relying on before stops working after upgrading.")
        	}
        section ("<h2>2. Select Additional Virtual Presence Devices</h2>") {
            paragraph ("Devices you created with <b>Quick Setup</b> in step 1 are already usable and don't need to be selected here. Use this only for devices you created yourself outside this app (or with a version before 2.0). If you select devices that are not <b>Geofency Virtual Mobile Presence Device</b> devices, they will not work - the instructions in step 3 below will tell you this.")
    		input "presence", "capability.presenceSensor", multiple: true, required: false, submitOnChange: true
    	}
        section("<h2>3. Setup URL in Geofency App</h2>"){
            paragraph("Use the following as the URL for Geofency but make sure that you add <b>your</b> user info after /location/ in the URL using the same <b>user</b> you configured in your virtual device in step 1: <a href='${extUri}'>${extUri}</a>. You will also need to create a location in Geofency that matches the location configured in your device.")
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
                section("Geofency Presence Entry ${entryNum}: ${loc} - ${u}", hideable: true, hidden: false) {
                    paragraph("&bull; <b>Device:</b> ${d.displayName}<br>&bull; <b>Location:</b> ${loc}<br>&bull; <b>User:</b> ${u}<br>&bull; <b>URL:</b> <a href='${perUserUri}'>${perUserUri}</a>")
                    paragraph("<b>In the Geofency app:</b><br>" +
                        "&bull; Create a Geofency Location named <b>${loc}</b>.<br>" +
                        "&bull; Go into Place Settings for Location <b>${loc}</b> -> Webhook. Under URL Settings -> Entry: paste the URL above and check <b>Send Webhook</b>. Under URL Settings -> Exit: paste the same URL and check <b>Send Webhook</b>.<br>" +
                        "&bull; Set HTTP Method to <b>POST as JSON</b>.<br>" +
                        "&bull; You may then use the <b>Test Enter</b> and <b>Test Exit</b> buttons. Watch the '${d.displayName}' device in Hubitat while doing so to confirm it works.")
                }
            }
            unconfigured.each { d ->
                entryNum++
                section("Geofency Presence Entry ${entryNum}: ${d.displayName} (NOT CONFIGURED)", hideable: true, hidden: false) {
                    paragraph("<font color='red'><b>This selected device is not usable yet</b> - it has no Location and/or User set, so no webhook URL can be generated for it. Either it is not a <b>Geofency Virtual Mobile Presence Device</b>, or its <b>Location to Track</b>/<b>User to Track</b> preferences have not been saved yet. Open the device, set both, and click Save Preferences (or re-create it with Quick Setup in step 1, or de-select it in step 2 above if you don't intend to use it).</font>")
                }
            }
        }
        section(""){
            paragraph("Detailed installation instructions for Geofency can be found <a href='https://github.com/bdwilson/hubitat/tree/master/Geofency-Presence#Installation'>here</a>.")
            paragraph("If for some reason you want to use the Internal URL it would be <a href='${uri}'>${uri}</a>, however it's inaccessible from outside your home. ")
        }
		section("<h2>4. Enable Debug Mode</h2>") {
            paragraph("Debug is enabled by default and will disable automatically after 1 hour. Having debug mode enabled will enable to see your tests made within Geofency")
       		input "isDebug", "bool", title: "Enable Debug Mode", required: false, multiple: false, defaultValue: true, submitOnChange: true
    	}
        section("<h2>5. Testing your installation</h2>") {
            paragraph("To test your installation, make sure debug mode is enabled, and use the <b>Test Enter</b> / <b>Test Exit</b> buttons from each entry above. Review your logs & virtual devices events.")
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
        state.createMessage = "Please enter both a Location and a User above, then click Create Device again."
        return
    }
    def dni = childDni(loc, usr)
    if (getChildDevice(dni)) {
        state.createMessage = "A device for location '${loc}' and user '${usr}' already exists."
        return
    }
    def label = "Geofency - ${loc} - ${usr}"
    def child
    try {
        child = addChildDevice("brianwilson-hubitat", "Geofency Virtual Mobile Presence Device", dni, null,
            [name: "Geofency Virtual Mobile Presence Device", label: label, completedSetup: true])
    } catch (e) {
        state.createMessage = "Error creating device: ${e.message}. Make sure 'Geofency Virtual Mobile Presence Device' is installed under Drivers Code."
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
    return "geofency-${safeLoc}-${safeUser}-${app.id}"
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
    getAllPresenceDevices().each {
      ifDebug("RECEIVED: ${it.displayName}, attribute ${it.name}, ID: ${it.id}")
      resp << [Name: it.displayName, ID: it.id]
    }
    return resp
}

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
    update(getAllPresenceDevices())
}

def update (devices) {
   	def data = request.JSON
   	def location = data.name?.trim()
   	def event = data.entry
   	def user = params.user?.trim()
   	def deviceName = location + "-" + user
    def device = devices.find {
        (it.currentValue("region")?.trim() + "-" + it.currentValue("user")?.trim()).equalsIgnoreCase(deviceName)
    }

   	ifDebug("event: ${event} device: ${device} location: ${location} user: ${user} deviceName: ${deviceName}")

 	if (location) {
        if (!device) {
            log.error "Geofency: device not found for '${deviceName}'. Make sure a device with type: Geofency Virtual Mobile Presence Device exists AND is configured with the proper location and user settings."
        } else {
            if (event == "0") {
                log.debug "Geofency: ${user} has exited ${location} - turning ${device} off"
                device.off()
            } else if (event == "1") {
                log.debug "Geofency: ${user} has entered ${location} - turning ${device} on"
                device.on()
            } else {
                log.warn "Geofency: unexpected event value '${event}' received for ${deviceName}"
            }
        }
     } else {
        log.error "Geofency: no location in payload. Make sure your Geofency location name matches the location configured in your Geofency Virtual Mobile Presence Device."
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
