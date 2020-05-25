/**
 *  Weewx Weather Driver - for Local Device Capabilities 
 * 
 *  Pulls data/variables from Weewx Weather Driver daily.json and publishes those to be local, usable capabilies.
 *  Edits by Brian Wilson
 * 
 *  Most code was originally in Weewx Weather Driver - With External Forecasting and is available here:
 *  https://raw.githubusercontent.com/CobraVmax/Hubitat/master/Drivers/Weather/Weewx%20Weather%20Driver%20-%20With%20External%20Forecasting.groovy
 *  
 *  Copyright 2019 Andrew Parker
 *
 *  This driver was originally born from an idea by @mattw01 and @Jhoke and I thank them for that!
 *  
 *  This driver is specifically designed to be used with 'Weewx' and your own PWS
 *  It also has the capability to collect forecast data from an external source (once you have an api key)
 *
 *  
 *  This driver is free!
 *
 *  Donations to support development efforts are welcomed via: 
 *
 *  Paypal at: https://www.paypal.me/smartcobra
 *  
 *
 *  I'm very happy for you to use this driver without a donation, but if you find it useful
 *  then it would be nice to get a 'shout out' on the forum! -  @Cobra
 *  Have an idea to make this driver better?  - Please let me know :)
 *  Please don't alter this code unless you really know what you are doing.
 *  
 *
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *-------------------------------------------------------------------------------------------------------------------
 */

metadata {
    definition (name: "Weewx Local Capabilities", namespace: "brianwilson-hubitat", author: "Brian Wilson") {
        capability "Actuator"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Illuminance Measurement"
        capability "Relative Humidity Measurement"
        capability "Water Sensor"
        capability "Switch"
        command "PollStation"
        command "poll"

        attribute "WeewxUptime", "string"
        attribute "Refresh-Weewx", "string"
	    attribute "WeewxLocation", "string"
        attribute "RainForPeriod", "string"

    }
    preferences() {

        section("Query Inputs"){
            input "ipaddress", "text", required: true, title: "Weewx Server IP/URI", defaultValue: "0.0.0.0"
            input "weewxPort", "text", required: true, title: "Connection Port", defaultValue: "80"
            input "weewxPath", "text", required: true, title: "path to file", defaultValue: "weewx/daily.json"
            //input "amtRain", "text", required: false, title: "amout of rain required to show as wet", defaultValue: ".25"
            input "logSet", "bool", title: "Log All Data", required: true, defaultValue: false
            input "pollInterval", "enum", title: "Weewx Station Poll Interval", required: true, defaultValue: "5 Minutes", options: ["Manual Poll Only", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]
            input "temp", "text", title: "Temp Source", required: false, defaultValue: "data.stats.current.outTemp"
            input "humid", "text", title: "Humidity Source", required: false, defaultValue: "data.stats.current.humidity"
            //input "rain", "text", title: "Rain Source", required: false, defaultValue: "data.stats.sinceMidnight.rainSum"
            input "var1", "text", title: "Custom Rain Source", required: false, defaultValue: "data.stats.sinceMidnight.rainSum"
            input "var2", "text", title: "2nd Custom Rain Source", required: false, defaultValue: ""
            input "varoperator", "enum", title: "Custom Operator - to act on custom source 1 and 2", required: true, defaultValue: "or", options: ["or", "and"]
            //input "var1capability", "enum", title: "Custom 1 Capability", required: true, defaultValue: "None", options: ["None", "temperature", "humidity", "water", "switch"]
            //input "customoperator", "enum", title: "Custom Switch Operator", required: true, defaultValue: "None", options: ["None", ">", "<"]
            input "customamount", "text", title: "Custom value for switch to be on", required: false, defaultValue: "0"
        }

    }
}

def initialize(){
	updated()

}
def updated() {
    log.debug "Updated called"

    logCheck()

    PollStation()
    def pollIntervalCmd = (settings?.pollInterval ?: "3 Hours").replace(" ", "")


    //log.debug ("${pollIntervalCmd} ${pollInterval}")
    if(pollInterval == "Manual Poll Only"){LOGINFO( "MANUAL POLLING ONLY")}
    else{ "runEvery${pollIntervalCmd}"(pollSchedule)}

}

def poll(){
    log.info "Manual Poll"
    PollStation()
}



def parse(String description) {
}

def toIntOrNull = { it?.isInteger() ? it.toInteger() : null }

