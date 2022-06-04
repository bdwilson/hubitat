/*
 * Virtual Mobile Presence for Geofency - based on original work by Austin Pritchett/ajpri
 * 
 */

metadata {
        definition (name: "Virtual Mobile Presence Device", 
					namespace: "brianwilson-hubitat", 
					author: "Brian Wilson",
					importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Drivers/virtual-mobile-presence.groovy"
		) {
        capability "Switch"
        capability "Refresh"
        capability "Presence Sensor"
		capability "Sensor"

    }
}

def refresh() { }

def arrived() {
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "presence", value: "present")
}

def departed () {
	sendEvent(name: "switch", value: "off")
	sendEvent(name: "presence", value: "not present")
}

def parse(String description) {
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


