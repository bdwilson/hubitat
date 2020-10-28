/*
 * Virtual Mobile Presence for Geofency - based on original work by Austin Pritchett/ajpri
 * 
 */

metadata {
        definition (name: "Geofency Virtual Mobile Presence Device", 
					namespace: "brianwilson-hubitat", 
					author: "Brian Wilson",
					importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/virtual-mobile-presence.groovy"
		) {
        capability "Switch"
        capability "Refresh"
        capability "Presence Sensor"
		capability "Sensor"
	    attribute "region", "text"
		attribute "user", "text"
    }
	preferences { 
		input name: "region", type: "text", title: "Location to Track", required: true
		input name: "user", type: "text", title: "User to Track", required: true
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

def updated() {
	state.clear()
	sendEvent(name: "user", value: "${user}") 
	sendEvent(name: "region", value: "${region}")
}
