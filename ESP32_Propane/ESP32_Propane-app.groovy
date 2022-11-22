/**

 */
definition(
    name: "ESP32 Propane App",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "ESP32 Propane App",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	//importUrl: "https://raw.githubusercontent.com/bdwilson/xxxx",
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
 	def uri = getFullLocalApiServerUrl() + "/update/?access_token=${state.accessToken}"
    //def extUri = fullApiServerUrl() + "/?access_token=${state.accessToken}"
    //extUri = extUri.replaceAll("null","update")
    return dynamicPage(name: "setupScreen", uninstall: true, install: true){
        section ("<h2>1. Select Virtual ESP32 Propane Device</h2>") {
            paragraph ("This will allow your ESP32 Propane device to update battery and level in Hubitat.")
    		input "voltages", "capability.voltageMeasurement", multiple: false, required: true
    	}
        section("<h2>2. Setup URL in ESP32 Propane Arduino Code</h2>"){
            paragraph("Use the following as the LOCAL URL for <a href='${uri}'>${uri}</a>.")
        }
		section("<h2>4. Enable Debug Mode</h2>") {
            paragraph("Debug is enabled by default and will disable automatically after 1 hour. Having debug mode enabled will enable to see your tests made within Geofency")
       		input "isDebug", "bool", title: "Enable Debug Mode", required: false, multiple: false, defaultValue: true, submitOnChange: true
    	}

    }
}

def installed() {
    log.warn "debug logging for ESP32 Propane: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
    ifDebug("Installed with settings: ${settings}")

}

def updated() {
    log.warn "debug logging for ESP32 Propane: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
	ifDebug("Updated with settings: ${settings}")
}

def update() {
    updateDevice(voltages)
}

def deviceHandler(evt) {}

def updateDevice (devices) {
   	def data = request.JSON
   	//def location = data.name
   	//def event = data.entry
   	double battery = params.battery.toDouble()
    int percentage = params.percentage.toInteger()
    int num = params.num.toInteger()
    int mins = params.mins.toInteger()
    def id = ""
    ifDebug("In updateDevice")
    voltages.each {
      ifDebug("RECEIVED: ${it.displayName}, attribute ${it.name}, ID: ${it.id}")
      //resp << [Name: it.displayName, ID: it.id]
      id = it.id
    }
    devices.each {
        ifDebug("Devices: ${it.id}  ${it.name}")
                }

      def device = devices.find { it.id == id }
      //def device = devices.find { it.currentValue("region") + "-" + it.currentValue("user") == deviceName }

   	  ifDebug("battery: ${battery} device: ${device} percentage: ${percentage} deviceName: ${deviceName}")

        if (!device) {
            def msg = ["Error: device not found. Make sure a device a with type: ."]
			ifDebug("${msg}")
            // render's aren't working. maybe someone can fix this.
            // render contentType: "text/html", msg: out, status: 404
        } else {
            if (battery && percentage) {
                def date = new Date()
                date = date.format("MM/dd/yyyy HH:mm:ss")
                device.sendEvent(name: "LastUpdated", value: date)
                device.sendEvent(name: "voltage", value: battery)
                device.sendEvent(name: "propaneLevel", value: percentage)
                device.sendEvent(name: "NumberOfRestarts", value: num)
                device.sendEvent(name: "MinutesActive", value: mins)
                if (num == 0) {
                    device.sendEvent(name: "LastRestart", value: date)
                }
                //def status = ""
                // https://www.vboxmotorsport.co.uk/index.php/us/calculators
                // min = 2.596  max = 4.22
                float status1 = battery*61.57-159.85-1.5
                status = status1.round(2)
                log.debug("Status: ${status}:${date}:${battery}:${percentage}")
                //if (battery > 4.1) {
                //    status=100
                //} else if ((battery > 4.0) && (battery < 4.1)) {
                //    status= 75
                //} else if (battery > 3.75) {
                //    status=50
               /// } else if ((battery < 3.75) && (battery > 3.4)) {
                //    status=25
               // } else if ((battery < 3.4) && (battery > 3.2)) {
               //     status=10
               // } else if ((battery < 3.2) && (battery > 3.1)) {
               //     status=5
               // } else {
               //    status=0
                //}
                device.sendEvent(name: "battery", value: status)
                ifDebug("Voltage: ${battery} propaneLevel: ${percentage} battery: ${status} mins: ${mins} restarts: ${num}")


                //render contentType: "text/html", data: msg, status: 200
            }
        }


}

mappings {
    path("/update/:battery/:percentage/:num/:mins") {
    	action: [
            POST: "update",
            GET: "update"
        ]
    }
}

def logsOff() {
    log.warn "debug logging for Geofency now disabled..."
    app.updateSetting("isDebug", [value: "false", type: "bool"])
    state.isDebug = false
}

private ifDebug(msg) {
    if (msg && state.isDebug)  log.debug 'Geofency-Presence: ' + msg
}