def PollStation()
{
    LOGDEBUG("Weewx: ForcePoll called")
    def params1 = [
        uri: "http://${ipaddress}:${weewxPort}/${weewxPath}", timeout: 5
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
                LOGINFO( "response data: ${resp1.data}")

           }
            def temp, humid, var1, var2, rain, wet, varT
            if (settings.temp && settings.temp != "None") {
                temp = varFix(resp1,settings.temp)
            }
            if (settings.humid && settings.humid != "None") {
                humid = varFix(resp1,settings.humid)
            }
            if (settings.var1 && settings.var1 != "None") {
                varT = varFix(resp1,settings.var1)
                var1 = varT.isDouble() ? varT.toDouble() : null
            }
            if (settings.var2 && settings.var2 != "None") {
                varT = varFix(resp1,settings.var2)
                var2 = varT.isDouble() ? varT.toDouble() : null
            }

           sendEvent(name: "WeewxUptime", value: resp1.data.serverUptime)
           sendEvent(name: "WeewxLocation", value: resp1.data.location)
           sendEvent(name: "Refresh-Weewx", value: pollInterval)
            
           if (temp) {
               sendEvent(name: "temperature", value: temp)
           }
           if (humid) {
               sendEvent(name: "humidity", value: humid)
           }
           wet=0
           rain=0 
            if ((settings.var1 || settings.var2) && !settings.customamount) {
                log.error("You need to set a custom amount")
            } else {
                if (settings.var1 && !settings.var2) {
                    if (var1 > settings.customamount.toDouble()) {
                        wet=1 
                    }
                    rain=var1
                }
                if (settings.var1 && settings.var2) {
                    if (varoperator == "and") {
                        def tRain = var1 + var2
                        rain=tRain
                        if (tRain.toDouble() >= settings.customamount.toDouble()) {
                            wet=1
                        }
                    } else {
                        if ((var1 >= settings.customamount.toDouble()) || (var2 >= settings.customamount.toDouble())) {
                            wet=1
                        }
                        rain=var2
                        if (var1 > var2) {
                                rain=var1
                        }
                    }
                }
            }
            if (wet == 1) {
                    sendEvent(name: "water", value: "wet")
                    sendEvent(name: "switch", value: "on")
            } else {
                    sendEvent(name: "water", value: "dry")
                    sendEvent(name: "switch", value: "off")               
            }
            sendEvent(name: "RainForPeriod", value: rain)

        }

    } catch (e) {
        log.error "something went wrong: $e"
    }

}

def varFix (response,var) {
    try {    
        def vars = var.split(/\./)
        //settings.temp.split('.').collect { it }.join('.')
        size = vars.size()
        //log.debug "SIze: ${size}"
        if (vars.size() == 2) {
            newVar=response."${vars[0]}"."${vars[1]}"
        } else if (vars.size() == 3) {
            newVar=response."${vars[0]}"."${vars[1]}"."${vars[2]}"
        } else if (vars.size() == 4) {
            newVar=response."${vars[0]}"."${vars[1]}"."${vars[2]}"."${vars[3]}"
        }
        //log.debug "Var: ${var}  NewVar: ${newVar}  Size: ${size}" 
        newVar = newVar.replace("\u00B0F", "")
        newVar = newVar.replace("\u00B0C", "")
        newVar = newVar.replace(" in", "")  
        newVar = newVar.replace(" mm", "")   
        return newVar
   } catch (e) {
        log.error "something went wrong: $e"
        log.error("It appears ${var} doesn't exist. Please check http://${ipaddress}:${weewxPort}/${weewxPath} to see if that value exists.")
    }    
}

def pollSchedule() {
    PollStation()
}

def logCheck() {
    state.checkLog = logSet
    if(state.checkLog == true){
        log.info "All Logging Enabled"
    } else if(state.checkLog == false){
        log.info "Further Logging Disabled"
    }
}

def on() {
    PollStation()
}

def off() {
    PollStation()
}

def LOGDEBUG(txt){
    try {
    	if(state.checkLog == true){ log.debug("Weewx Driver - DEBUG:  ${txt}") }
    } catch(ex) {
    	log.error("LOGDEBUG unable to output requested data!")
    }
}

def LOGINFO(txt){
    try {
    	if(state.checkLog == true){log.info("Weewx Driver - INFO:  ${txt}") }
    } catch(ex) {
    	log.error("LOGINFO unable to output requested data!")
    }
}
