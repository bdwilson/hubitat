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
 *
 *  Change Log:
 *
 *  1.2.0 - Publish the extended rain data that daily.json already contains (rain rate, today, yesterday,
 *          2-7 day totals and the rolling week total) as first class numeric attributes, plus an optional
 *          set of extended observations (wind, pressure, dewpoint, solar/UV, almanac, daily hi/lo).
 *          Added a rainSummary / rainTile attribute for dashboard tiles, Refresh capability, and fixed
 *          duplicate schedules being created on every save.
 *  1.1.0 - Original custom rain source / wet-dry switch behaviour.
 */

metadata {
    definition (name: "Weewx Local Capabilities", namespace: "brianwilson-hubitat", author: "Brian Wilson",
                importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Weewx/Weewx_Local_Capabilities-Hubitat.groovy") {
        capability "Actuator"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Illuminance Measurement"
        capability "Relative Humidity Measurement"
        capability "PressureMeasurement"
        capability "UltravioletIndex"
        capability "Water Sensor"
        capability "Switch"
        capability "Refresh"
        command "PollStation"
        command "poll"

        attribute "WeewxUptime", "string"
        attribute "Refresh-Weewx", "string"
	    attribute "WeewxLocation", "string"
        attribute "RainForPeriod", "string"

        // Extended rain data (always published when present in daily.json)
        attribute "rainRate", "number"
        attribute "rainToday", "number"
        attribute "rainYesterday", "number"
        attribute "rain2Days", "number"
        attribute "rain3Days", "number"
        attribute "rain4Days", "number"
        attribute "rain5Days", "number"
        attribute "rain6Days", "number"
        attribute "rain7Days", "number"
        attribute "rainWeek", "number"
        attribute "rainUnit", "string"
        attribute "rainSummary", "string"
        attribute "rainTile", "string"

        // Extended observations (published when "Publish extended Weewx data" is enabled)
        attribute "dewpoint", "number"
        attribute "windChill", "number"
        attribute "heatIndex", "number"
        attribute "insideTemperature", "number"
        attribute "insideHumidity", "number"
        attribute "highTempToday", "number"
        attribute "lowTempToday", "number"
        attribute "highTempYesterday", "number"
        attribute "lowTempYesterday", "number"
        attribute "avgTempYesterday", "number"
        attribute "windSpeed", "number"
        attribute "windGust", "number"
        attribute "windDirection", "number"
        attribute "windDirectionText", "string"
        attribute "pressureTrend", "number"
        attribute "solarRadiation", "number"
        attribute "sunrise", "string"
        attribute "sunset", "string"
        attribute "moonPhase", "string"
        attribute "moonFullness", "number"
        attribute "stationHardware", "string"
        attribute "weewxVersion", "string"
        attribute "observationTime", "string"
        attribute "lastPoll", "string"
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

        section("Extended Data"){
            input "rainData", "bool", title: "Publish extended rain data (rate, today, yesterday, 2-7 day, week)", required: false, defaultValue: true
            input "extendedData", "bool", title: "Publish extended Weewx data (wind, pressure, dewpoint, solar/UV, almanac, daily hi/lo)", required: false, defaultValue: false
            input "solarToLux", "bool", title: "Estimate illuminance (lux) from solar radiation", required: false, defaultValue: false
        }

    }
}

def installed() {
    log.debug "Installed called"
    updated()
}

