/**
 Camect Connect Child - Motion Disabler
 */

import groovy.time.TimeCategory
import java.text.SimpleDateFormat

def setVersion(){
    state.name = "Simple Irrigation"
	state.version = "2.0.6"
}

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
            prefListDevices() 
        }
        section("Which presence sensors?") {
		    input "contacts", "capability.contactSensor", title: "Contact Sensors(s):", required: false, multiple: true
	    }
		section("Which locks?") {
		    input "locks", "capability.lock", title: "Lock(s):", required: false, multiple: true
	    }
        section("Which motion sensors?") {
		    input "motions", "capability.motionSensor", title: "Motion(s):", required: false, multiple: true
	    }
        section("Which presence sensors?") {
		    input "presence", "capability.presenceSensor", title: "Presence Sensors(s):", required: false, multiple: true
	    }
        section("How long to disable camera notifications if detection is found?") {
            input "seconds", "number", title: "Number of Seconds", required: true, defaultValue: 60, width: 6
        }
        section("Enter the name of your Motion Disabler - this is probably a combination of your camera names you've selected.") {
             label title: "Name", required: true
        }
	}
}

def prefListDevices() {  
    state.cameraList = [:]
    def response = parent.sendCommand('/ListCameras')
    for (camera in response.camera) { 
        parent.ifDebug("Camera Found: ID ${camera.id} Name: ${camera.name}")
        state.cameraList[camera.id] = camera.name
    }
    if (state.cameraList) {
        	//def nextPage = "sensorPage"
           // if (!state.doorList){nextPage = "summary"}  //Skip to summary if there are no doors to handle
                //return dynamicPage(name: "prefListDevices",  title: "Devices", nextPage:nextPage, install:false, uninstall:false) {
                    //if (state.camera) {
                        section("Select which cameras to use"){
                            input(name: "cameras", type: "enum", required:false, multiple:true, options:state.cameraList)
                        }
                    //}
                  
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
    //state.cameraList.each { camera ->
        
        //def items = camera.split('=')
	//log.debug "DeviceName: ${deviceName}  FieldNum: ${fieldNum}  evt: ${evt}"
    //def items = fieldNum.split('field')
        //def id = items[0]
        //def name = items[1]
        //parent.ifDebug "Camera List: ID: ${camera.key} Name: ${camera.value} "
    //}
    
    cameras.each { camera -> 
        parent.ifDebug "CameraID's Selected: ${camera} "
    }
    
    if (presence) {
	    subscribe(presence, "presence.present", genericHandler)
        //subscribe(presence, "presence.not present", genericHandler)
    }
    if (locks) {
        subscribe(lock, "lock.locked", genericHandler)
        subscribe(lock, "lock.unlocked", genericHandler)
    }
    if (contacts) {
        subscribe(contact, "contact.open", genericHandler)
        subscribe(contact, "contact.closed", genericHandler)
    }
    if (motions) {
        subscribe(motions, "motion.active", genericHandler)
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
