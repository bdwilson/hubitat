/**
* IFTTT Thermostat Driver for Hubitat
*
* v1.0
*/
metadata {
    definition (name: "Honeywell IFTTT Thermostat Driver", namespace: "brianwilson-hubitat", author: "Brian Wilson") {
        capability "Thermostat"
        command    "setFollowSchedule"
        attribute  "followSchedule",     "string"
    }

    main "temperature"
    details(["thermostatFanMode", "heatingSetpoint", "coolingSetpoint","fanOperatingState","followSchedule","status"])

    preferences {
        input("APIKEY", "text", title: "IFTTT Webhook API Key", description: "Your IFTTT Webhook API Key", required: true)
        input("deviceName", "text", title: "Device Name/Location:",required: true, defaultValue: "downstairs")
        input("setHeatPerm", "text", title: "Set Heat Name (perm)", required: true, defaultValue: "set_heat_perm")
        input ("setCoolPerm", "text", title: "Set Cool Name (perm)", required: true, defaultValue: "set_cool_perm")
        input ("resume", "text", title: "Resume Name", required: true, defaultValue: "resume")
        input ("setCool", "text", title: "Set Cool Perm Hold Name (hold hours below)", required: true, defaultValue: "set_cool")
		input ("setHeat", "text", title: "Set Heat Perm Hold Name (hold hours below)", required: true, defaultValue: "set_heat")
		input ("holdTime", "number", title: "Hold # Hours: (0 for perm Hold)", required: true, defaultValue: "0")
		//input ("tempHold", "text", title: "Temp Hold Name", required: false, defaultValue: "temp_hold")
	    input ("fanModeOn", "text", title: "Fan Mode On Name", required: false, defaultValue: "fan_mode_on")
	    input ("fanModeAuto", "text", title: "Fan Mode Auto Name", required: false, defaultValue: "fan_mode_auto")
		//input name: "permHold", type: "bool", title: "Enable debug logging?", defaultValue: true
        input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
    }
}


// parse events into attributes
def parse(String description) {
	logDebug("Here: ${description}")
}

def ifttt_call (Map map = [:], String cmd, String event) {
	def key = settings.deviceName + "-" + cmd
	def ifttt_base = "https://maker.ifttt.com/trigger/" + key + "/with/key/" + settings.APIKEY

	if (map.parm1) {
		ifttt_base = ifttt_base + "?value1=" + map.parm1
	}
	
	if (map.parm2) {
		ifttt_base = ifttt_base + "&value2=" + map.parm2
	}
	
	logDebug("Sending request (${event}): ${ifttt_base} (i.e. IFTTT will send an event, even if it doesn't exist. So check your names if you're having issues)")
	
	try {
		httpGet(uri: "${ifttt_base}"){response ->
        if(response.status != 200) {
            log.error "ERROR: ${response.status}"
		} else {
			logDebug("Response: ${response.data}")
			sendEvent(name: "${event}", value: "${map.parm1}")
        }
   	}   
	}
	catch (groovyx.net.http.HttpResponseException hre) {
                    if (hre.getResponse().getStatus() != 200) {
						log.error "ERROR: ${hre.getResponse().getStatus()} ${hre.getResponse().getData()}"
						log.error "Make sure you have created a webhook called \"${key}\" in IFTTT."
						log.error "Make sure you select the proper options (heat or cool - where applicable)"
						if ((map.parm1) || (map.parm2)) {
							log.error "Also make sure you configure Value1 and Value2 variables for optional input items where applicable."
						}
                    }
    }
}
	
def setHeatingSetpoint(Double temp)
{
	logDebug("Settings: ${settings.setHeat}")
	if ((settings.setHeat) && (settings.holdTime)) {
		ifttt_call(settings.setHeat,"heatingSetpoint",parm1:temp,parm2:settings.holdTime)  	
	} else {
		ifttt_call(settings.setHeatPerm,"heatingSetpoint",parm1:temp)  	
	}
}

def setFollowSchedule() {
	logDebug("Settings: ${settings.resume}")
	ifttt_call(settings.resume,"followSchedule") 
}


def setCoolingSetpoint(double temp) {
	logDebug("Settings: ${settings.setCool}")
	if ((settings.setCool) && (settings.holdTime > 0)) {
		ifttt_call(settings.setCool,"coolingSetpoint",parm1:temp,parm2:settings.holdTime)  	
	} else {
		ifttt_call(settings.setCoolPerm,"coolingSetpoint",parm1:temp)  	
	}
}

def setThermostatFanMode(mode) {
	if (mode == "auto") {
		fanAuto()
	} else {
		fanOn()
	}
}

def fanOn() {	
	logDebug("Settings: ${settings.fanModeOn}")
	ifttt_call(settings.fanModeOn,"fanOperatingState",parm1:'on')  	
}

def fanAuto() {
	logDebug("Settings: ${settings.fanModeAuto}")
	ifttt_call(settings.fanModeAuto,"fanOperatingState",parm1:'auto')  	
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug "$msg"
	}
}
