/**
 For use with this: https://github.com/bdwilson/otodata-genmon 
 */
definition(
    name: "Otodata Propane Receiver",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Otodata Propane Receiver",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Otodata-Propane/Otodata-app.groovy", 
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
 	def uri = getFullLocalApiServerUrl() + "/update/"
    //def extUri = fullApiServerUrl() + "/?access_token=${state.accessToken}"
    //extUri = extUri.replaceAll("null","update")
    return dynamicPage(name: "setupScreen", uninstall: true, install: true){
        section ("<h2>1. Select Virtual Otodata Propane Device</h2>") {
            paragraph ("This will allow your Otodata Propane device to update propane levels in Hubitat.")
    		input "voltages", "capability.sensor", multiple: false, required: true
    	}
        section("<h2>2. Setup URL in Otadata Receiver</h2>"){ 
            paragraph("You will need to install the receiver from here: <a href='https://github.com/bdwilson/otodata-genmon'>https://github.com/bdwilson/otodata-genmon</a> and update the following variables")
            paragraph("Use the following as the hubitat_url: <a href='${uri}'>${uri}</a>")
            paragraph("Use the following as the hubitat_key: ${state.accessToken}")
        }
		section("<h2>4. Enable Debug Mode</h2>") {
            paragraph("Debug is enabled by default and will disable automatically after 1 hour. Having debug mode enabled will enable to see your tests made within Geofency")
       		input "isDebug", "bool", title: "Enable Debug Mode", required: false, multiple: false, defaultValue: true, submitOnChange: true
    	}

    }
}

def installed() {
    log.warn "debug logging for Otodata Propane Receiver: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
    ifDebug("Installed with settings: ${settings}")

}

def updated() {
    log.warn "debug logging for Otodata Propane Receiver: ${state.isDebug == true}"
    if (state.isDebug) runIn(3600, logsOff)
	ifDebug("Updated with settings: ${settings}")
}

def update() {
    updateDevice(voltages)
}

def deviceHandler(evt) {}

def updateDevice (devices) {
   	def data = request.JSON
    float percentage = params.percentage.toFloat()
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

   	  ifDebug("device: ${device} percentage: ${percentage} deviceName: ${deviceName}")
      
        if (!device) {
            def msg = ["Error: device not found. Make sure a device a with type: ."]
			ifDebug("${msg}")
            // render's aren't working. maybe someone can fix this. 
            // render contentType: "text/html", msg: out, status: 404
        } else {
            if (percentage) {
                def date = new Date()
                date = date.format("MM/dd/yyyy HH:mm:ss")
                device.sendEvent(name: "LastUpdated", value: date)
                device.sendEvent(name: "propaneLevel", value: percentage)          

            }
        } 
     
 
}

mappings {
    path("/update/:percentage") {
    	action: [
            POST: "update",
            GET: "update"
        ]
    }
}

def logsOff() {
    log.warn "debug logging for Otodata Propane Receiver now disabled..."
    app.updateSetting("isDebug", [value: "false", type: "bool"])
    state.isDebug = false 
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'Otodata Propane Receiver: ' + msg  
}

