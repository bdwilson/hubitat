/**
 *  Toggle API App
 *
 *  Copyright 2021 Brian Wilson
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
    name: "Toggle API App",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Toggle API App",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Toggle-API/toggle-api-app.groovy", 
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
 	def uri = getFullLocalApiServerUrl() + "/toggle/?access_token=${state.accessToken}"
    def extUri = fullApiServerUrl() + "/?access_token=${state.accessToken}"
    def deviceListUri = getFullLocalApiServerUrl() + "/listDevices?access_token=${state.accessToken}"
    extUri = extUri.replaceAll("null","toggle")
    return dynamicPage(name: "setupScreen", uninstall: true, install: true){
   			section("<h1>Toggle API App</h1>") {
            	paragraph ("Please read all the steps below in order to toggle your devices via a URL. This can be used to open/close, lock/unlock, turn on/off devices via a URL - suitable for running a shortcut in iOS, launcher on your watch, or a momentary button")
			}
        section ("<h2>1. Select devices you wish to control with a toggle - </h2>") {
            paragraph ("This will allow this App to control the devices you created above")
		    input "locks", "capability.lock", title: "Lock(s):", required: false, multiple: true
            input "switches", "capability.switch", title: "Switches(s):", required: false, multiple: true
            input "doors", "capability.garageDoorControl", title: "Switches(s):", required: false, multiple: true
    	}
        section("<h2>2. Setup URL your App/Shortcuts/Launcher</h2>"){ 
            //paragraph("Use the following URL using the <a href='${extUri}'>${extUri}</a> with the [deviceId] after /toggle in the URL. The Internal URL would be <a href='${uri}'>${uri}</a> however it's inaccessible from outside your home.")
            paragraph("<b>Once installed</b>, open this URL to display internal and external URL's for each device. <a href='${deviceListUri}'>${deviceListUri}</a>. You can click this now, then reload it after clicking <b>Done</b>.")

        }
        section("<h2>3. Setup your shortcuts</h2>") {
            paragraph("If you're using iOS Shortcuts, you can pass your URL to <a href='https://www.icloud.com/shortcuts/d5271e247ab24c3880a38c45372a8252'>this shortcut</a> to run your toggle action. You can also use Tasker or another app to run your toggle actions.")
    	}
		section("<h2>4. Enable Debug Mode</h2>") {
            paragraph("Debug is enabled by default and will disable automatically after 1 hour.")
       		input "isDebug", "bool", title: "Enable Debug Mode", required: false, multiple: false, defaultValue: true, submitOnChange: true
    	}
    }
}

def installed() {
    log.warn "debug logging for Toggle API App: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
    ifDebug("Installed with settings: ${settings}")

}

def updated() {
    log.warn "debug logging for Toggle API App: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
	ifDebug("Updated with settings: ${settings}")
}

def listDevices() {
    def resp = []
    def uri = getFullLocalApiServerUrl() + "/toggle/?access_token=${state.accessToken}"
    def extUri = fullApiServerUrl() + "/?access_token=${state.accessToken}"
    def deviceListUri = getFullLocalApiServerUrl() + "/listDevices?access_token=${state.accessToken}"
    extUri = extUri.replaceAll("null","toggle")
    
    locks.each {
      ifDebug("RECEIVED: ${it.displayName}, attribute ${it.name}, ID: ${it.id}")
        resp << [Name: it.displayName, ID: it.id, InternalURL: uri.replaceAll("toggle/", "toggle/${it.id}"), ExternalURL: extUri.replaceAll("toggle/","toggle/${it.id}")]
    }
    switches.each {
      ifDebug("RECEIVED: ${it.displayName}, attribute ${it.name}, ID: ${it.id}")
        resp << [Name: it.displayName, ID: it.id, InternalURL: uri.replaceAll("toggle/", "toggle/${it.id}"), ExternalURL: extUri.replaceAll("toggle/","toggle/${it.id}")]
    }
    doors.each {
      ifDebug("RECEIVED: ${it.displayName}, attribute ${it.name}, ID: ${it.id}")
        resp << [Name: it.displayName, ID: it.id, InternalURL: uri.replaceAll("toggle/", "toggle/${it.id}"), ExternalURL: extUri.replaceAll("toggle/","toggle/${it.id}")]
    }
    return resp
}

def deviceHandler(evt) {}

def validCommandsg() {
	def msg = "Valid Commands: GET:/listDevices, GET:/toggle/<deviceId>"
	ifDebug("${msg}")
	return([error:msg])
}

def validCommandsp() {
	def msg = "You're missing a deviceId: /toggle/<deviceId>?access_token=..... - use /listDevices to get a list of valid deviceId's"
	ifDebug("${msg}")
	return([error:msg])
}

def doToggle(devices) {
   	def mydevice = params.deviceId.toInteger()
    def device
    def msg 
    device = locks.find { it.deviceId == mydevice }
    if (device) {
        def currentState = device.currentState("lock")?.value
        if (currentState == "unlocked") {
                msg = "Locking ${device}"
                ifDebug(msg)
                device.lock()
        } else {
                msg = "Unlocking ${device}"
                ifDebug(msg)
                device.unlock()
        }
    } else {
            device = switches.find { it.deviceId == mydevice }
            if (device) {
             currentState = device.currentState("switch")?.value
             if (currentState == "on") {
                msg = "Turning Off ${device}"
                ifDebug(msg)
                device.off()
             } else {
                msg = "Turning On ${device}"
                ifDebug(msg)
                device.on()
             }
            } else {
                 device = doors.find { it.deviceId == mydevice }
                 if (device) {
                     currentState = device.currentState("door")?.value
                     if (currentState == "open") {
                         msg = "Closing ${device}"
                         ifDebug(msg)
                         device.close()
                    } else {
                         msg = "Opening ${device}"
                         ifDebug(msg)  
                         device.open()
                    }
                 } else {
                      msg = "Error: device not found. Make sure a deviceId of a device you've configured to toggle. To get a list of valid devices, go to /listDevices URL"
			          ifDebug("${msg}")
                      return([status:msg])
                 }
             }
 
    }
    return([status:msg])
    //def deviceName = device.displayName
    //ifDebug("event: ${event} device: ${device} deviceName: ${deviceName} currentState: ${currentState}")           
  
}

mappings {
	path("/") {
		action: [
			GET: "validCommandsg"
		]
	}
    path("/listDevices") {
        action: [
            GET: "listDevices"
        ]
    }
    path("/toggle") {
        action: [
            GET: "validCommandsp"
        ]
    }
    path("/toggle/:deviceId") {
    	action: [
            POST: "doToggle",
            GET: "doToggle"
        ]
    }
}

def logsOff() {
    log.warn "debug logging for Toggle API App now disabled..."
    app.updateSetting("isDebug", [value: "false", type: "bool"])
    state.isDebug = false 
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'Toggle API App: ' + msg  
}