def initialize(){
	updated()

}
def updated() {
    log.debug "Updated called"

    logCheck()

    unschedule()

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

def refresh(){
    PollStation()
}



def parse(String description) {
}

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
            def var1, var2, rain, wet, varT
            def temp = settings.temp ? parseMeasure(nodeAt(resp1, settings.temp)) : null
            def humid = settings.humid ? parseMeasure(nodeAt(resp1, settings.humid)) : null
            if (settings.var1) {
                varT = varFix(resp1,settings.var1)
                var1 = varT?.isDouble() ? varT.toDouble() : null
            }
            if (settings.var2) {
                varT = varFix(resp1,settings.var2)
                var2 = varT?.isDouble() ? varT.toDouble() : null
            }

           sendEvent(name: "WeewxUptime", value: resp1.data.serverUptime)
           sendEvent(name: "WeewxLocation", value: resp1.data.location)
           sendEvent(name: "Refresh-Weewx", value: pollInterval)

           if (temp?.value != null) {
               sendEvent(name: "temperature", value: temp.value, unit: (temp.unit ?: "\u00B0F"))
           }
           if (humid?.value != null) {
               sendEvent(name: "humidity", value: humid.value, unit: (humid.unit ?: "%"))
           }

            // Published before the custom wet/dry logic below so a bad custom source or
            // custom amount can't stop the rest of the station data from reaching Hubitat.
            def root = resp1.data
            if (settings.rainData == null || settings.rainData) {
                publishRain(root)
            }
            if (settings.extendedData) {
                publishExtended(root)
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
                    sendEvent(name: "water", value: "wet", isStateChange: true)
                    sendEvent(name: "switch", value: "on", isStateChange: true)
            } else {
                    sendEvent(name: "water", value: "dry", isStateChange: true)
                    sendEvent(name: "switch", value: "off", isStateChange: true)
            }
            sendEvent(name: "RainForPeriod", value: rain)

            sendEvent(name: "lastPoll", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone))

        }

    } catch (e) {
        log.error "something went wrong: $e"
    }

}

// Publishes the rain data that daily.json already carries: current rate, today, yesterday,
// the 2-7 day rolling sums from the "rainstats" block and the rolling week total.
private publishRain(root) {
    if (root == null) { return }

    def rate = publishNumber("rainRate", root, "stats.current.rainRate")
    def today = publishNumber("rainToday", root, "stats.sinceMidnight.rainSum")
    def yesterday = publishNumber("rainYesterday", root, "stats.yesterday.rainSum")
    def week = publishNumber("rainWeek", root, "stats.week.rainSum")

    def days = [:]
    (2..7).each { d ->
        days[d] = publishNumber("rain${d}Days", root, "stats.rainstats.rainSum${d}days")
    }

    if (nodeAt(root, "stats.rainstats") == null) {
        LOGINFO("No 'rainstats' block found in daily.json - the 2-7 day totals need the current hubitat-weewx-driver skin installed on your Weewx server.")
    }

    // Unit comes straight from Weewx ("in" or "mm") so the tile matches the station's own formatting.
    def unit = today?.unit ?: yesterday?.unit ?: days[2]?.unit ?: week?.unit
    if (unit) { sendEvent(name: "rainUnit", value: unit) }

    def rateUnit = rate?.unit ?: (unit ? "${unit}/hr" : null)
    def summary = "Rate: ${fmt(rate, rateUnit)} | Total: ${fmt(today, unit)} | Yesterday: ${fmt(yesterday, unit)}" +
                  " | 2 Day: ${fmt(days[2], unit)} | 3 Day: ${fmt(days[3], unit)} | Week: ${fmt(week, unit)}"
    sendEvent(name: "rainSummary", value: summary)

    def tile = "<div style='line-height:1.3em;font-size:0.85em;text-align:left'>" +
               "Rate: <b>${fmt(rate, rateUnit)}</b><br>" +
               "Today: <b>${fmt(today, unit)}</b><br>" +
               "Yesterday: <b>${fmt(yesterday, unit)}</b><br>" +
               "2 Day: <b>${fmt(days[2], unit)}</b> &nbsp; 3 Day: <b>${fmt(days[3], unit)}</b><br>" +
               "Week: <b>${fmt(week, unit)}</b></div>"
    if (tile.length() > 1024) { tile = tile.substring(0, 1024) }
    sendEvent(name: "rainTile", value: tile)
}

