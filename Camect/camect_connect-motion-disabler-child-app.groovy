/**
 *  Camect Connect Child - Motion Disabler
 *  Version: 1.1.0
 */

import groovy.time.TimeCategory
import java.text.SimpleDateFormat


definition(
    name: "Camect Connect Child - Motion Disabler",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Motion Disabler",
    category: "Convenience",
	parent: "brianwilson-hubitat:Camect Connect",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
	importUrl: "https://raw.githubusercontent.com/bptworld/Hubitat/master/Apps/Simple%20Irrigation/SI-child.groovy",
)

preferences {
    page(name: "pageConfig")
}

def pageConfig() {
    
    dynamicPage(name: "", title: "", install: true, uninstall: true, refreshInterval:0) {
        section("Instructions:", hideable: false, hidden: false) {
			paragraph "Select optional sensors/locks/contacts that will be used to disable notifications on the selected camera."
		}
        section("Select cameras that will be disabled when any of the below are triggered") {
            prefListDevices("Select which cameras to use that will have motion disabled") 
        }
        section("Which presence sensors?") {
		    input "contacts", "capability.contactSensor", title: "Contact Sensors(s):", required: false, multiple: true
            input "contactOpen", "bool", title: "On contact open?", required: true, defaultValue: true
            input "contactClose", "bool", title: "On contact close?", required: true, defaultValue: true
	    }
		section("Which locks?") {
		    input "locks", "capability.lock", title: "Lock(s):", required: false, multiple: true
            input "lockUnlock", "bool", title: "On lock unlocked?", required: true, defaultValue: true
            input "lockLock", "bool", title: "On lock locked?", required: true, defaultValue: true
	    }
        section("Which motion sensors?") {
		    input "motions", "capability.motionSensor", title: "Motion(s):", required: false, multiple: true
            input "motionActive", "bool", title: "On motion active?", required: true, defaultValue: true
            input "motionInactive", "bool", title: "On motion inactive?", required: true, defaultValue: true
	    }
        section("Which presence sensors?") {
		    input "presence", "capability.presenceSensor", title: "Presence Sensors(s):", required: false, multiple: true
            input "onPresent", "bool", title: "On presence present?", required: true, defaultValue: true
            input "onNotPresent", "bool", title: "On presence not present?", required: true, defaultValue: true
	    }
        section("How long to disable camera notifications if detection is found?") {
            input "seconds", "number", title: "Number of Seconds", required: true, defaultValue: 60, width: 6
        }
        section("Enter the name of your Motion Disabler - this is probably a combination of your camera names you've selected.") {
             label title: "Name", required: true
        }
	}
}

def installed() {
    parent.ifDebug "Installed with settings: ${settings}"
	initialize()
}

def updated() {	
    parent.ifDebug "Updated with settings: ${settings}"
	unschedule()
	initialize()
}

def initialize() {
    
    //cameras.each { camera -> 
    //    parent.ifDebug "CameraID's Selected: ${camera} "
    //}
    
    if (presence) {
        if (onPresent) 
	        subscribe(presence, "presence.present", genericHandler)
        if (onNotPresent) 
            subscribe(presence, "presence.not present", genericHandler)
    }
    if (locks) {
        if (lockLock) 
            subscribe(lock, "lock.locked", genericHandler)
        if (lockUnlock) 
            subscribe(lock, "lock.unlocked", genericHandler)
    }
    if (contacts) {
        if (contactOpen)
            subscribe(contact, "contact.open", genericHandler)
        if (contactClose)
            subscribe(contact, "contact.closed", genericHandler)
    }
    if (motions) {
        if (motionActive) 
            subscribe(motions, "motion.active", genericHandler)
        if (motionInActive)
            subscribe(motions, "motion.inactive", genericHandler)
    }
}
	
def genericHandler(evt) {
	 cameras.each { camera -> 
        parent.ifDebug "CameraID's Selected in Generic Handler: ${camera} "
    //}
    // ${state.data[door].child}
    //state.cameraList.each { camera ->
        parent.ifDebug "Received an event for ${evt.displayName} with value: ${evt.value}; Sending disable to camera: ${camera} for ${settings.seconds}"
        parent.ifDebug "Camera List: ID: ${camera} Name: ${state.cameraList[camera]} "
    }
}

def prefListDevices(title) {  
    state.cameraList = [:]
    def response = parent.sendCommand('/ListCameras')
    for (camera in response.camera) { 
        parent.ifDebug("Camera Found: ID ${camera.id} Name: ${camera.name}")
        state.cameraList[camera.id] = camera.name
    }
    if (state.cameraList) {
        section("${title}"){
            input(name: "cameras", type: "enum", required:false, multiple:true, options:state.cameraList)
        }
    }           
}
