/*
 * Rain Cloud Sprinkler Controller
 *
 * Calls URIs with HTTP GET to manage Melnor Raincloud devices.
 * 
 * Assumptions: 
 * 1) You need to create one virtual device per valve - its up to you
 * to not schedule the valves to overlap.
 * 2) The default time to run needs to be > whatever time you schedule via an 
 * app like Simple Irrigation.
 * 3) Be courteous with your scheduling to check status updates. If you have 4
 * valves checking every 5 minutes, it could be seen as putting considerable load
 * on Melnor's servers. I would not go lower than 5 minutes while valves are open
 * and would consider setting the refresh time to manual or 3 hours while closed.
 * 4) This should really be a parent/child app setup and the backend API should
 * respond with the statuses of all controllers/faucets/valves vs. one response
 * per valve. I only have 1 faucet so this doesn't impact me as much as it does
 * others. Feel free to fix and send me pull requests. 
 * 
 * You need to run a Rainycloud API server on your network: https://github.com/bdwilson/rainycloud-flask
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 * 
 */

def driverVer() { return "1.1.0" }

metadata {
    definition(name: "Raincloud Controller", namespace: "brianwilson-hubitat", author: "Brian Wilson", importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Raincloud/Raincloud.groovy") {
        capability "Valve"
		capability "Actuator"
        capability "Switch"
		
		command "battery"
		command "refresh"
        command "auto"
        command "open", ["number"]
        
        attribute "AutoWatering", "BOOLEAN"
        attribute "LastUpdated", "DATE"
        attribute "WateringTime", "NUMBER"
        attribute "Battery", "NUMBER"
    }
}

preferences {
    section("URIs") {
		input "URL", "text", title: "Rainycloud URL and port", required: true, defaultValue: "http://x.x.x.x:5058/api"
        input "controllerID", "text", title: "Controller ID", required: true
        input "faucetID", "text", title: "Faucet ID (4 digit alpha-numeric)", required: true
        input "valveID", "text", title: "Valve ID (1-4)", required: true
        input "defaultTime", "text", title: "Default time to keep valve open", required: true, defaultValue: "10"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true            
        input "pollInterval", "enum", title: "Rainycloud API Poll Interval (how often to poll when valve is closed)", required: true, defaultValue: "Manual Poll Only", options: ["Manual Poll Only", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]
        input "pollIntervalOpen", "enum", title: "Rainycloud API Poll Interval (how often to poll when valve is open)", required: true, defaultValue: "5 Minutes", options: ["Manual Poll Only", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]
    }
}

def get(url,mystate) {
   if (logEnable) log.debug "Call to ${url}; setting ${mystate}"
   state.lastCmd = mystate
   try {
        httpGet([uri:url, timeout:5]) { r ->
            if (r.success) {
                if (logEnable) log.debug "r.data: ${r.data}"
                date = new Date()
                date = date.format("MM/dd/yyyy HH:mm:ss")
                if (date != state.lastUpdated) {
                    state.lastActivity = date
                    sendEvent(name: "LastUpdate", value: date)
                }
                if ((r.data.is_watering != state.is_watering) && (r.data.is_watering != null)) {
                    state.is_watering = r.data.is_watering
                    if (r.data.is_watering == false) {
                        sendEvent(name: "switch", value: "off", isStateChange: true)
                        sendEvent(name: "valve", value: "closed", isStateChange: true)
                        unschedule(refresh)
                        setSchedule()
                    } else {
                        sendEvent(name: "switch", value: "on", isStateChange: true)
                        sendEvent(name: "valve", value: "open", isStateChange: true)  
                        unschedule(refresh)
                        setScheduleOpen()
                        //schedule("0 */5 * ? * * *", refresh)
                    }
                }
                if ((r.data.watering_time >= 0) && (state.watering_time != r.data.watering_time) && (r.data.watering_time != null))  {
                    state.watering_time = r.data.watering_time
                    sendEvent(name: "WateringTime", value: r.data.watering_time)  
                }
                if ((r.data.auto_watering == 1) || (r.data.auto_watering == true))
                        r.data.auto_watering = true
                    else 
                        r.data.auto_watering = false
                if ((r.data.auto_watering != state.auto_watering) && (r.data.auto_watering != null))  {
                    state.auto_watering = r.data.auto_watering
                    sendEvent(name: "AutoWatering", value: r.data.auto_watering)
                }
                if ((r.data.battery != state.battery) && (r.data.battery != null)) {
                    state.battery= r.data.battery
                    sendEvent(name: "Battery", value: r.data.battery)
                }
    
           }
           if (logEnable)
                if (r.data) log.debug "${r.data}"
       }
    } catch (Exception e) {
        log.warn "Call to ${url} failed: ${e.message}"
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def installed() {
    log.info "installed or updated with ${settings}"
	if (!settings.URL || !settings.valveID || !settings.defaultTime || !settings.controllerID || !settings.faucetID) {
		log.error "Please make sure Rainycloud URL, controller, faucet and Valve ID are configured. You can see your devices listed when you start raincloudy-flask." 
	}
	state.controllerURL = settings.URL + "/" + settings.controllerID + "/" + settings.faucetID
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(3600, logsOff)
	state.DriverVersion=driverVer()
    unschedule()
     
    if (!onlyPoll) {
        setSchedule()
    }    
    schedule("59 23 * ? * * *", battery)
}

def updated() {
    installed()
}

def setSchedule() {
    unschedule(refresh)
    def pollIntervalCmd = (settings?.pollInterval ?: "3 Hours").replace(" ", "")
 
    if(pollInterval == "Manual Poll Only"){ 
       if (logEnable) log.debug( "Manual Polling only")
    } else { 
       "runEvery${pollIntervalCmd}"(refresh)
    }    
}

def setScheduleOpen() {
    unschedule(refresh)
    def pollIntervalCmd = (settings?.pollIntervalOpen ?: "3 Hours").replace(" ", "")
 
    if(pollIntervalOpen == "Manual Poll Only"){ 
       if (logEnable) log.debug( "Manual Polling when Open")
    } else { 
       "runEvery${pollIntervalCmd}"(refresh)
    }    
}

def parse(String description) {
    if (logEnable) log.debug(description)
}

def on() {
    open()
}

def off() {
    close()
}

def close() {
    url = state.controllerURL + "/close/" + valveID     
    get(url,"closing")
    refresh()
}

def open(mins) {
    if (!mins)
        mins = defaultTime
    
    url = state.controllerURL + "/open/" + valveID + "/" + mins    
    get(url,"opening")
    refresh()
}

def refresh() {
    url = state.controllerURL + "/status/" + valveID
    get(url,"refresh")
}

def battery() {
    url = state.controllerURL + "/battery"
    get(url,"battery")
}

def auto() {
    if (state.auto_watering == true) 
        aw = 0
    else 
        aw = 1
    url = state.controllerURL + "/auto/" + valveID + "/" + aw     
    get(url,"auto")
    refresh()
}
