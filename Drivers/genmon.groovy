/**
 * Please Note: This app is NOT released under any open-source license.
 * Please be sure to read the license agreement before installing this code.
 *
 *  Genmon Driver for Hubitat Elevation
 *
 *  Original code © 2019 AScott Grayban
 *  Original code from Andrew Parker
 *
 * This software package is created and licensed by Scott Grayban.
 *
 * This software, along with associated elements, including but not limited to online and/or electronic documentation are
 * protected by international laws and treaties governing intellectual property rights.
 *
 * This software has been licensed to you. All rights are reserved. You may use and/or modify the software.
 * You may not sublicense or distribute this software or any modifications to third parties in any way.
 *
 * You may not distribute any part of this software without the author's express permission
 *
 * By downloading, installing, and/or executing this software you hereby agree to the terms and conditions set forth in the Software license agreement.
 * This agreement can be found on-line at: https://sgrayban.github.io/Hubitat-Public/software_License_Agreement.txt
 * 
 * Hubitat is the trademark and intellectual property of Hubitat Inc. 
 * Scott Grayban has no formal or informal affiliations or relationships with Hubitat.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License Agreement
 * for the specific language governing permissions and limitations under the License.
 *
 *-------------------------------------------------------------------------------------------------------------------
 *
 *
 *  Version:
 *  1.0.0 - Initial commit
 *
 */


metadata {
    definition (
	name: "Genmon Status Driver",
	namespace: "brianwilson-hubitat",
	author: "Brian Wilson",
	importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Drivers/genmon.groovy"
	)

	{
        //capability "Actuator"
        capability "PowerMeter"
        //capability "Switch"
        //capability "Sensor"
        capability "Polling"
        command "PollStation"
        command "poll"
        //command "Start"
       // command "Stop"
       // command "StartAndTransfer"
        
// Base Info   
        attribute "Generator Time", "string"
        attribute "Monitor Time", "string"
        attribute "Alarm Status", "string"
        
// Collected Local Station Data       
        attribute "Switch State", "string"
        attribute "Engine State", "string"
        attribute "Battery Voltage", "string"
        attribute "RPM", "number"
        attribute "Frequency", "string"
        attribute "Output Voltage", "string"
        attribute "Output Current", "string"
        attribute "Output Power", "string"
        attribute "Utility Voltage", "string"
        attribute "Last Service", "string"
        attribute "Last Alarm", "string"
        attribute "Last Run", "string"
        attribute "Last Outage", "string"
        attribute "Fuel in last 24 hours", "string"
        attribute "Fuel in last 30 days", "string"
        attribute "Fuel in last 7 days", "string"
        
        attribute "Battery Voltage Number", "number"
        attribute "Frequency Number", "number"
        attribute "Output Voltage Number", "number"
        attribute "Output Current Number", "number"
        attribute "Output Power Number", "number"
        attribute "Utility Voltage Number", "number"
        attribute "Fuel in last 24 hours Number", "number"
        attribute "Fuel in last 30 days Number", "number"
        attribute "Fuel in last 7 days Number", "number"


    }

    preferences() { 
        section("Query Inputs"){
            input "ipaddress", "text", required: true, title: "genmon Server IP/URI", defaultValue: "0.0.0.0"
            input "port", "text", required: true, title: "Connection Port", defaultValue: "80"
            input "pollInterval", "enum", title: "Poll Interval", required: true, defaultValue: "5 Minutes", options: ["Manual Poll Only", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]
            input "logSet", "bool", title: "Log All Data", required: true, defaultValue: false
        }
    }
}

def initialize(){
    updated()
}

private dbCleanUp() {
	unschedule()
}

def updated() {
    dbCleanUp()
     log.debug "Updated called"
    logCheck()
    PollStation()
    def pollIntervalCmd = (settings?.pollInterval ?: "3 Hours").replace(" ", "")
    if(pollInterval == "Manual Poll Only"){LOGINFO( "MANUAL POLLING ONLY")}
    else{ "runEvery${pollIntervalCmd}"(pollSchedule)}
}

