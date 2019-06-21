/**
 *  Weewx Weather Driver - for Local Device Capabilities 
 *  Pulls data/variables from Weewx Weather Driver daily.json and publishes those to be local, usable capabilies.
 * 
 *  Import URL: https://raw.githubusercontent.com/bdwilson/hubitat/master/Weewx/Weewx_Local_Capabilities-Hubitat.groovy
 *  
 *  Search for "variable" and alter the sections there with the information you want to capture from your daily.json file:
 *  Follow these directions to create the file via Weewx: https://github.com/sgrayban/hubitat-weewx-driver/
 *  
 *  You will have to edit this code to make this work; if you know how to migrate the info that you have to edit into 
 *  preference variables, please send me a pull request!  Please read the comments to modify for your use. 
 *  
 *-------------------------------------------------------------------------------------------------------------------
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
        command "PollStation"
        command "poll"

        attribute "WeewxUptime", "string"
        attribute "Refresh-Weewx", "string"
	    attribute "WeewxLocation", "string"

    }
    preferences() {

        section("Query Inputs"){
            input "ipaddress", "text", required: true, title: "Weewx Server IP/URI", defaultValue: "0.0.0.0"
            input "weewxPort", "text", required: true, title: "Connection Port", defaultValue: "800"
            input "weewxPath", "text", required: true, title: "path to file", defaultValue: "weewx/daily.json"
            input "amtRain", "text", required: false, title: "amout of rain required to show as wet", defaultValue: ".25"
            input "logSet", "bool", title: "Log All Data", required: true, defaultValue: false
            input "pollInterval", "enum", title: "Weewx Station Poll Interval", required: true, defaultValue: "5 Minutes", options: ["Manual Poll Only", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]
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


    log.debug ("${pollIntervalCmd} ${pollInterval}")
    if(pollInterval == "Manual Poll Only"){LOGINFO( "MANUAL POLLING ONLY")}
    else{ "runEvery${pollIntervalCmd}"(pollSchedule)}

}

def poll(){
    log.info "Manual Poll"
    PollStation()
}



def parse(String description) {
}


def PollStation()
{
    LOGDEBUG("Weewx: ForcePoll called")
    def params1 = [
        uri: "http://${ipaddress}:${weewxPort}/${weewxPath}"
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
         
           // change this to be whatever variable you want to pull from daily.json.
           // the replace function will need to remove fahrenheit, in, % so adjust
           // as needed
           LOGDEBUG("Getting variable1 : ${resp1.data.stats.current.poolTemp}")
           def var1=(resp1.data.stats.current.poolTemp)
           // remove fahrenheit and celsius (doesn't hurt to have both) 
           var1 = var1.replace("\u00B0F", "")
           var1 = var1.replace("\u00B0C", "")
            
           // same as above. if not needed, comment out
           LOGDEBUG("Getting variable2: ${resp1.data.stats.current.crawlHumidity}")
           def var2=(resp1.data.stats.current.crawlHumidity)
           var2 = var2.replace("%", "")
           
           // same as above. if not needed, comment out.
           LOGDEBUG("Getting variable3: ${resp1.data.stats.sinceMidnight.rainSum}")
           def var3=(resp1.data.stats.sinceMidnight.rainSum)
           var3 = var3.replace("in", "")                
           LOGDEBUG("Done with variables: ${var1} ${var2} ${var3}")

           sendEvent(name: "WeewxUptime", value: resp1.data.serverUptime)
           sendEvent(name: "WeewxLocation", value: resp1.data.location)
           sendEvent(name: "Refresh-Weewx", value: pollInterval)
            
           if (var1) {
               // adjust if var1 isn't a temperature value.
               sendEvent(name: "temperature", value: var1)
           }
           if (var2) {
               // adjust if var2 isn't a humidity value
               sendEvent(name: "humidity", value: var2)
           }
           if (var3) {
               // if you're note using wet/dry water values, you will need to adjust
               if (var3.toDouble() >= amtRain.toDouble()) {
                    sendEvent(name: "water", value: "wet")
               } else {
                    sendEvent(name: "water", value: "dry")
               } 
           }
           LOGDEBUG("Done with events")
   }

    } catch (e) {
        log.error "something went wrong: $e"
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
