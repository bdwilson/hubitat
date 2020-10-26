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
        
		attribute "ssid", "text"
		attribute "bssid", "text"
		attribute "batteryStatus", "text"
		attribute "region", "text"
		attribute "user", "text"
	}
	preferences { 
		input name: "region", type: "text", title: "Location/Region to Track", required: true
		input name: "user", type: "text", title: "User to Track", required: true
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

def updated() {
	state.clear()
	sendEvent(name: "user", value: "${user}")
	sendEvent(name: "region", value: "${region}")
}