def poll(){
    log.info "Manual Poll"
    PollStation()
}

def pollSchedule()
{
    PollStation()
}
              
def parse(String description) {
}

def PollStation()
{
    LOGDEBUG("Genmon: ForcePoll called")
    def params1 = [
        uri: "http://${ipaddress}:${port}/cmd/status_json",
        requestContentType: 'application/json',
        contentType: 'application/json'
         ]
    
    try {
        httpGet(params1) { resp1 ->
            resp1.headers.each {
            LOGINFO( "Response1: ${it.name} : ${it.value}")
        }
            if(logSet == true){   
                LOGINFO( "params1: ${params1}")
                LOGINFO( "response contentType: ${resp1.contentType}")
 		        LOGINFO( "response data: ${resp1.data}")
            } 
            def temp = ""
            state.SwitchState = resp1.data.Status[0].Engine[0].'Switch State'
            sendEvent(name: "Switch State", value: state.SwitchState, isStateChange: true)   
            
            state.EngineState = resp1.data.Status[0].Engine[1].'Engine State'
            sendEvent(name: "Engine State", value: state.EngineState, isStateChange: true)
            LOGINFO(state.SwitchState)
            int element = 0
            if (resp1.data.Status[0].Engine[2].'System In Alarm') {
                temp = resp1.data.Status[0].Engine[2].'System In Alarm'
                log.info("System is in alarm: ${temp}")
                state.AlarmStatus = temp
                sendEvent(name: "Alarm Status", value: temp, isStateChange: true)
                element = 3
            } else {
                state.AlarmStatus = "None"
                sendEvent(name: "Alarm Status", value: "No Alarm", isStateChange: true)
                element = 2
            }

            temp = resp1.data.Status[0].Engine[element].'Battery Voltage'
            sendEvent(name: "Battery Voltage", value: temp, isStateChange: true)    
            //LOGINFO("${temp}")
            state.BatteryVoltage = temp.replace(" V", "")
            sendEvent(name: "Battery Voltage Number", value: state.BatteryVoltage, isStateChange: true)
            
            element=element+1
            temp = resp1.data.Status[0].Engine[element].'RPM'
            //LOGINFO("${temp}")

            state.RPM = temp.replace(" ", "")
            sendEvent(name: "RPM", value: state.RPM, isStateChange: true)
           
            element = element+1
            temp = resp1.data.Status[0].Engine[element].'Frequency'
            sendEvent(name: "Frequency", value: temp, isStateChange: true)
            //LOGINFO("${temp}")
            state.Frequency = temp.replace(" Hz", "")
            sendEvent(name: "Frequency Number", value: state.Frequency, isStateChange: true)

            element = element+1
            temp = resp1.data.Status[0].Engine[element].'Output Voltage'
            sendEvent(name: "Output Voltage", value: temp, isStateChange: true)
            //LOGINFO("${temp}")
            state.OutputVoltage = temp.replace(" V", "")
            sendEvent(name: "Output Voltage Number", value: state.OutputVoltage, isStateChange: true)

            element = element+1
            temp = resp1.data.Status[0].Engine[element].'Output Current'
            sendEvent(name: "Output Current", value: temp, isStateChange: true)
            //LOGINFO("${temp}")
            state.OutputCurrent = temp.replace(" A", "")
            sendEvent(name: "Output Current Number", value: state.OutputCurrent, isStateChange: true)
   
            element=element+1
            temp = resp1.data.Status[0].Engine[element].'Output Power (Single Phase)'
            sendEvent(name: "Output Power", value: temp, isStateChange: true)
            //LOGINFO("${temp}")
            state.OutputPower = temp.replace(" kW", "")
            sendEvent(name: "Output Power Number", value: state.OutputPower, isStateChange: true)

            temp = resp1.data.Status[1].Line[0].'Utility Voltage'
            sendEvent(name: "Utility Voltage", value: temp, isStateChange: true)
            //LOGINFO("${temp}")
            state.UtilityVoltage = temp.replace(" V", "")
            sendEvent(name: "Utility Voltage Number", value: state.UtilityVoltage, isStateChange: true)

            state.LastService = resp1.data.Status[2]['Last Log Entries']['Logs']['Service Log']
            state.LastAlarm = resp1.data.Status[2]['Last Log Entries']['Logs']['Alarm Log']
            state.LastRun = resp1.data.Status[2]['Last Log Entries']['Logs']['Run Log']
            state.MonitorTime = resp1.data.Status[3].Time[0].'Monitor Time'
            state.GeneratorTime = resp1.data.Status[3].Time[1].'Generator Time'
           // sendEvent(name: "Switch State", value: state.SwitchState)
           // sendEvent(name: "Engine State", value: state.EngineState)
           // sendEvent(name: "Battery Voltage", value: state.BatteryVoltage)
          //  sendEvent(name: "RPM", value: state.RPM)
         //   sendEvent(name: "Frequency", value: state.Frequency)
         //  sendEvent(name: "Output Voltage", value: state.OutputVoltage)
         //   sendEvent(name: "Output Current", value: state.OutputCurrent)
         //  sendEvent(name: "Output Power", value: state.OutputPower)
         //  sendEvent(name: "Utility Voltage", value: state.UtilityVoltage)
            sendEvent(name: "Last Service", value: state.LastService, isStateChange: true)
            sendEvent(name: "Last Alarm", value: state.LastAlarm, isStateChange: true)
            sendEvent(name: "Last Run", value: state.LastRun, isStateChange: true)
            sendEvent(name: "Monitor Time", value: state.MonitorTime, isStateChange: true)
            sendEvent(name: "Generator Time", value: state.GeneratorTime, isStateChange: true)
        }
       } catch (e) {
                   log.error "Something went wrong: $e"
       }
       def params2 = [
            uri: "http://${ipaddress}:${port}/cmd/power_log_json?power_log_json=43200,fuel",
            requestContentType: 'application/json',
            contentType: 'application/json'
         ]
    
        try {
            httpGet(params2) { resp2 ->
                resp2.headers.each {
                LOGINFO( "Response1: ${it.name} : ${it.value}")
            }
            if(logSet == true){  
                LOGINFO( "params1: ${params2}")
                LOGINFO( "response contentType: ${resp2.contentType}")
 		        LOGINFO( "response data: ${resp2.data}")
            } 
            state.Fuel30 = resp2.data
            sendEvent(name: "Fuel in last 30 days", value: state.Fuel30 + " gal", isStateChange: true)
            //state.Fuel30 = temp.replace(" gal", "")
            sendEvent(name: "Fuel in last 30 days Number", value: state.Fuel30, isStateChange: true)
            }
        } catch (e) {
                   log.error "Something went wrong: $e"
        }
       
       def params3 = [
            uri: "http://${ipaddress}:${port}/cmd/power_log_json?power_log_json=1440,fuel",
            requestContentType: 'application/json',
            contentType: 'application/json'
         ]
    
        try {
            httpGet(params3) { resp3 ->
                resp3.headers.each {
                LOGINFO( "Response1: ${it.name} : ${it.value}")
            }
            if(logSet == true){  
                LOGINFO( "params1: ${params3}")
                LOGINFO( "response contentType: ${resp3.contentType}")
 		        LOGINFO( "response data: ${resp3.data}")
            } 
            state.Fuel24 = resp3.data
            sendEvent(name: "Fuel in last 24 hours", value: state.Fuel24 + " gal", isStateChange: true)
            //state.Fuel24 = temp.replace(" gal", "")
            sendEvent(name: "Fuel in last 24 hours Number", value: state.Fuel24, isStateChange: true)

            //LOGINFO( "Data: HERE ${SwitchState} ${EngineSwitch} ${BatteryVoltage}")
            //LOGINFO( "Data: HERE2 ${SwitchState} ${EngineState} ${BatteryVoltage} ${RPM} ${Frequency} ${OutputVoltage} ${OutputCurrent} ${OutputPower} ${UtilityVoltage} ${LastService} ${MonitorTime} ${GeneratorTime} ${Fuel30} ${Fuel24}")   
          }
        }  catch (e) {
                   log.error "Something went wrong: $e"
        }
       
       def params4 = [
            uri: "http://${ipaddress}:${port}/cmd/power_log_json?power_log_json=10080,fuel",
            requestContentType: 'application/json',
            contentType: 'application/json'
         ]
    
        try {
            httpGet(params4) { resp4 ->
                resp4.headers.each {
                LOGINFO( "Response1: ${it.name} : ${it.value}")
            }
            if(logSet == true){  
                LOGINFO( "params1: ${params4}")
                LOGINFO( "response contentType: ${resp4.contentType}")
 		        LOGINFO( "response data: ${resp4.data}")
            } 
            state.Fuel7 = resp4.data
            sendEvent(name: "Fuel in last 7 days", value: state.Fuel7 + " gal", isStateChange: true)
            //state.Fuel24 = temp.replace(" gal", "")
            sendEvent(name: "Fuel in last 7 days Number", value: state.Fuel7, isStateChange: true)

            //LOGINFO( "Data: HERE ${SwitchState} ${EngineSwitch} ${BatteryVoltage}")
            //LOGINFO( "Data: HERE2 ${SwitchState} ${EngineState} ${BatteryVoltage} ${RPM} ${Frequency} ${OutputVoltage} ${OutputCurrent} ${OutputPower} ${UtilityVoltage} ${LastService} ${MonitorTime} ${GeneratorTime} ${Fuel30} ${Fuel24}")
          }
         } catch (e) {
                   log.error "Something went wrong: $e"
         }
         
        def params5 = [
            uri: "http://${ipaddress}:${port}/cmd/outage_json",
            requestContentType: 'application/json',
            contentType: 'application/json'
         ]
    
        try {
            httpGet(params5) { resp5 ->
                resp5.headers.each {
                LOGINFO( "Response1: ${it.name} : ${it.value}")
            }
            if(logSet == true){  
                LOGINFO( "params1: ${params5}")
                LOGINFO( "response contentType: ${resp5.contentType}")
 		        LOGINFO( "response data: ${resp5.data}")
            }
               // LOGINFO("test: ${resp5.data.Outage[7].'Outage Log'[0]}")
                
            state.LastOutage = resp5.data.Outage[7].'Outage Log'[0]
            sendEvent(name: "Last Outage", value: state.LastOutage, isStateChange: true)

            //LOGINFO( "Data: HERE ${SwitchState} ${EngineSwitch} ${BatteryVoltage}")
            //LOGINFO( "Data: HERE2 ${SwitchState} ${EngineState} ${BatteryVoltage} ${RPM} ${Frequency} ${OutputVoltage} ${OutputCurrent} ${OutputPower} ${UtilityVoltage} ${LastService} ${MonitorTime} ${GeneratorTime} ${Fuel30} ${Fuel24}")
          }
         } catch (e) {
                   log.error "Something went wrong: $e"
         }
}


// define debug action ***********************************
def logCheck(){
state.checkLog = logSet
    if(state.checkLog == true){
    log.info "All Logging Enabled"
    }
    else if(state.checkLog == false){
    log.info "Further Logging Disabled"
    }
}

def LOGDEBUG(txt){
    try {
    	if(state.checkLog == true){ log.debug("Genmon Driver - DEBUG:  ${txt}") }
    } catch(ex) {
    	log.error("LOGDEBUG unable to output requested data!")
    }
}

def LOGINFO(txt){
    try {
    	if(state.checkLog == true){log.info("Genmon Driver - INFO:  ${txt}") }
    } catch(ex) {
    	log.error("LOGINFO unable to output requested data!")
    }
}
