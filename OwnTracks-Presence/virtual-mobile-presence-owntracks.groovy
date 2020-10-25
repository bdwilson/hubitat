/*
 * Virtual Mobile Presence for Owntracks - based on original work by Austin Pritchett/ajpri
 *
 * Version 1.1.2: bdwilson - initial version tracking.
 */
metadata {
        definition (
            name: "Virtual Mobile Presence for Owntracks", 
			namespace: "brianwilson-hubitat", 
            author: "Brian Wilson", 
            importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy"
        ) {
        capability "Switch"
        capability "Refresh"
        capability "Presence Sensor"
	    capability "Sensor"
        capability "Battery"
        attribute "ssid", "string"
        attribute "bssid", "string"    
		attribute "batteryStatus", "string"
    }
}

def parse(String description) {
}

def on() {
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "presence", value: "present")

}

def refresh() { }

def off() {
	sendEvent(name: "switch", value: "off")
    sendEvent(name: "presence", value: "not present")

}

def arrived() {
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "presence", value: "present")
}

def departed () {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "presence", value: "not present")
}

def installed () {
}

