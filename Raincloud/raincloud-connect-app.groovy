/**
 * Rain Cloud Controller App
 * 
 * 1.1.0 - Brian Wilson / bubba@bubba.org
 *
 * Assumptions: 
 * 1) You need to use Rainycloud-flask python code and run it on a server to talk 
 * to RainCloud - this Hubitat app depends on it and can't run on it's own.
 * https://github.com/bdwilson/rainycloud-flask
 * 2) The default time to run needs to be > whatever time you schedule via an 
 * app like Simple Irrigation. I suggest 59 minutes. 
 * 3) You should not overlap schedules for valves on the same faucet end. 
 * 4) Be courteous with your scheduling to check status updates. If you have 4
 * valves checking every 5 minutes, it could be seen as putting considerable load
 * on Melnor's servers. I would not go lower than 5 minutes while valves are open
 * and would consider setting the refresh time to manual or 3 hours while closed.
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
import groovy.json.JsonSlurper

definition(
  name: "Raincloud Controller",
  namespace: "brianwilson-hubitat",
  author: "bubba@bubba.org",
  description: "Raincloud Controller",
  category: "My Apps",
  iconUrl: "https://raw.githubusercontent.com/redloro/smartthings/master/images/honeywell-security.png",
  iconX2Url: "https://raw.githubusercontent.com/redloro/smartthings/master/images/honeywell-security.png",
  iconX3Url: "https://raw.githubusercontent.com/redloro/smartthings/master/images/honeywell-security.png",
  singleInstance: true
)

preferences {
	page(name: "page1")
}

def page1() {
    state.isDebug = isDebug
    return dynamicPage(name: "page1", install: true, uninstall: true) {
    section("Raincloud Connect") {
      paragraph "This integration requires the Raincloudy Flask python app in order to communicate with Melnor Raincloud. You'll need to set this up on a Linux system prior to completing this info."
      paragraph "Keep in mind, if you rename your zones within the RainCloud app, you'll need to restart your Rainycloud Flask app to pull new names into this integration"
      input "URL", "text", title: "Rainycloud Flask Server and port", description: "http://192.168.1.x:5058", required: true
      input "pollInterval", "enum", title: "Rainycloud API Poll Interval (how often to poll the RainCloud servers when all valves are closed)", required: true, defaultValue: "Manual Poll Only", options: ["Manual Poll Only", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]
      input "pollIntervalOpen", "enum", title: "Rainycloud API Poll Interval (how often to poll the RainCloud servers when at least one valve is open)", required: true, defaultValue: "5 Minutes", options: ["Manual Poll Only", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]
      input "defaultTime", "text", title: "Default time to keep valve open", required: true, defaultValue: "59"
    }

    section("") {
       input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
    }
  }
}

def installed() {
    updated()
}

def uninstalled() {
    unschedule()
    removeChildDevices()
}

def updated() {
    runIn(5, discoverChildDevices)
    updateStatus()
}


private sendCommand(command, params=[:]) {
    def path = "/api${command}"
    ifDebug("sendCommand: Endpoint: ${settings.URL} PATH: ${path}")

    def request = [
        uri:  settings.URL,
        path: path,
        contentType: 'application/json',
        query: params
    ]
    try {
        httpGet(request) {resp ->
            ifDebug("resp data: ${resp.data}")
            return resp.data
        }
    } catch (e) {  // } catch (groovyx.net.http.HttpResponseException e)  $e.response
        log.error "error: $e"
    }
}


private removeChildDevices() {
  getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def childClose(DNI) {
    def item = DNI.tokenize('-')
    response = sendCommand("/${item[0]}/${item[1]}/close/${item[2]}")
    runIn(10,updateStatus)

}

def childAuto(DNI, autoWatering) {
    def item = DNI.tokenize('-')
    if (autoWatering == true) 
        aw = 0
    else 
        aw = 1
    response = sendCommand("/${item[0]}/${item[1]}/auto/${item[2]}/${aw}")
    runIn(10,updateStatus)
}

def childOpen(DNI, mins=[:]) {
    def item = DNI.tokenize('-')
    if (!mins) {
         mins = settings.defaultTime
    }
    response = sendCommand("/${item[0]}/${item[1]}/open/${item[2]}/${mins}")
    runIn(10,updateStatus)
}

def childRain(DNI, mins=[:]) {
    def item = DNI.tokenize('-')
    if (!mins) {
         mins = settings.defaultTime
    }
    response = sendCommand("/${item[0]}/${item[1]}/rain/${item[2]}/${mins}")
    updateStatus(response)
}

def updateStatus(response=[:]) {
    if (!response) {
         response = sendCommand('/status')
    }
    def date = new Date()
    date = date.format("MM/dd/yyyy HH:mm:ss")
    def valveOn=0
    for (controller in response.controllers) {
      ifDebug("Controller Found: ${controller.value.id}")
      controllerID = controller.value.id
      controllerName = controller.value.name
      for (faucet in controller.value.faucets) {
          faucetID = faucet.value.id
          battery = faucet.value.battery
          faucetName = faucet.value.name
          ifDebug("Faucet Found: ${faucet.value.id}")
          for (valve in faucet.value.valves) {
              valveID = valve.value.id
              DNI=controllerID + "-" + faucetID + "-" + valveID
              is_watering = valve.value.is_watering
              auto_watering = valve.value.auto_watering
              rain_delay = valve.value.rain_delay
              watering_time = valve.value.watering_time
              valveName = valve.value.name 
                          
              d = getChildDevices()?.find { it.deviceNetworkId == DNI }
              if (d != null) {
                if (date != d.currentValue("LastUpdate")) {
                    state.lastActivity = date
                    d.sendEvent(name: "LastUpdate", value: date)
                }
                  if ((valveName != "") && (d.getName() != valveName)) {
                    tName = d.getName()
                    ifDebug("In Rename: valveName: ${valveName} getName(): ${tName}")
                    d.setDisplayName("${valveName}")
                    d.setName("${valveName}")
                    d.setLabel("${valveName}")
                  }
                myValue = d.currentValue("valve")
                ifDebug("Processing updates for ${d.deviceNetworkId} name: ${valveName} is_watering: ${is_watering} auto_watering: ${auto_watering} rain_delay: ${rain_delay} watering_time: ${watering_time}")
                if ((is_watering != null) && (is_watering == false)) {  
                    if ((d.currentValue("valve") == "open") || (d.currentValue("valve") == null)) {                   
                        d.sendEvent(name: "valve", value: "closed", isStateChange: true)
                        d.sendEvent(name: "switch", value: "off", isStateChange: true)
                     }
                } else {
                     valveOn=1
                     if ((d.currentValue("valve") == "closed") || (d.currentValue("valve") == null)) {  
                        d.sendEvent(name: "valve", value: "open", isStateChange: true)
                        d.sendEvent(name: "switch", value: "on", isStateChange: true)
                     }    
                }
                if ((watering_time >= 0) && (d.currentValue("WateringTime") != watering_time) && (watering_time != null))  {
                    d.sendEvent(name: "WateringTime", value: watering_time)  
                }
                if ((rain_delay >= 0) && (d.currentValue("RainDelay") != rain_delay) && (rain_delay != null))  {
                    d.sendEvent(name: "RainDelay", value: rain_delay)  
                }  
                if ((auto_watering == 1) || (auto_watering == true))
                        auto_watering = true
                      else 
                        auto_watering = false
                if ((d.currentValue("AutoWatering") != auto_watering) && (auto_watering != null))  {
                    d.sendEvent(name: "AutoWatering", value: auto_watering)
                }
                if ((d.currentValue("Battery") != battery) && (battery != null)) {
                    d.sendEvent(name: "Battery", value: battery)
                }
                
               }
              }
          }
     }
    if (valveOn == 1) {
       setScheduleOpen()
    } else {
       setSchedule()
    }
}

def discoverChildDevices() {
  def response = sendCommand('/status')        
  for (controller in response.controllers) {
      ifDebug("Controller: Name: ${controller.value.name}")
      controllerID = controller.value.id
      for (faucet in controller.value.faucets) {
          faucetID = faucet.value.id
          faucetName = faucet.value.name
          ifDebug("faucet name: ${faucet.value.name}")
          for (valve in faucet.value.valves) {
              valveID = valve.value.id
              valveName = valve.value.name
              //ifDebug("valves: ${valve.value.valve} Is Watering: ${valve.value.is_watering}")
              DNI=controllerID + "-" + faucetID + "-" + valveID
              currentchild = getChildDevices()?.find { it.deviceNetworkId == "${DNI}"}
              if (currentchild == null) {
                  if ((valveName == null) || (valveName == '')) {
                      name = "${faucetName}-${valveID}"
                  } else {
                      name = valveName
                  }
                  ifDebug("Creating Raincloud Valve for ${DNI} with Name: ${name}")
	              addChildDevice("brianwilson-hubitat", "Raincloud Valve", DNI, null, [name: "Raincloud ${name}", label: " Raincloud ${name}", completedSetup: true])
              }
          }
      }
  }
  updateStatus()
}

def setSchedule() {
    unschedule(updateStatus)
    def pollIntervalCmd = (settings?.pollInterval ?: "3 Hours").replace(" ", "")
 
    if(settings.pollInterval == "Manual Poll Only"){ 
       ifDebug("Manual Polling when all valves are closed")
    } else { 
       "runEvery${pollIntervalCmd}"(updateStatus)
    }    
}

def setScheduleOpen() {
    unschedule(updateStatus)
    def pollIntervalCmd = (settings?.pollIntervalOpen ?: "3 Hours").replace(" ", "")
 
    if(settings.pollIntervalOpen == "Manual Poll Only"){ 
       ifDebug("Manual Polling when a valve is open")
    } else { 
       "runEvery${pollIntervalCmd}"(updateStatus)
    }    
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'Raincloud Controller: ' + msg  
}
