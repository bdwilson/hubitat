/**
 *  Camect Connect Child - Motion Disabler
 *  Version: 1.3.0
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
	importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-motion-disabler-child-app.groovy"
)

preferences {
    page(name: "pageConfig")
}

def pageConfig() {
    theName = app.label
    if(theName == null || theName == "") theName = "New Child App"
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
        section("How long to disable camera notifications if detection is triggered?") {
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
    unsubscribe()
	initialize()
}

def initialize() {
    
    cameras.each { camera -> 
        parent.ifDebug "CameraID's Selected: ${camera} "
    }
    
    if (presence) {
        if (settings.onPresent == true) {
	        subscribe(presence, "presence.present", genericHandler)
            parent.ifDebug "Subscribing to presence present for ${presence} "

        }
        if (settings.onNotPresent == true) {
            subscribe(presence, "presence.not present", genericHandler)
            parent.ifDebug "Subscribing to presence not present for ${presence} "
        }
    }
    if (locks) {
        if (settings.lockLock == true) {
            subscribe(locks, "lock.locked", genericHandler)
            parent.ifDebug "Subscribing to locks locked for ${locks} "

        }
        if (settings.lockUnlock == true) {
            subscribe(locks, "lock.unlocked", genericHandler)
            parent.ifDebug "Subscribing to locks unlocked for ${locks} "
        }
    }
    if (contacts) {
        if (settings.contactOpen == true) {
            subscribe(contacts, "contact.open", genericHandler)
            parent.ifDebug "Subscribing to contact open for ${contacts} "
        }
        if (settings.contactClose == true) {
            subscribe(contacts, "contact.closed", genericHandler)
            parent.ifDebug "Subscribing to contact close for ${contacts} "
        }
    }
    if (motions) {
        if (settings.motionActive == true) {
            subscribe(motions, "motion.active", genericHandler)
             parent.ifDebug "Subscribing to motion active for ${motions} "

        }
        if (settings.motionInActive == true) {
            subscribe(motions, "motion.inactive", genericHandler)
            parent.ifDebug "Subscribing to motion inactive for ${motions} "
        }
    }
}
	
def genericHandler(evt) {
	 cameras.each { camera -> 
        def paramse  = [ Enable:1, Reason:"Hubitat Motion Disabler", CamId:camera]
        def paramsd  = [ Reason:"Hubitat Motion Disabler", CamId:camera]
        parent.ifDebug("Motion Disabler App ${app.label} received an event for ${evt.displayName} with value ${evt.value}")
        parent.sendCommand('/EnableAlert', paramsd)
        runIn(settings.seconds,enableAlert,[data: [command: "/EnableAlert", camera: camera, params: paramse], overwrite:false])
    }
}

def enableAlert(data) {
    //parent.ifDebug("Sending enable for camera ${state.cameraList[data.camera]} (${data.camera})")
    parent.sendCommand(data.command,data.params)
}

def prefListDevices(title) {  
    state.cameraList = [:]
    def response = parent.sendCommand('/ListCameras')
    for (camera in response.camera) { 
        //parent.ifDebug("Camera Found: ID ${camera.id} Name: ${camera.name}")
        state.cameraList[camera.id] = camera.name
    }
    if (state.cameraList) {
        section("${title}"){
            input(name: "cameras", type: "enum", required:false, multiple:true, options:state.cameraList)
        }
    }           
}
