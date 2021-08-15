/**
 * WaterGuru Integration App
 * 
 * 1.0.1 - Brian Wilson / bubba@bubba.org
 *
 * Assumptions: 
 * 1) You need to use waterguru-flask python code and run it on a server to talk 
 * to WaterGuru - this Hubitat app depends on it and can't run on it's own.
 * https://github.com/bdwilson/waterguru-api
 * 2) Be courteous with your scheduling to check status updates. There is no need
 * to refresh this often. The python code doesn't make use of any token caching, so
 * each run will log you in. I gather my temperatures for my pool via another means, 
 * so getting this info daily or twice a day is sufficient. 
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
  name: "WaterGuru Integration",
  namespace: "brianwilson-hubitat",
  author: "bubba@bubba.org",
  description: "WaterGuru Integration App",
  category: "My Apps",
  importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/WaterGuru/WaterGuru-Integration.groovy",
  iconUrl: "",
  iconX2Url: "",
  iconX3Url: "",
  singleInstance: true
)

preferences {
	page(name: "page1")
}

def page1() {
    state.isDebug = isDebug
    return dynamicPage(name: "page1", install: true, uninstall: true) {
    section("WaterGuru Integration") {
      paragraph "This integration requires the <a window=_ href='https://github.com/bdwilson/waterguru-api'>WaterGuru Flask python app</a> in order to communicate with the WaterGuru API. You'll need to set this up on a Linux system prior to completing this info."
      input "URL", "text", title: "WaterGuru Flask Server and port", description: "http://192.168.1.x:53255", required: true
    }
    section("WaterGuru Polling Schedule") {
            paragraph "Check your app to see what time your pool is taking samples and make sure your check time happens after this time."
			input(name: "days", type: "enum", title: "Only check on pool on these days", description: "Days to check", required: true, multiple: true, options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"])
			paragraph "Select up to 2 polling times per day; leave one blank if you just want to update once a day."
			input "startTime1", "time", title: "1st Time to Poll", required: true, width: 6, submitOnChange:true
			paragraph "<hr>"
			input "startTime2", "time", title: "2nd Time to Poll", required: false, width: 6, submitOnChange:true
            paragraph "<hr>"
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
    if(startTime1) schedule(startTime1, timedRefresh, [overwrite: false])
	if(startTime2) schedule(startTime2, timedRefresh, [overwrite: false])
}

def timedRefresh() {
	dayOfTheWeekHandler()
    if (state.daysMatch) {
        updateStatus()
    }
}

def dayOfTheWeekHandler() {
    // thank you @bptworld for this routine from Simple Irrigation.
	ifDebug("In dayOfTheWeek")
    
    def df = new java.text.SimpleDateFormat("EEEE")
    df.setTimeZone(location.timeZone)
    def day = df.format(new Date())
    def dayCheck = days.contains(day)

    if(dayCheck) {
		ifDebug("In dayOfTheWeekHandler - Days of the Week Passed")
		state.daysMatch = true
	} else {
		ifDebug("In dayOfTheWeekHandler - Days of the Week Check Failed")
		state.daysMatch = false
	}
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
            return resp.data
        }
    } catch (e) {  // } catch (groovyx.net.http.HttpResponseException e)  $e.response
        log.error "error: $e"
    }
}


private removeChildDevices() {
  getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def updateStatus() {
    discoverChildDevices()
}

def discoverChildDevices() {
  def response = sendCommand('/wg')  
  ifDebug("response: ${response}")
  def date = new Date()
  date = date.format("MM/dd/yyyy HH:mm:ss")
  response.waterBodies.each { item -> 
      ifDebug("Name: ${item.name}")
      name = item.name
      temp = item.waterTemp
      id = item.waterBodyId
      status = item.status
      LastMeasurementHuman = item.latestMeasureTimeHuman
      LastMeasurement = item.latestMeasureTime

      statusMsg = "None"
      def CassetteStatus, batteryStatus
          
      def count = 0
      item.alerts.each { alert ->
          count++
          statusMsg = statusMsg + alert.status + " Alert (" + count + "): " + alert.text + "\n"
          ifDebug("status: ${alert.status}")
          ifDebug("text: ${alert.text}")
          statusMsg = statusMsg.replace("None", "")
      }  
      index = item.measurements.findIndexOf({ it.type == "FREE_CL" })
      freeChlorine = item.measurements[index].floatValue
      index = item.measurements.findIndexOf({ it.type == "PH" })
      pH = item.measurements[index].floatValue
      index = item.measurements.findIndexOf({ it.type == "SKIMMER_FLOW" })
      rate = item.measurements[index].intValue
      ifDebug("measurements: ${freeChlorine} ${pH} ${rate}")
      rssi = item.pods.rssiInfo.rssi
      item.pods.each { pod ->
          index = pod.refillables.findIndexOf({ it.label == "Cassette" })
          podStatus = pod.refillables[index].status
          CassettePercent = pod.refillables[index].pctLeft
          CassetteTimeLeft = pod.refillables[index].timeLeftText
          CassetteChecksLeft = pod.refillables[index].amountLeft
          index = pod.refillables.findIndexOf({ it.label == "Battery" })
          batteryStatus = pod.refillables[index].status
          batteryPct = pod.refillables[index].pctLeft
       }

      d = getChildDevices()?.find { it.deviceNetworkId == "${id}"}
      if (d == null) {
                  log.debug("Creating WaterGuru Pool for ${id} with Name: ${name}")
                  addChildDevice("brianwilson-hubitat", "WaterGuru Integration Driver", id, null, [name: "${name} Pool", label: "${name} Pool", completedSetup: true])
      }
      d = getChildDevices()?.find { it.deviceNetworkId == "${id}" }
      if (d != null) {
          if (date != d.currentValue("LastUpdate")) {
              state.lastActivity = date
              d.sendEvent(name: "LastUpdate", value: date)
          }
          if (d.currentValue("battery") != batteryPct) {  
              d.sendEvent(name: "battery", value: batteryPct, isStateChange: true)
          } 
          if (d.currentValue("batteryStatus") != batteryStatus) {  
              d.sendEvent(name: "batteryStatus", value: batteryStatus, isStateChange: true)
          }  
          if (d.currentValue("CassettePercent") != CassettePercent) {
              d.sendEvent(name: "CassettePercent", value: CassettePercent, isStateChange: true)
          }
          if (d.currentValue("temperature") != temp) {
              d.sendEvent(name: "temperature", value: temp, isStateChange: true)
          }
          if (d.currentValue("CassetteStatus") != CassetteStatus) {  
              d.sendEvent(name: "CassetteStatus", value: CassetteStatus, isStateChange: true)
              switch (CassetteStatus) {
			        case "GREEN":
                        d.sendEvent(name: "consumableStatus", value: "good")
                        break
                    case "YELLOW":
              	        d.sendEvent(name: "consumableStatus", value: "replace")
                        break
                    case "RED": 
                        d.sendEvent(name: "consumableStatus", value: "missing")
                        break
                    default: 
                        d.sendEvent(name: "consumableStatus", value: "maintenance_required")
                        break
              }
          }
         
          if (d.currentValue("CassetteTimeLeft") != CassetteTimeLeft) {  
              d.sendEvent(name: "CassetteTimeLeft", value: CassetteTimeLeft, isStateChange: true)
          } 
          if (d.currentValue("LastMeasurementHuman") != LastMeasurementHuman) {  
              d.sendEvent(name: "LastMeasurementHuman", value: LastMeasurementHuman, isStateChange: true)
          }     
          if (d.currentValue("LastMeasurement") != LastMeasurement) {  
              d.sendEvent(name: "LastMeasurement", value: LastMeasurement, isStateChange: true)
          }
          if (d.currentValue("CassetteChecksLeft") != CassetteChecksLeft) {  
              d.sendEvent(name: "CassetteChecksLeft", value: CassetteChecksLeft, isStateChange: true)
          }
          if (d.currentValue("Status") != status) {  
              d.sendEvent(name: "Status", value: status, isStateChange: true)
          } 
          if (d.currentValue("rssi") != rssi) {  
              d.sendEvent(name: "rssi", value: rssi, isStateChange: true)
          }     
          if (d.currentValue("statusMsg") != statusMsg) {  
              d.sendEvent(name: "statusMsg", value: statusMsg, isStateChange: true)
          }
          if (d.currentValue("freeChlorine") != freeChlorine) {  
              d.sendEvent(name: "freeChlorine", value: freeChlorine, isStateChange: true)
          }   
          if (d.currentValue("pH") != pH) {  
              d.sendEvent(name: "pH", value: pH, isStateChange: true)
          }
          if (d.currentValue("rate") != rate) {  
              d.sendEvent(name: "rate", unit: "GPM", value: rate, isStateChange: true)
          }
      } 
  }
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'WaterGuru Integration: ' + msg  
}
