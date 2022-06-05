/*
 * Virtual Mobile Presence for Geofency - based on original work by Austin Pritchett/ajpri and ogiewon.
 * https://github.com/ogiewon/Hubitat/blob/master/Drivers/virtual-presence-switch.src/virtual-presence-switch.groovy 
 * https://community.hubitat.com/t/virtual-presence-question/15272/21
 */

metadata {
        definition (name: "Virtual Mobile Presence Device", 
					namespace: "brianwilson-hubitat", 
					author: "Brian Wilson",
					importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Drivers/virtual-mobile-presence.groovy"
		) {
        capability "Switch"
        capability "Presence Sensor"
		capability "Sensor"
        command "arrived"
        command "departed"   
    }
}

def arrived() {
	on()
}

def departed () {
	off()
}

def on() {
	sendEvent(name: "switch", value: "on")
    sendEvent(name: "presence", value: "present")

}

def off() {
	sendEvent(name: "switch", value: "off")
    sendEvent(name: "presence", value: "not present")

}
def installed () {
}