// Everything else daily.json exposes. Off by default so existing devices stay tidy.
private publishExtended(root) {
    if (root == null) { return }

    publishNumber("dewpoint", root, "stats.current.dewpoint")
    publishNumber("windChill", root, "stats.current.windchill")
    publishNumber("heatIndex", root, "stats.current.heatIndex")
    publishNumber("insideTemperature", root, "stats.current.insideTemp")
    publishNumber("insideHumidity", root, "stats.current.insideHumidity", "%")

    publishNumber("highTempToday", root, "stats.sinceMidnight.maxtemptoday")
    publishNumber("lowTempToday", root, "stats.sinceMidnight.mintemptoday")
    publishNumber("highTempYesterday", root, "stats.yesterday.maxtemp")
    publishNumber("lowTempYesterday", root, "stats.yesterday.mintemp")
    publishNumber("avgTempYesterday", root, "stats.yesterday.avgtemp")

    publishNumber("windSpeed", root, "stats.current.windSpeed")
    publishNumber("windGust", root, "stats.current.windGust")
    publishNumber("windDirection", root, "stats.current.windDir")
    publishString("windDirectionText", root, "stats.current.windDirText")

    publishNumber("pressure", root, "stats.current.barometer")
    publishNumber("pressureTrend", root, "stats.current.barometerTrendData")

    publishNumber("ultravioletIndex", root, "stats.current.UV")
    def solar = publishNumber("solarRadiation", root, "stats.current.solarRadiation", "W/m2")
    if (settings.solarToLux && solar?.value != null) {
        // Rough daylight conversion - 1 W/m^2 is about 126.7 lux. Good enough for lights-on style rules.
        sendEvent(name: "illuminance", value: Math.round(solar.value.doubleValue() * 126.7), unit: "lux")
    }

    publishString("sunrise", root, "almanac.sun.sunrise")
    publishString("sunset", root, "almanac.sun.sunset")
    publishString("moonPhase", root, "almanac.moon.phase")
    publishNumber("moonFullness", root, "almanac.moon.fullness", "%")

    publishString("stationHardware", root, "hardware")
    publishString("weewxVersion", root, "weewxVersion")
    publishString("observationTime", root, "time")
}

// Walks a dotted path (e.g. "stats.rainstats.rainSum2days") and returns null rather than
// throwing if any segment is missing - older skins won't have every block.
private nodeAt(root, String path) {
    def node = root
    for (String key in path.tokenize('.')) {
        if (node == null) { return null }
        try {
            node = node."${key}"
        } catch (ignored) {
            return null
        }
    }
    return node
}

// Weewx renders values as formatted strings ("1.41 in", "72.3 degF", "0.00 in/hr", "N/A").
// Splits that into a number plus the unit Weewx chose, so we don't have to guess metric vs imperial.
private Map parseMeasure(raw) {
    if (raw == null) { return null }
    String s = raw.toString().trim()
    if (!s || s.equalsIgnoreCase("N/A") || s.equalsIgnoreCase("None") || s.equalsIgnoreCase("null") || s == "-") { return null }
    s = s.replace("&deg;", "\u00B0")
    def m = (s =~ /^(-?(?:[0-9][0-9,]*)?(?:\.[0-9]+)?)\s*(.*)$/)
    if (!m.find() || !m.group(1) || m.group(1) == "-") { return [value: null, unit: null, raw: s] }
    BigDecimal v
    try {
        v = new BigDecimal(m.group(1).replace(",", ""))
    } catch (ignored) {
        return [value: null, unit: null, raw: s]
    }
    String u = m.group(2)?.trim()
    return [value: v, unit: (u ?: null), raw: s]
}

private Map publishNumber(String name, root, String path, String unitOverride = null) {
    def m = parseMeasure(nodeAt(root, path))
    if (m?.value == null) {
        LOGDEBUG("No usable value at ${path} for ${name}")
        return null
    }
    sendEvent(name: name, value: m.value, unit: (unitOverride ?: m.unit))
    return m
}

private publishString(String name, root, String path) {
    def val = nodeAt(root, path)
    if (val == null) {
        LOGDEBUG("No usable value at ${path} for ${name}")
        return null
    }
    String s = val.toString().replace("&deg;", "\u00B0").trim()
    if (!s || s.equalsIgnoreCase("N/A")) { return null }
    sendEvent(name: name, value: s)
    return s
}

private String fmt(Map m, String unit) {
    if (m?.value == null) { return "--" }
    return unit ? "${m.value} ${unit}" : "${m.value}"
}

def varFix (response,var) {
    def node = nodeAt(response, var)
    if (node == null) {
        log.error("It appears ${var} doesn't exist. Please check http://${ipaddress}:${weewxPort}/${weewxPath} to see if that value exists.")
        return null
    }
    def m = parseMeasure(node)
    if (m == null) { return null }
    return (m.value != null) ? m.value.toString() : m.raw
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
